package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public final class SessionFileWriter {

    private final ObjectMapper objectMapper;

    public SessionFileWriter() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    public Path write(LauncherConfig config, GameTicket ticket) throws IOException {
        Path gameDirectory = Paths.get(requireText(config.getGameDirectory(), "Game directory is not configured."));
        Path sessionFile = gameDirectory.resolve(".obsidiangate").resolve("session.json").toAbsolutePath().normalize();
        Files.createDirectories(sessionFile.getParent());

        SessionPayload payload = new SessionPayload();
        payload.ticket = ticket.getTicket();
        payload.username = ticket.getUsername();
        payload.uuid = ticket.getUuid();
        payload.serverId = ticket.getServerId();
        payload.expiresAt = ticket.getExpiresAt();

        try (OutputStream outputStream = Files.newOutputStream(sessionFile)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, payload);
        }
        return sessionFile;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static final class SessionPayload {
        public String ticket;
        public String username;
        public String uuid;
        public String serverId;
        public Instant expiresAt;
    }
}
