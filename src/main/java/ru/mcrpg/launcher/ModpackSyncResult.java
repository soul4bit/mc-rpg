package ru.mcrpg.launcher;

public final class ModpackSyncResult {

    private final LauncherConfig resolvedConfig;
    private final ModpackManifest manifest;
    private final int downloadedFiles;
    private final int reusedFiles;
    private final int removedFiles;
    private final long downloadedBytes;

    public ModpackSyncResult(
        LauncherConfig resolvedConfig,
        ModpackManifest manifest,
        int downloadedFiles,
        int reusedFiles,
        int removedFiles,
        long downloadedBytes
    ) {
        this.resolvedConfig = resolvedConfig;
        this.manifest = manifest;
        this.downloadedFiles = downloadedFiles;
        this.reusedFiles = reusedFiles;
        this.removedFiles = removedFiles;
        this.downloadedBytes = downloadedBytes;
    }

    public LauncherConfig getResolvedConfig() {
        return resolvedConfig;
    }

    public ModpackManifest getManifest() {
        return manifest;
    }

    public int getDownloadedFiles() {
        return downloadedFiles;
    }

    public int getReusedFiles() {
        return reusedFiles;
    }

    public int getRemovedFiles() {
        return removedFiles;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }
}
