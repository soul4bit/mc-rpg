package ru.mcrpg.launcher;

public final class LauncherManifestSettings {

    private String serverHost;
    private Integer serverPort;
    private String launchTemplate;
    private String workingDirectory;
    private String authBaseUrl;
    private String serverId;

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public String getLaunchTemplate() {
        return launchTemplate;
    }

    public void setLaunchTemplate(String launchTemplate) {
        this.launchTemplate = launchTemplate;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
