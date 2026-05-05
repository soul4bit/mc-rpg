package ru.mcrpg.launcher;

import java.net.URL;

public final class LoadedManifest {

    private final URL sourceUrl;
    private final ModpackManifest manifest;

    public LoadedManifest(URL sourceUrl, ModpackManifest manifest) {
        this.sourceUrl = sourceUrl;
        this.manifest = manifest;
    }

    public URL getSourceUrl() {
        return sourceUrl;
    }

    public ModpackManifest getManifest() {
        return manifest;
    }
}

