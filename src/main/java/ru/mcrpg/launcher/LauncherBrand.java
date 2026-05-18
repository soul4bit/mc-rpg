package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class LauncherBrand {

    public static final String APP_NAME = "ObsidianGate Launcher";
    public static final String APP_TITLE = "ObsidianGate";
    public static final String APP_SUBTITLE = "Лаунчер RPG-сервера";

    private LauncherBrand() {
    }

    public static String displayVersion() {
        String version = LauncherBrand.class.getPackage().getImplementationVersion();
        if (hasText(version)) {
            return version.trim();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = LauncherBrand.class.getResourceAsStream(
            "/META-INF/maven/ru.mcrpg/obsidian-gate-launcher/pom.properties"
        )) {
            if (inputStream != null) {
                properties.load(inputStream);
                version = properties.getProperty("version");
                if (hasText(version)) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
        }

        return "dev";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
