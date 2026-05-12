package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void refreshIfNeededClearsStoredSessionWhenRefreshTokenIsInvalid() throws Exception {
        HttpServer server = startRefreshServer(401, "{\"error\":\"invalid_refresh_token\",\"message\":\"Refresh token is invalid.\"}");
        try {
            AuthSessionStore store = new AuthSessionStore(tempDirectory.resolve("session.json"));
            AuthService service = new AuthService(new AuthApiClient(HttpClient.newHttpClient()), store);
            LauncherConfig config = LauncherConfig.defaults();
            config.setAuthBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

            AuthSession session = expiringSession(true);
            store.save(session);

            IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> service.refreshIfNeeded(config, session)
            );

            assertInstanceOf(AuthSessionExpiredException.class, exception);
            assertEquals("Сессия истекла. Войдите в аккаунт снова.", exception.getMessage());
            assertFalse(Files.exists(store.getSessionFile()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refreshIfNeededKeepsStoredSessionForNonAuthFailures() throws Exception {
        HttpServer server = startRefreshServer(500, "{\"error\":\"server_error\",\"message\":\"Backend is down.\"}");
        try {
            AuthSessionStore store = new AuthSessionStore(tempDirectory.resolve("session.json"));
            AuthService service = new AuthService(new AuthApiClient(HttpClient.newHttpClient()), store);
            LauncherConfig config = LauncherConfig.defaults();
            config.setAuthBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

            AuthSession session = expiringSession(true);
            store.save(session);

            IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> service.refreshIfNeeded(config, session)
            );

            AuthClientException authException = assertInstanceOf(AuthClientException.class, exception);
            assertEquals(500, authException.getStatusCode());
            assertTrue(Files.exists(store.getSessionFile()));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startRefreshServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/refresh", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static AuthSession expiringSession(boolean persisted) {
        AuthAccount account = new AuthAccount("acc-1", "Knight", "knight@example.com", "player", "active");
        return new AuthSession(account, "access-1", "refresh-1", Instant.now().plusSeconds(5), persisted);
    }
}
