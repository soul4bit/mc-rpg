package ru.mcrpg.gameauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherSessionFilesTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writeAndReadRoundTripUsesIsoDates() throws Exception {
        LauncherSessionFiles files = new LauncherSessionFiles();
        LauncherSession session = new LauncherSession(
            "ticket-1",
            "Knight",
            "uuid-1",
            "obsidiangate-main",
            Instant.parse("2026-05-07T14:00:00Z")
        );

        Path sessionFile = tempDirectory.resolve("session.json");
        files.write(sessionFile, session);
        String json = new String(Files.readAllBytes(sessionFile), StandardCharsets.UTF_8);

        assertFalse(json.contains("177"));
        assertEquals(session.getExpiresAt(), files.read(sessionFile).getExpiresAt());
    }

    @Test
    void readUnderstandsLegacyNumericInstant() throws Exception {
        Path sessionFile = tempDirectory.resolve("legacy-session.json");
        Files.write(
            sessionFile,
            ("{\n"
                + "  \"ticket\": \"ticket-2\",\n"
                + "  \"username\": \"Mage\",\n"
                + "  \"uuid\": \"uuid-2\",\n"
                + "  \"serverId\": \"obsidiangate-main\",\n"
                + "  \"expiresAt\": 1778161561.205935253\n"
                + "}\n").getBytes(StandardCharsets.UTF_8)
        );

        LauncherSession session = new LauncherSessionFiles().read(sessionFile);

        assertEquals("ticket-2", session.getTicket());
        assertEquals("Mage", session.getUsername());
        assertEquals("obsidiangate-main", session.getServerId());
        assertEquals(Instant.parse("2026-05-07T13:46:01.205935253Z"), session.getExpiresAt());
    }
}
