package ru.mcrpg.launcher;

public final class LauncherManifestSettings {

    private String serverHost;
    private Integer serverPort;
    private String launchTemplate;
    private String workingDirectory;

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
}

