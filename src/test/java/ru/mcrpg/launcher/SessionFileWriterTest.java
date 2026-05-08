package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionFileWriterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writeUsesIsoDateFormatForExpiry() throws Exception {
        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory(tempDirectory.toString());

        GameTicket ticket = new GameTicket(
            "ticket-1",
            "Knight",
            "uuid-1",
            "obsidiangate-main",
            Instant.parse("2026-05-07T13:06:01.205935253Z")
        );

        Path sessionFile = new SessionFileWriter().write(config, ticket);
        String json = new String(Files.readAllBytes(sessionFile), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"expiresAt\" : \"2026-05-07T13:06:01.205935253Z\""));
    }
}
