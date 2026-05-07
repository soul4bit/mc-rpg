package ru.mcrpg.launcher;

import java.time.Duration;
import java.time.Instant;

public final class AuthSession {

    private final AuthAccount account;
    private final String accessToken;
    private final String refreshToken;
    private final Instant expiresAt;
    private final boolean persisted;

    public AuthSession(AuthAccount account, String accessToken, String refreshToken, Instant expiresAt, boolean persisted) {
        this.account = account;
        this.accessToken = normalize(accessToken);
        this.refreshToken = normalize(refreshToken);
        this.expiresAt = expiresAt == null ? Instant.EPOCH : expiresAt;
        this.persisted = persisted;
    }

    public AuthAccount getAccount() {
        return account;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isPersisted() {
        return persisted;
    }

    public boolean isAccessTokenExpiringWithin(Duration duration) {
        Duration threshold = duration == null ? Duration.ZERO : duration;
        return expiresAt.minus(threshold).isBefore(Instant.now());
    }

    public AuthSession withPersistence(boolean value) {
        return new AuthSession(account, accessToken, refreshToken, expiresAt, value);
    }

    public AuthSession withTokens(String newAccessToken, String newRefreshToken, Instant newExpiresAt) {
        return new AuthSession(account, newAccessToken, newRefreshToken, newExpiresAt, persisted);
    }

    public AuthSession withAccount(AuthAccount newAccount) {
        return new AuthSession(newAccount, accessToken, refreshToken, expiresAt, persisted);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
