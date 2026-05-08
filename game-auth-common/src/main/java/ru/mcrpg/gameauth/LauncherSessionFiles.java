package ru.mcrpg.gameauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public final class LauncherSessionFiles {

    public LauncherSession read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Session file path is required.");
        }

        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (json.trim().isEmpty()) {
            throw new IOException("Session file is empty or invalid: " + path);
        }

        SimpleJsonObject root = SimpleJsonObject.parse(json);
        return new LauncherSession(
            root.requiredString("ticket"),
            root.requiredString("username"),
            root.requiredString("uuid"),
            root.requiredString("serverId"),
            requiredInstant(root.get("expiresAt"), "expiresAt")
        );
    }

    public void write(Path path, LauncherSession session) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Session file path is required.");
        }
        if (session == null) {
            throw new IllegalArgumentException("Session payload is required.");
        }

        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.write(path, toJson(session).getBytes(StandardCharsets.UTF_8));
    }

    public LauncherSession readFromSystemProperty() throws IOException {
        String rawPath = System.getProperty(GameAuthConstants.SESSION_FILE_PROPERTY, "").trim();
        if (rawPath.isEmpty()) {
            throw new IOException("Missing system property: " + GameAuthConstants.SESSION_FILE_PROPERTY);
        }
        return read(Paths.get(rawPath));
    }

    private static Instant requiredInstant(SimpleJsonObject.JsonValue value, String field) throws IOException {
        if (value == null || value.getType() == SimpleJsonObject.JsonType.NULL) {
            throw new IOException("Missing required session field: " + field);
        }
        if (value.getType() == SimpleJsonObject.JsonType.STRING) {
            return Instant.parse(value.getText().trim());
        }
        if (value.getType() == SimpleJsonObject.JsonType.NUMBER) {
            BigDecimal numericValue = new BigDecimal(value.getText().trim());
            long seconds = numericValue.longValue();
            BigDecimal fractional = numericValue.subtract(BigDecimal.valueOf(seconds)).abs();
            int nanos = fractional.movePointRight(9).intValue();
            return Instant.ofEpochSecond(seconds, nanos);
        }
        throw new IOException("Unsupported instant value for field: " + field);
    }

    private static String toJson(LauncherSession session) {
        return "{\n"
            + "  \"ticket\": \"" + escapeJson(session.getTicket()) + "\",\n"
            + "  \"username\": \"" + escapeJson(session.getUsername()) + "\",\n"
            + "  \"uuid\": \"" + escapeJson(session.getUuid()) + "\",\n"
            + "  \"serverId\": \"" + escapeJson(session.getServerId()) + "\",\n"
            + "  \"expiresAt\": \"" + session.getExpiresAt().toString() + "\"\n"
            + "}\n";
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
