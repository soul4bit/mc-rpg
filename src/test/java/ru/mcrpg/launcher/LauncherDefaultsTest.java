package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LauncherDefaultsTest {

    @Test
    void applyMissingValuesFillsZeroConfigLauncherFields() {
        LauncherConfig config = new LauncherConfig();
        config.setUsername("");
        config.setJavaCommand("");
        config.setManifestUrl("");
        config.setGameDirectory("");
        config.setWorkingDirectory("");
        config.setServerHost("");
        config.setServerPort(0);
        config.setLaunchTemplate("");
        config.setUpdateFilesBeforeLaunch(true);

        LauncherDefaults.applyMissingValues(config);

        assertFalse(config.getUsername().isEmpty());
        assertEquals("java", config.getJavaCommand());
        assertFalse(config.getGameDirectory().isEmpty());
        assertEquals(LauncherConfig.DEFAULT_SERVER_HOST, config.getServerHost());
        assertEquals(LauncherConfig.DEFAULT_SERVER_PORT, config.getServerPort());
        assertEquals(
            "http://" + LauncherConfig.DEFAULT_SERVER_HOST + "/manifest.json",
            config.getManifestUrl()
        );
        assertEquals(LauncherConfig.DEFAULT_LAUNCH_TEMPLATE, config.getLaunchTemplate());
    }

    @Test
    void defaultManifestUrlUsesConfiguredServerHost() {
        assertEquals(
            "http://example.local/manifest.json",
            LauncherDefaults.defaultManifestUrl("example.local")
        );
        assertTrue(LauncherDefaults.defaultGameDirectory().contains("mc-rpg-client"));
    }
}
