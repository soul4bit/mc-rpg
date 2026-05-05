package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModpackSyncServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void syncDownloadsFilesAndAppliesManifestLauncherSettings() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path forgeJar = writeFile(sourceDirectory, "forge-1.12.2-14.23.5.2864.jar", "forge-client");
        Path modJar = writeFile(sourceDirectory, "mods/examplemod.jar", "example-mod");
        Path configFile = writeFile(sourceDirectory, "config/rpg.cfg", "difficulty=hard");

        Path manifest = tempDirectory.resolve("manifest.json");
        Files.write(
            manifest,
            buildManifest(sourceDirectory, forgeJar, modJar, configFile).getBytes(StandardCharsets.UTF_8)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(tempDirectory.resolve("client").toString());
        config.setServerHost("");
        config.setLaunchTemplate("");

        List<String> logLines = new ArrayList<String>();
        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());
        ModpackSyncResult result = service.sync(config, logLines::add);

        assertTrue(Files.exists(tempDirectory.resolve("client/forge-1.12.2-14.23.5.2864.jar")));
        assertTrue(Files.exists(tempDirectory.resolve("client/mods/examplemod.jar")));
        assertTrue(Files.exists(tempDirectory.resolve("client/config/rpg.cfg")));
        assertEquals("192.168.1.103", result.getResolvedConfig().getServerHost());
        assertEquals(25565, result.getResolvedConfig().getServerPort());
        assertEquals(
            "{java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}",
            result.getResolvedConfig().getLaunchTemplate()
        );
        assertTrue(result.getResolvedConfig().getWorkingDirectory().endsWith("client"));
        assertEquals(3, result.getDownloadedFiles());
        assertEquals(0, result.getReusedFiles());
        assertTrue(result.getDownloadedBytes() > 0L);
        assertTrue(logLines.size() >= 4);

        ModpackSyncResult secondRun = service.sync(result.getResolvedConfig(), null);
        assertEquals(0, secondRun.getDownloadedFiles());
        assertEquals(3, secondRun.getReusedFiles());
    }

    @Test
    void syncRejectsPathsOutsideGameDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("source"));
        Path payload = writeFile(sourceDirectory, "mods/examplemod.jar", "example-mod");

        Path manifest = tempDirectory.resolve("manifest.json");
        String manifestJson = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"files\": [\n"
            + "    {\n"
            + "      \"path\": \"../escape.txt\",\n"
            + "      \"url\": \"mods/examplemod.jar\",\n"
            + "      \"sha256\": \"" + ChecksumUtils.sha256(payload) + "\"\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";
        Files.write(manifest, manifestJson.getBytes(StandardCharsets.UTF_8));

        LauncherConfig config = LauncherConfig.defaults();
        config.setManifestUrl(manifest.toUri().toURL().toString());
        config.setGameDirectory(tempDirectory.resolve("client").toString());

        ModpackSyncService service = new ModpackSyncService(new ModpackManifestClient());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.sync(config, null)
        );

        assertEquals("Путь файла выходит за пределы папки игры: ../escape.txt", exception.getMessage());
    }

    private static String buildManifest(Path sourceDirectory, Path forgeJar, Path modJar, Path configFile) throws IOException {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"mc-rpg\",\n"
            + "  \"version\": \"2026.05.05\",\n"
            + "  \"baseUrl\": \"" + sourceDirectory.toUri().toURL().toString() + "\",\n"
            + "  \"launcher\": {\n"
            + "    \"serverHost\": \"192.168.1.103\",\n"
            + "    \"serverPort\": 25565,\n"
            + "    \"workingDirectory\": \".\",\n"
            + "    \"launchTemplate\": \"{java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}\"\n"
            + "  },\n"
            + "  \"files\": [\n"
            + fileJson("forge-1.12.2-14.23.5.2864.jar", forgeJar) + ",\n"
            + fileJson("mods/examplemod.jar", modJar) + ",\n"
            + fileJson("config/rpg.cfg", configFile) + "\n"
            + "  ]\n"
            + "}\n";
    }

    private static String fileJson(String relativePath, Path file) throws IOException {
        return "    {\n"
            + "      \"path\": \"" + relativePath + "\",\n"
            + "      \"sha256\": \"" + ChecksumUtils.sha256(file) + "\",\n"
            + "      \"size\": " + Files.size(file) + "\n"
            + "    }";
    }

    private static Path writeFile(Path root, String relativePath, String content) throws IOException {
        Path path = root.resolve(relativePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
