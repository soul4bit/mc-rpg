package ru.mcrpg.gameauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TicketVerificationClientTest {

    @Test
    void verifyReadsValidResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/game/tickets/verify", exchange -> respond(
                exchange,
                200,
                "{"
                    + "\"valid\":true,"
                    + "\"accountId\":\"acc-1\","
                    + "\"username\":\"Knight\","
                    + "\"uuid\":\"uuid-1\","
                    + "\"role\":\"player\""
                    + "}"
            ));
            server.start();

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            TicketVerificationResult result = new TicketVerificationClient().verify(baseUrl, "ticket-1", "obsidiangate-main");

            assertTrue(result.isValid());
            assertEquals("acc-1", result.getAccountId());
            assertEquals("Knight", result.getUsername());
            assertEquals("uuid-1", result.getPlayerUuid());
            assertEquals("player", result.getRole());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void verifyReadsInvalidResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/game/tickets/verify", exchange -> respond(
                exchange,
                200,
                "{"
                    + "\"valid\":false,"
                    + "\"reason\":\"expired\""
                    + "}"
            ));
            server.start();

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            TicketVerificationResult result = new TicketVerificationClient().verify(baseUrl, "ticket-1", "obsidiangate-main");

            assertFalse(result.isValid());
            assertEquals("expired", result.getReason());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
