package ru.mcrpg.launcher;

import java.nio.file.Path;

public final class LaunchIdentity {

    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String userType;
    private final Path sessionFile;

    private LaunchIdentity(String username, String uuid, String accessToken, String userType, Path sessionFile) {
        this.username = normalize(username);
        this.uuid = normalize(uuid);
        this.accessToken = normalize(accessToken);
        this.userType = normalize(userType);
        this.sessionFile = sessionFile;
    }

    public static LaunchIdentity authenticated(String username, String uuid, String accessToken, Path sessionFile) {
        return new LaunchIdentity(username, uuid, accessToken, "legacy", sessionFile);
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUserType() {
        return userType;
    }

    public Path getSessionFile() {
        return sessionFile;
    }

    public boolean hasSessionFile() {
        return sessionFile != null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
