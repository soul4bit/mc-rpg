package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherBackendCliTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void bootstrapReturnsConfigAndHomeContent() throws Exception {
        CliHarness harness = createCli("", tempDir.resolve("launcher.properties"));

        int exitCode = harness.cli.run(new String[] {"bootstrap"});

        assertEquals(0, exitCode);
        JsonNode event = firstEvent(harness.outputStream);
        assertEquals("result", event.get("type").asText());
        assertEquals("Redstone Launcher", event.get("brand").get("appName").asText());
        assertNotNull(event.get("config"));
        assertTrue(event.get("config").get("username").asText().length() >= 1);
        assertTrue(event.get("homeContent").get("heroTitle").asText().length() >= 1);
    }

    @Test
    void previewCommandUsesProvidedConfig() throws Exception {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("BridgeUser");
        config.setGameDirectory("D:/games/mc-rpg");

        String json = objectMapper.writeValueAsString(config);
        CliHarness harness = createCli(json, tempDir.resolve("launcher.properties"));

        int exitCode = harness.cli.run(new String[] {"preview-command"});

        assertEquals(0, exitCode);
        JsonNode event = firstEvent(harness.outputStream);
        assertEquals("result", event.get("type").asText());
        assertTrue(event.get("preview").asText().contains("BridgeUser"));
        assertTrue(event.get("preview").asText().contains("forge-1.12.2-14.23.5.2864.jar"));
    }

    private CliHarness createCli(String input, Path configFile) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        return new CliHarness(
            new LauncherBackendCli(
                new LauncherConfigStore(configFile),
                new LaunchCommandBuilder(),
                new ModpackSyncService(new ModpackManifestClient()),
                new LauncherHomeContentLoader(),
                inputStream,
                outputStream
            ),
            outputStream
        );
    }

    private JsonNode firstEvent(ByteArrayOutputStream outputStream) throws Exception {
        String[] lines = outputStream.toString(StandardCharsets.UTF_8).trim().split("\\R");
        return objectMapper.readTree(lines[0]);
    }

    private static final class CliHarness {

        private final LauncherBackendCli cli;
        private final ByteArrayOutputStream outputStream;

        private CliHarness(LauncherBackendCli cli, ByteArrayOutputStream outputStream) {
            this.cli = cli;
            this.outputStream = outputStream;
        }
    }
}
