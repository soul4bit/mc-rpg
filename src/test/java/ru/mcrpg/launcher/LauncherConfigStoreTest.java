package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherConfigStoreTest {

    @TempDir
    Path tempDirectory;

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        Path configFile = tempDirectory.resolve("launcher.properties");
        LauncherConfigStore store = new LauncherConfigStore(configFile);

        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("Knight");
        config.setJavaCommand("/usr/bin/java");
        config.setGameDirectory("/home/player/.minecraft");
        config.setWorkingDirectory("/home/player/.minecraft");
        config.setServerHost("192.168.1.103");
        config.setServerPort(25565);
        config.setLaunchTemplate("{java} -jar forge.jar --username {username}");
        config.setManifestUrl("https://example.com/manifest.json");
        config.setAuthBaseUrl("https://example.com/api");
        config.setServerId("obsidiangate-main");
        config.setUpdateFilesBeforeLaunch(false);

        store.save(config);
        LauncherConfig restored = store.load();

        assertEquals("Knight", restored.getUsername());
        assertEquals("/usr/bin/java", restored.getJavaCommand());
        assertEquals("/home/player/.minecraft", restored.getGameDirectory());
        assertEquals("/home/player/.minecraft", restored.getWorkingDirectory());
        assertEquals("192.168.1.103", restored.getServerHost());
        assertEquals(25565, restored.getServerPort());
        assertEquals("{java} -jar forge.jar --username {username}", restored.getLaunchTemplate());
        assertEquals("https://example.com/manifest.json", restored.getManifestUrl());
        assertEquals("https://example.com/api", restored.getAuthBaseUrl());
        assertEquals("obsidiangate-main", restored.getServerId());
        assertEquals(false, restored.isUpdateFilesBeforeLaunch());
    }

    @Test
    void loadMigratesLegacyManifestUrlWithoutPort() throws IOException {
        Path configFile = tempDirectory.resolve("launcher.properties");
        LauncherConfigStore store = new LauncherConfigStore(configFile);

        LauncherConfig config = LauncherConfig.defaults();
        config.setServerHost("192.168.1.103");
        config.setManifestUrl("http://192.168.1.103/manifest.json");

        store.save(config);
        LauncherConfig restored = store.load();

        assertEquals("http://192.168.1.103:8080/manifest.json", restored.getManifestUrl());
    }
}
