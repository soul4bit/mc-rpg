package ru.mcrpg.forgeauth.client;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import ru.mcrpg.gameauth.GameTicketProof;

public final class AuthTicketMessage implements IMessage {

    private String ticket;
    private String serverId;

    public AuthTicketMessage() {
    }

    public AuthTicketMessage(GameTicketProof proof) {
        this.ticket = proof == null ? "" : proof.getTicket();
        this.serverId = proof == null ? "" : proof.getServerId();
    }

    public String getTicket() {
        return ticket == null ? "" : ticket;
    }

    public String getServerId() {
        return serverId == null ? "" : serverId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ticket = readUtf8(buf);
        serverId = readUtf8(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, getTicket());
        writeUtf8(buf, getServerId());
    }

    private static void writeUtf8(ByteBuf buf, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf8(ByteBuf buf) {
        int length = Math.max(0, buf.readInt());
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
