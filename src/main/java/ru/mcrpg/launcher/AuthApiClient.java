package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public final class AuthApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AuthApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    AuthApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AuthSession login(String baseUrl, String login, String password, String deviceName) throws IOException {
        AuthSessionResponse response = sendJson(
            "POST",
            baseUrl,
            "/auth/login",
            new LoginRequest(login, password, deviceName),
            null,
            AuthSessionResponse.class
        );
        return response.toSession(true);
    }

    public AuthSession register(String baseUrl, String username, String email, String password, String deviceName) throws IOException {
        AuthSessionResponse response = sendJson(
            "POST",
            baseUrl,
            "/auth/register",
            new RegisterRequest(username, email, password, deviceName),
            null,
            AuthSessionResponse.class
        );
        return response.toSession(true);
    }

    public AuthSession refresh(String baseUrl, AuthSession session) throws IOException {
        RefreshResponse response = sendJson(
            "POST",
            baseUrl,
            "/auth/refresh",
            new RefreshRequest(session.getRefreshToken()),
            null,
            RefreshResponse.class
        );
        return session.withTokens(response.accessToken, response.refreshToken, response.expiresAt);
    }

    public void logout(String baseUrl, AuthSession session) throws IOException {
        sendJson(
            "POST",
            baseUrl,
            "/auth/logout",
            new RefreshRequest(session.getRefreshToken()),
            null,
            Void.class
        );
    }

    public AuthAccount me(String baseUrl, String accessToken) throws IOException {
        AccountResponse response = sendJson("GET", baseUrl, "/me", null, accessToken, AccountResponse.class);
        return response.toAccount();
    }

    public GameTicket createGameTicket(String baseUrl, String accessToken, String serverId) throws IOException {
        GameTicketResponse response = sendJson(
            "POST",
            baseUrl,
            "/game/tickets",
            new GameTicketRequest(serverId),
            accessToken,
            GameTicketResponse.class
        );
        return response.toGameTicket();
    }

    private <T> T sendJson(
        String method,
        String baseUrl,
        String path,
        Object requestBody,
        String accessToken,
        Class<T> responseType
    ) throws IOException {
        URI uri = resolveUri(baseUrl, path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json");

        if (accessToken != null && !accessToken.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + accessToken.trim());
        }

        if (requestBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = send(builder.build());
        String body = response.body() == null ? "" : response.body().trim();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw decodeError(response.statusCode(), body);
        }
        if (Void.class.equals(responseType)) {
            return null;
        }
        if (body.isEmpty()) {
            throw new IOException("Auth API returned an empty response for " + uri + ".");
        }
        return objectMapper.readValue(body, responseType);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Auth request was interrupted.", exception);
        }
    }

    private AuthClientException decodeError(int statusCode, String body) {
        String errorCode = "request_failed";
        String message = "Authentication request failed.";

        if (!body.isEmpty()) {
            try {
                JsonNode node = objectMapper.readTree(body);
                if (node.hasNonNull("error")) {
                    errorCode = node.get("error").asText(errorCode);
                }
                if (node.hasNonNull("message")) {
                    message = node.get("message").asText(message);
                }
            } catch (Exception ignored) {
                message = body;
            }
        }

        return new AuthClientException(statusCode, errorCode, message);
    }

    private static URI resolveUri(String baseUrl, String path) throws IOException {
        String normalizedBase = requireText(baseUrl, "Auth API base URL is not configured.");
        String normalizedPath = path == null ? "" : path.trim();
        String separator = normalizedBase.endsWith("/") || normalizedPath.startsWith("/") ? "" : "/";
        String resolved = normalizedBase.endsWith("/") && normalizedPath.startsWith("/")
            ? normalizedBase.substring(0, normalizedBase.length() - 1) + normalizedPath
            : normalizedBase + separator + normalizedPath;
        try {
            return new URI(resolved);
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid auth API URL: " + resolved, exception);
        }
    }

    private static String requireText(String value, String message) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException(message);
        }
        return value.trim();
    }

    private static final class RegisterRequest {
        public final String username;
        public final String email;
        public final String password;
        public final String deviceName;

        private RegisterRequest(String username, String email, String password, String deviceName) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.deviceName = deviceName;
        }
    }

    private static final class LoginRequest {
        public final String login;
        public final String password;
        public final String deviceName;

        private LoginRequest(String login, String password, String deviceName) {
            this.login = login;
            this.password = password;
            this.deviceName = deviceName;
        }
    }

    private static final class RefreshRequest {
        public final String refreshToken;

        private RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    private static final class GameTicketRequest {
        public final String serverId;

        private GameTicketRequest(String serverId) {
            this.serverId = serverId;
        }
    }

    private static final class AccountResponse {
        public String id;
        public String username;
        public String email;
        public String role;
        public String status;

        private AuthAccount toAccount() {
            return new AuthAccount(id, username, email, role, status);
        }
    }

    private static final class AuthSessionResponse {
        public AccountResponse account;
        public String accessToken;
        public String refreshToken;
        public long expiresIn;
        public Instant expiresAt;

        private AuthSession toSession(boolean persisted) {
            return new AuthSession(account == null ? null : account.toAccount(), accessToken, refreshToken, expiresAt, persisted);
        }
    }

    private static final class RefreshResponse {
        public String accessToken;
        public String refreshToken;
        public long expiresIn;
        public Instant expiresAt;
    }

    private static final class GameTicketResponse {
        public String ticket;
        public String username;
        public String uuid;
        public String serverId;
        public Instant expiresAt;

        private GameTicket toGameTicket() {
            return new GameTicket(ticket, username, uuid, serverId, expiresAt);
        }
    }
}
