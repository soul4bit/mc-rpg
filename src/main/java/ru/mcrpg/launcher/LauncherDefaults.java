package ru.mcrpg.launcher;

import java.nio.file.Paths;

public final class LauncherDefaults {

    private static final String DEFAULT_GAME_DIRECTORY_NAME = "rpg-client";
    private static final String DEFAULT_MANIFEST_PATH = "/manifest.json";

    private LauncherDefaults() {
    }

    public static LauncherConfig applyMissingValues(LauncherConfig config) {
        if (!hasText(config.getUsername())) {
            config.setUsername(defaultUsername());
        }
        if (!hasText(config.getJavaCommand())) {
            config.setJavaCommand("java");
        }
        if (!hasText(config.getGameDirectory())) {
            config.setGameDirectory(defaultGameDirectory());
        }
        if (!hasText(config.getServerHost())) {
            config.setServerHost(LauncherConfig.DEFAULT_SERVER_HOST);
        }
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            config.setServerPort(LauncherConfig.DEFAULT_SERVER_PORT);
        }
        if (!hasText(config.getManifestUrl())) {
            config.setManifestUrl(defaultManifestUrl(config.getServerHost()));
        }
        if (!hasText(config.getLaunchTemplate())) {
            config.setLaunchTemplate(LauncherConfig.DEFAULT_LAUNCH_TEMPLATE);
        }
        return config;
    }

    public static String defaultGameDirectory() {
        return Paths.get(System.getProperty("user.home"), DEFAULT_GAME_DIRECTORY_NAME).toString();
    }

    public static String defaultManifestUrl(String serverHost) {
        String host = hasText(serverHost) ? serverHost.trim() : LauncherConfig.DEFAULT_SERVER_HOST;
        return "http://" + host + DEFAULT_MANIFEST_PATH;
    }

    public static String defaultUsername() {
        String raw = System.getProperty("user.name", "");
        String sanitized = raw.replaceAll("[^A-Za-z0-9_]", "");
        if (sanitized.length() >= 3 && sanitized.length() <= 16) {
            return sanitized;
        }
        return "Player";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
