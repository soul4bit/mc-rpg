package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SessionFileWriter {

    private final ObjectMapper objectMapper;

    public SessionFileWriter() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path write(LauncherConfig config, GameTicket ticket) throws IOException {
        return write(config, Collections.singletonList(ticket));
    }

    public Path write(LauncherConfig config, List<GameTicket> tickets) throws IOException {
        Path gameDirectory = Paths.get(requireText(config.getGameDirectory(), "Game directory is not configured."));
        Path sessionFile = gameDirectory.resolve(".obsidiangate").resolve("session.json").toAbsolutePath().normalize();
        Files.createDirectories(sessionFile.getParent());

        List<GameTicket> resolvedTickets = requireTickets(tickets);
        GameTicket firstTicket = resolvedTickets.get(0);
        Instant earliestExpiry = firstTicket.getExpiresAt();

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        for (int index = 0; index < resolvedTickets.size(); index++) {
            GameTicket currentTicket = resolvedTickets.get(index);
            requireCompatibleTicket(firstTicket, currentTicket);
            if (currentTicket.getExpiresAt().isBefore(earliestExpiry)) {
                earliestExpiry = currentTicket.getExpiresAt();
            }
            payload.put(index == 0 ? "ticket" : "ticket" + (index + 1), currentTicket.getTicket());
        }
        payload.put("username", firstTicket.getUsername());
        payload.put("uuid", firstTicket.getUuid());
        payload.put("serverId", firstTicket.getServerId());
        payload.put("expiresAt", earliestExpiry);

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

    private static List<GameTicket> requireTickets(List<GameTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            throw new IllegalArgumentException("At least one game ticket is required.");
        }
        for (GameTicket ticket : tickets) {
            if (ticket == null) {
                throw new IllegalArgumentException("Game ticket list contains a null entry.");
            }
        }
        return tickets;
    }

    private static void requireCompatibleTicket(GameTicket firstTicket, GameTicket currentTicket) {
        if (!firstTicket.getUsername().equals(currentTicket.getUsername())
            || !firstTicket.getUuid().equals(currentTicket.getUuid())
            || !firstTicket.getServerId().equals(currentTicket.getServerId())) {
            throw new IllegalArgumentException("Reconnect tickets must belong to the same player and server.");
        }
    }
}
