package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModpackManifestGeneratorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void generateBuildsManifestFromClientDirectory() throws Exception {
        Path client = Files.createDirectories(tempDirectory.resolve("client"));
        Path rootJar = writeFile(client, "forge-1.12.2-14.23.5.2864.jar", "forge");
        Path modJar = writeFile(client, "mods/examplemod.jar", "mod");
        Path runtimeArchive = writeFile(tempDirectory, "runtime/jre8.zip", "portable-jre");
        writeFile(client, "logs/latest.log", "ignore-me");

        ManifestGeneratorConfig config = new ManifestGeneratorConfig();
        config.setSourceDirectory(client);
        config.setOutputFile(client.resolve("manifest.json"));
        config.setModpackId("mc-rpg");
        config.setModpackVersion("2026.05.05");
        config.setBaseUrl("http://192.168.1.103/client/");
        config.setRuntimeArchive(runtimeArchive);
        config.setRuntimeUrl("runtime/windows-x64/jre8.zip");
        config.setMinecraftVersion("1.12.2");
        config.setForgeVersion("14.23.5.2864");
        config.getExcludePatterns().add("logs/**");

        ModpackManifestGenerator generator = new ModpackManifestGenerator();
        ModpackManifest manifest = generator.generate(config);

        assertEquals(1, manifest.getSchemaVersion());
        assertEquals("mc-rpg", manifest.getId());
        assertEquals("2026.05.05", manifest.getVersion());
        assertEquals("http://192.168.1.103/client/", manifest.getBaseUrl());
        assertEquals("1.12.2", manifest.getMinecraft().getVersion());
        assertEquals("14.23.5.2864", manifest.getMinecraft().getForgeVersion());
        assertEquals(1, manifest.getRuntime().getPackages().size());
        assertEquals(2, manifest.getFiles().size());

        RuntimePackage runtimePackage = manifest.getRuntime().getPackages().get(0);
        assertEquals("windows", runtimePackage.getOs());
        assertEquals("x86_64", runtimePackage.getArch());
        assertEquals("runtime/windows-x64/jre8.zip", runtimePackage.getUrl());
        assertEquals(ChecksumUtils.sha256(runtimeArchive), runtimePackage.getSha256());
        assertEquals(Long.valueOf(Files.size(runtimeArchive)), runtimePackage.getSize());
        assertEquals("runtime/jre8", runtimePackage.getExtractDir());
        assertEquals("bin/java.exe", runtimePackage.getJavaPath());

        ModpackFile first = manifest.getFiles().get(0);
        assertEquals("forge-1.12.2-14.23.5.2864.jar", first.getPath());
        assertEquals(Long.valueOf(Files.size(rootJar)), first.getSize());
        assertEquals(ChecksumUtils.sha256(rootJar), first.getSha256());

        ModpackFile second = manifest.getFiles().get(1);
        assertEquals("mods/examplemod.jar", second.getPath());
        assertEquals(Long.valueOf(Files.size(modJar)), second.getSize());
        assertEquals(ChecksumUtils.sha256(modJar), second.getSha256());
    }

    @Test
    void writeSkipsOutputManifestInsideSourceDirectory() throws Exception {
        Path client = Files.createDirectories(tempDirectory.resolve("client"));
        writeFile(client, "mods/examplemod.jar", "mod");

        ManifestGeneratorConfig config = new ManifestGeneratorConfig();
        config.setSourceDirectory(client);
        config.setOutputFile(client.resolve("manifest.json"));
        config.setModpackVersion("2026.05.05");

        ModpackManifestGenerator generator = new ModpackManifestGenerator();
        ManifestGenerationResult result = generator.generateAndWrite(config);
        Path output = result.getOutputFile();
        assertTrue(Files.exists(output));

        ModpackManifestClient clientReader = new ModpackManifestClient();
        LoadedManifest loaded = clientReader.load(output.toUri().toURL().toString());

        assertEquals(1, loaded.getManifest().getFiles().size());
        assertEquals("mods/examplemod.jar", loaded.getManifest().getFiles().get(0).getPath());
        assertNotNull(loaded.getManifest().getLauncher());
        assertEquals(".", loaded.getManifest().getLauncher().getWorkingDirectory());
    }

    @Test
    void generateMarksExecutableFiles() throws Exception {
        Path client = Files.createDirectories(tempDirectory.resolve("client"));
        Path script = writeFile(client, "start-client.sh", "#!/bin/sh\necho test\n");
        script.toFile().setExecutable(true, false);

        ManifestGeneratorConfig config = new ManifestGeneratorConfig();
        config.setSourceDirectory(client);
        config.setOutputFile(tempDirectory.resolve("manifest.json"));
        config.setModpackVersion("2026.05.05");

        ModpackManifest manifest = new ModpackManifestGenerator().generate(config);

        assertEquals(1, manifest.getFiles().size());
        assertEquals("start-client.sh", manifest.getFiles().get(0).getPath());
        assertTrue(manifest.getFiles().get(0).isExecutable() || !Files.isExecutable(script));
        assertFalse(manifest.getFiles().get(0).getPath().contains("\\"));
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
