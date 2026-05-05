package ru.mcrpg.launcher;

public final class MinecraftBootstrapSettings {

    private String version = "";
    private String forgeVersion = "";
    private String versionManifestUrl = "";
    private String forgeInstallerUrl = "";
    private String assetBaseUrl = "";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = sanitize(version);
    }

    public String getForgeVersion() {
        return forgeVersion;
    }

    public void setForgeVersion(String forgeVersion) {
        this.forgeVersion = sanitize(forgeVersion);
    }

    public String getVersionManifestUrl() {
        return versionManifestUrl;
    }

    public void setVersionManifestUrl(String versionManifestUrl) {
        this.versionManifestUrl = sanitize(versionManifestUrl);
    }

    public String getForgeInstallerUrl() {
        return forgeInstallerUrl;
    }

    public void setForgeInstallerUrl(String forgeInstallerUrl) {
        this.forgeInstallerUrl = sanitize(forgeInstallerUrl);
    }

    public String getAssetBaseUrl() {
        return assetBaseUrl;
    }

    public void setAssetBaseUrl(String assetBaseUrl) {
        this.assetBaseUrl = sanitize(assetBaseUrl);
    }

    public boolean isEnabled() {
        return hasText(version) || hasText(forgeVersion) || hasText(versionManifestUrl) || hasText(forgeInstallerUrl);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
