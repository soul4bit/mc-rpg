package ru.mcrpg.gameauth;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class LauncherSessionFiles {

    private static final String PRIMARY_TICKET_FIELD = "ticket";

    public LauncherSession read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Session file path is required.");
        }

        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (json.trim().isEmpty()) {
            throw new IOException("Session file is empty or invalid: " + path);
        }

        SimpleJsonObject root = SimpleJsonObject.parse(json);
        List<String> tickets = readTickets(root);
        if (tickets.isEmpty()) {
            throw new IOException("Missing required session ticket data.");
        }
        return new LauncherSession(
            tickets,
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
        return read(resolveFromSystemProperty());
    }

    public Path resolveFromSystemProperty() throws IOException {
        String rawPath = System.getProperty(GameAuthConstants.SESSION_FILE_PROPERTY, "").trim();
        if (rawPath.isEmpty()) {
            throw new IOException("Missing system property: " + GameAuthConstants.SESSION_FILE_PROPERTY);
        }
        return Paths.get(rawPath);
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
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        List<String> tickets = session.getTickets();
        appendStringField(json, PRIMARY_TICKET_FIELD, tickets.isEmpty() ? "" : tickets.get(0), true);
        for (int index = 1; index < tickets.size(); index++) {
            appendStringField(json, PRIMARY_TICKET_FIELD + (index + 1), tickets.get(index), true);
        }

        appendStringField(json, "username", session.getUsername(), true);
        appendStringField(json, "uuid", session.getUuid(), true);
        appendStringField(json, "serverId", session.getServerId(), true);
        appendStringField(json, "expiresAt", session.getExpiresAt().toString(), false);
        json.append("}\n");
        return json.toString();
    }

    private static List<String> readTickets(SimpleJsonObject root) {
        List<String> tickets = new ArrayList<String>();
        for (int index = 1; index < 65; index++) {
            String field = index == 1 ? PRIMARY_TICKET_FIELD : PRIMARY_TICKET_FIELD + index;
            String value = root.optionalString(field);
            if (value.isEmpty()) {
                if (index == 1) {
                    return tickets;
                }
                break;
            }
            tickets.add(value);
        }
        return tickets;
    }

    private static void appendStringField(StringBuilder json, String name, String value, boolean withComma) {
        json.append("  \"")
            .append(name)
            .append("\": \"")
            .append(escapeJson(value))
            .append('"');
        if (withComma) {
            json.append(',');
        }
        json.append('\n');
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
