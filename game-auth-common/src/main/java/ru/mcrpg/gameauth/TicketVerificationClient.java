package ru.mcrpg.gameauth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TicketVerificationClient {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public TicketVerificationClient() {
        this(5000, 5000);
    }

    public TicketVerificationClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public TicketVerificationResult verify(String baseUrl, String ticket, String serverId) throws IOException {
        String resolvedBaseUrl = requireText(baseUrl, "Base URL is required.");
        String resolvedTicket = requireText(ticket, "Ticket is required.");
        String resolvedServerId = requireText(serverId, "Server id is required.");

        URL endpoint = buildVerifyUrl(resolvedBaseUrl);
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        byte[] requestBody = (
            "{\"ticket\":\"" + escapeJson(resolvedTicket) + "\",\"serverId\":\"" + escapeJson(resolvedServerId) + "\"}"
        ).getBytes(StandardCharsets.UTF_8);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody);
        }

        int responseCode = connection.getResponseCode();
        InputStream responseStream = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (responseStream == null) {
            throw new IOException("Verification endpoint returned no body. HTTP " + responseCode);
        }

        try (InputStream inputStream = responseStream) {
            SimpleJsonObject root = SimpleJsonObject.parse(readUtf8(inputStream));
            if (responseCode >= 400) {
                throw new IOException("Verification request failed. HTTP " + responseCode + ": " + compactMessage(root));
            }

            return new TicketVerificationResult(
                root.optionalBoolean("valid", false),
                root.optionalString("accountId"),
                root.optionalString("username"),
                root.optionalString("uuid"),
                root.optionalString("role"),
                root.optionalString("reason")
            );
        }
    }

    private static URL buildVerifyUrl(String baseUrl) throws IOException {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new URL(normalized + "/game/tickets/verify");
    }

    private static String compactMessage(SimpleJsonObject root) {
        if (root == null) {
            return "";
        }
        String error = root.optionalString("error");
        String message = root.optionalString("message");
        if (!error.isEmpty() && !message.isEmpty()) {
            return error + ": " + message;
        }
        if (!message.isEmpty()) {
            return message;
        }
        return error;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[8192];
        StringBuilder builder = new StringBuilder();
        int read;
        while ((read = inputStream.read(bytes)) != -1) {
            builder.append(new String(bytes, 0, read, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
