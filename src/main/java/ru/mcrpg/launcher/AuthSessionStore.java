package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

public final class AuthSessionStore {

    private final Path sessionFile;
    private final ObjectMapper objectMapper;

    public AuthSessionStore(Path sessionFile) {
        this.sessionFile = sessionFile;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static AuthSessionStore defaultStore() {
        Path sessionFile = Paths.get(
            System.getProperty("user.home"),
            ".obsidian-gate-launcher",
            "session.json"
        );
        return new AuthSessionStore(sessionFile);
    }

    public Optional<AuthSession> load() throws IOException {
        if (!Files.exists(sessionFile)) {
            return Optional.empty();
        }

        try (InputStream inputStream = Files.newInputStream(sessionFile)) {
            StoredSession stored = objectMapper.readValue(inputStream, StoredSession.class);
            AuthSession session = new AuthSession(
                new AuthAccount(
                    stored.accountId,
                    stored.username,
                    stored.email,
                    stored.role,
                    stored.status,
                    stored.avatar,
                    stored.avatarUrl
                ),
                stored.accessToken,
                stored.refreshToken,
                stored.expiresAt,
                stored.persisted
            );
            return Optional.of(session);
        }
    }

    public void save(AuthSession session) throws IOException {
        if (session == null || session.getAccount() == null) {
            clear();
            return;
        }

        Path parent = sessionFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StoredSession stored = new StoredSession();
        stored.accountId = session.getAccount().getId();
        stored.username = session.getAccount().getUsername();
        stored.email = session.getAccount().getEmail();
        stored.role = session.getAccount().getRole();
        stored.status = session.getAccount().getStatus();
        stored.avatar = session.getAccount().getAvatar();
        stored.avatarUrl = session.getAccount().getAvatarUrl();
        stored.accessToken = session.getAccessToken();
        stored.refreshToken = session.getRefreshToken();
        stored.expiresAt = session.getExpiresAt();
        stored.persisted = session.isPersisted();

        try (OutputStream outputStream = Files.newOutputStream(sessionFile)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, stored);
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(sessionFile);
    }

    public Path getSessionFile() {
        return sessionFile;
    }

    private static final class StoredSession {
        public String accountId;
        public String username;
        public String email;
        public String role;
        public String status;
        public String avatar;
        public String avatarUrl;
        public String accessToken;
        public String refreshToken;
        public Instant expiresAt;
        public boolean persisted;
    }
}
