package ru.mcrpg.launcher;

import java.net.URL;
import java.nio.file.Path;

final class LauncherUpdateCandidate {

    private final LauncherUpdateSettings settings;
    private final String currentVersion;
    private final URL downloadUrl;
    private final Path currentLauncherPath;

    LauncherUpdateCandidate(
        LauncherUpdateSettings settings,
        String currentVersion,
        URL downloadUrl,
        Path currentLauncherPath
    ) {
        this.settings = settings;
        this.currentVersion = currentVersion == null ? "" : currentVersion.trim();
        this.downloadUrl = downloadUrl;
        this.currentLauncherPath = currentLauncherPath;
    }

    String getVersion() {
        return settings.getVersion();
    }

    String getCurrentVersion() {
        return currentVersion;
    }

    URL getDownloadUrl() {
        return downloadUrl;
    }

    String getSha256() {
        return settings.getSha256();
    }

    Long getSize() {
        return settings.getSize();
    }

    boolean isRequired() {
        return settings.isRequired();
    }

    boolean isInstallSupported() {
        return currentLauncherPath != null;
    }

    Path getCurrentLauncherPath() {
        return currentLauncherPath;
    }
}
