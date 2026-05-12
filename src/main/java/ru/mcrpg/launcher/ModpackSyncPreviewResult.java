package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModpackSyncPreviewResult {

    private final LauncherConfig resolvedConfig;
    private final ModpackManifest manifest;
    private final List<ModpackSyncPreviewEntry> entries;
    private final int downloadFiles;
    private final int reusedFiles;
    private final long downloadBytes;

    public ModpackSyncPreviewResult(
        LauncherConfig resolvedConfig,
        ModpackManifest manifest,
        List<ModpackSyncPreviewEntry> entries,
        int downloadFiles,
        int reusedFiles,
        long downloadBytes
    ) {
        this.resolvedConfig = resolvedConfig;
        this.manifest = manifest;
        this.entries = Collections.unmodifiableList(new ArrayList<ModpackSyncPreviewEntry>(entries));
        this.downloadFiles = downloadFiles;
        this.reusedFiles = reusedFiles;
        this.downloadBytes = downloadBytes;
    }

    public LauncherConfig getResolvedConfig() {
        return resolvedConfig;
    }

    public ModpackManifest getManifest() {
        return manifest;
    }

    public List<ModpackSyncPreviewEntry> getEntries() {
        return entries;
    }

    public int getDownloadFiles() {
        return downloadFiles;
    }

    public int getReusedFiles() {
        return reusedFiles;
    }

    public long getDownloadBytes() {
        return downloadBytes;
    }
}
