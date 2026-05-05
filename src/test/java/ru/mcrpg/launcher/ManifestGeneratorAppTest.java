package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestGeneratorAppTest {

    @TempDir
    Path tempDirectory;

    @Test
    void runReturnsUsageForHelp() throws Exception {
        ManifestGeneratorApp app = new ManifestGeneratorApp();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = app.run(
            new String[] {"--help"},
            new PrintStream(output, true, StandardCharsets.UTF_8.name()),
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name())
        );

        assertEquals(0, exitCode);
        assertTrue(new String(output.toByteArray(), StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void runFailsWhenSourceIsMissing() throws Exception {
        ManifestGeneratorApp app = new ManifestGeneratorApp();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = app.run(
            new String[] {"--output", tempDirectory.resolve("manifest.json").toString()},
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
            new PrintStream(error, true, StandardCharsets.UTF_8.name())
        );

        assertEquals(2, exitCode);
        assertTrue(new String(error.toByteArray(), StandardCharsets.UTF_8).contains("Missing required option --source."));
    }

    @Test
    void runGeneratesManifestFile() throws Exception {
        Path source = Files.createDirectories(tempDirectory.resolve("client"));
        Files.write(source.resolve("mods.jar"), "payload".getBytes(StandardCharsets.UTF_8));
        Path output = tempDirectory.resolve("manifest.json");

        ManifestGeneratorApp app = new ManifestGeneratorApp();
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();

        int exitCode = app.run(
            new String[] {
                "--source", source.toString(),
                "--output", output.toString(),
                "--version", "2026.05.05"
            },
            new PrintStream(standardOutput, true, StandardCharsets.UTF_8.name()),
            new PrintStream(standardError, true, StandardCharsets.UTF_8.name())
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output));
        assertTrue(new String(standardOutput.toByteArray(), StandardCharsets.UTF_8).contains("Manifest generated:"));
        assertEquals("", new String(standardError.toByteArray(), StandardCharsets.UTF_8));
    }
}
