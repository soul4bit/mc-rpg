package ru.mcrpg.launcher;

public final class ModpackSyncPreviewEntry {

    public enum State {
        DOWNLOAD,
        REUSED
    }

    private final String path;
    private final String targetPath;
    private final String sha256;
    private final Long size;
    private final State state;
    private final String reason;

    public ModpackSyncPreviewEntry(
        String path,
        String targetPath,
        String sha256,
        Long size,
        State state,
        String reason
    ) {
        this.path = path;
        this.targetPath = targetPath;
        this.sha256 = sha256;
        this.size = size;
        this.state = state;
        this.reason = reason;
    }

    public String getPath() {
        return path;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getSize() {
        return size;
    }

    public State getState() {
        return state;
    }

    public String getReason() {
        return reason;
    }
}
