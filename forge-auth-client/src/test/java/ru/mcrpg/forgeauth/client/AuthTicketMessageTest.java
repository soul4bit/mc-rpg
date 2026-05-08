package ru.mcrpg.forgeauth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import ru.mcrpg.gameauth.GameTicketProof;

class AuthTicketMessageTest {

    @Test
    void roundTripKeepsTicketAndServerId() {
        AuthTicketMessage original = new AuthTicketMessage(new GameTicketProof("ticket-1", "obsidiangate-main"));
        ByteBuf buffer = Unpooled.buffer();

        original.toBytes(buffer);

        AuthTicketMessage restored = new AuthTicketMessage();
        restored.fromBytes(buffer);

        assertEquals("ticket-1", restored.getTicket());
        assertEquals("obsidiangate-main", restored.getServerId());
    }
}
