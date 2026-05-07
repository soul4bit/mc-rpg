package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class LauncherConfigStore {

    private static final String KEY_USERNAME = "username";
    private static final String KEY_JAVA_COMMAND = "java.command";
    private static final String KEY_GAME_DIRECTORY = "game.directory";
    private static final String KEY_WORKING_DIRECTORY = "working.directory";
    private static final String KEY_SERVER_HOST = "server.host";
    private static final String KEY_SERVER_PORT = "server.port";
    private static final String KEY_LAUNCH_TEMPLATE = "launch.template";
    private static final String KEY_MANIFEST_URL = "manifest.url";
    private static final String KEY_AUTH_BASE_URL = "auth.base.url";
    private static final String KEY_SERVER_ID = "server.id";
    private static final String KEY_UPDATE_FILES_BEFORE_LAUNCH = "update.files.before.launch";

    private final Path configFile;

    public LauncherConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    public static LauncherConfigStore defaultStore() {
        Path configFile = Paths.get(
            System.getProperty("user.home"),
            ".obsidian-gate-launcher",
            "launcher.properties"
        );
        return new LauncherConfigStore(configFile);
    }

    public LauncherConfig load() throws IOException {
        LauncherConfig defaults = LauncherConfig.defaults();
        if (!Files.exists(configFile)) {
            return defaults;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            properties.load(inputStream);
        }

        defaults.setUsername(properties.getProperty(KEY_USERNAME, defaults.getUsername()));
        defaults.setJavaCommand(properties.getProperty(KEY_JAVA_COMMAND, defaults.getJavaCommand()));
        defaults.setGameDirectory(properties.getProperty(KEY_GAME_DIRECTORY, defaults.getGameDirectory()));
        defaults.setWorkingDirectory(properties.getProperty(KEY_WORKING_DIRECTORY, defaults.getWorkingDirectory()));
        defaults.setServerHost(properties.getProperty(KEY_SERVER_HOST, defaults.getServerHost()));
        defaults.setServerPort(parsePort(
            properties.getProperty(KEY_SERVER_PORT),
            defaults.getServerPort()
        ));
        defaults.setLaunchTemplate(properties.getProperty(KEY_LAUNCH_TEMPLATE, defaults.getLaunchTemplate()));
        defaults.setManifestUrl(properties.getProperty(KEY_MANIFEST_URL, defaults.getManifestUrl()));
        defaults.setAuthBaseUrl(properties.getProperty(KEY_AUTH_BASE_URL, defaults.getAuthBaseUrl()));
        defaults.setServerId(properties.getProperty(KEY_SERVER_ID, defaults.getServerId()));
        defaults.setUpdateFilesBeforeLaunch(parseBoolean(
            properties.getProperty(KEY_UPDATE_FILES_BEFORE_LAUNCH),
            defaults.isUpdateFilesBeforeLaunch()
        ));
        return LauncherDefaults.applyMissingValues(defaults);
    }

    public void save(LauncherConfig config) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(KEY_USERNAME, config.getUsername());
        properties.setProperty(KEY_JAVA_COMMAND, config.getJavaCommand());
        properties.setProperty(KEY_GAME_DIRECTORY, config.getGameDirectory());
        properties.setProperty(KEY_WORKING_DIRECTORY, config.getWorkingDirectory());
        properties.setProperty(KEY_SERVER_HOST, config.getServerHost());
        properties.setProperty(KEY_SERVER_PORT, Integer.toString(config.getServerPort()));
        properties.setProperty(KEY_LAUNCH_TEMPLATE, config.getLaunchTemplate());
        properties.setProperty(KEY_MANIFEST_URL, config.getManifestUrl());
        properties.setProperty(KEY_AUTH_BASE_URL, config.getAuthBaseUrl());
        properties.setProperty(KEY_SERVER_ID, config.getServerId());
        properties.setProperty(KEY_UPDATE_FILES_BEFORE_LAUNCH, Boolean.toString(config.isUpdateFilesBeforeLaunch()));

        Path parent = configFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream outputStream = Files.newOutputStream(configFile)) {
            properties.store(outputStream, "ObsidianGate launcher settings");
        }
    }

    public Path getConfigFile() {
        return configFile;
    }

    private static int parsePort(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
