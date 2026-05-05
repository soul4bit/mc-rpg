package ru.mcrpg.launcher;

import java.nio.file.Path;

public final class ManifestGenerationResult {

    private final Path outputFile;
    private final ModpackManifest manifest;

    public ManifestGenerationResult(Path outputFile, ModpackManifest manifest) {
        this.outputFile = outputFile;
        this.manifest = manifest;
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public ModpackManifest getManifest() {
        return manifest;
    }
}

