package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;

public final class AuthService {

    private static final Duration ACCESS_TOKEN_REFRESH_WINDOW = Duration.ofSeconds(45);

    private final AuthApiClient authApiClient;
    private final AuthSessionStore authSessionStore;

    public AuthService(AuthApiClient authApiClient, AuthSessionStore authSessionStore) {
        this.authApiClient = authApiClient;
        this.authSessionStore = authSessionStore;
    }

    public AuthSession login(LauncherConfig config, String login, String password, boolean rememberSession) throws IOException {
        AuthSession session = authApiClient.login(config.getAuthBaseUrl(), login, password, deviceName());
        return remember(session, rememberSession);
    }

    public AuthSession register(LauncherConfig config, String username, String email, String password, boolean rememberSession) throws IOException {
        AuthSession session = authApiClient.register(config.getAuthBaseUrl(), username, email, password, deviceName());
        return remember(session, rememberSession);
    }

    public AuthSession refreshIfNeeded(LauncherConfig config, AuthSession session) throws IOException {
        if (session == null) {
            return null;
        }
        if (!session.isAccessTokenExpiringWithin(ACCESS_TOKEN_REFRESH_WINDOW)) {
            return session;
        }

        AuthSession refreshed = authApiClient.refresh(config.getAuthBaseUrl(), session);
        AuthSession enriched = refreshed.withAccount(session.getAccount()).withPersistence(session.isPersisted());
        persist(enriched);
        return enriched;
    }

    public AuthAccount fetchProfile(LauncherConfig config, AuthSession session) throws IOException {
        AuthSession refreshed = refreshIfNeeded(config, session);
        AuthAccount account = authApiClient.me(config.getAuthBaseUrl(), refreshed.getAccessToken());
        AuthSession enriched = refreshed.withAccount(account);
        persist(enriched);
        return account;
    }

    public GameTicket createGameTicket(LauncherConfig config, AuthSession session) throws IOException {
        AuthSession refreshed = refreshIfNeeded(config, session);
        persist(refreshed);
        return authApiClient.createGameTicket(
            config.getAuthBaseUrl(),
            refreshed.getAccessToken(),
            config.getServerId()
        );
    }

    public void logoutQuietly(LauncherConfig config, AuthSession session) {
        if (session != null) {
            try {
                authApiClient.logout(config.getAuthBaseUrl(), session);
            } catch (IOException ignored) {
            }
        }
        try {
            authSessionStore.clear();
        } catch (IOException ignored) {
        }
    }

    public void persist(AuthSession session) throws IOException {
        if (session == null) {
            authSessionStore.clear();
            return;
        }
        if (session.isPersisted()) {
            authSessionStore.save(session);
        } else {
            authSessionStore.clear();
        }
    }

    private AuthSession remember(AuthSession session, boolean rememberSession) throws IOException {
        AuthSession persisted = session.withPersistence(rememberSession);
        persist(persisted);
        return persisted;
    }

    private static String deviceName() {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        PlatformInfo platform = PlatformInfo.current();
        return LauncherBrand.APP_NAME + "@" + host + " (" + platform.getOs() + "/" + platform.getArch() + ")";
    }
}
