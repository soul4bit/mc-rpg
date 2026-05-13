package ru.mcrpg.launcher;

public final class LauncherUpdateSettings {

    private String version;
    private String url;
    private String sha256;
    private Long size;
    private boolean required;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = sanitize(version);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = sanitize(url);
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sanitize(sha256);
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value;
    }
}
