package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.List;

public final class ModpackManifest {

    private int schemaVersion = 1;
    private String id;
    private String version;
    private String baseUrl;
    private LauncherManifestSettings launcher = new LauncherManifestSettings();
    private List<ModpackFile> files = new ArrayList<ModpackFile>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public LauncherManifestSettings getLauncher() {
        return launcher;
    }

    public void setLauncher(LauncherManifestSettings launcher) {
        this.launcher = launcher == null ? new LauncherManifestSettings() : launcher;
    }

    public List<ModpackFile> getFiles() {
        return files;
    }

    public void setFiles(List<ModpackFile> files) {
        this.files = files == null ? new ArrayList<ModpackFile>() : files;
    }
}

