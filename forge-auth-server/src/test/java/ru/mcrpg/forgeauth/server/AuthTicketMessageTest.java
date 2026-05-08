package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class AuthTicketMessageTest {

    @Test
    void roundTripKeepsTicketAndServerId() {
        AuthTicketMessage original = new AuthTicketMessage();
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt("ticket-1".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        buffer.writeBytes("ticket-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        buffer.writeInt("obsidiangate-main".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        buffer.writeBytes("obsidiangate-main".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        AuthTicketMessage restored = new AuthTicketMessage();
        restored.fromBytes(buffer);

        ByteBuf encoded = Unpooled.buffer();
        restored.toBytes(encoded);

        AuthTicketMessage roundTripped = new AuthTicketMessage();
        roundTripped.fromBytes(encoded);

        assertEquals("ticket-1", roundTripped.getTicket());
        assertEquals("obsidiangate-main", roundTripped.getServerId());
        assertTrue(roundTripped.isComplete());
    }
}
