package ru.mcrpg.launcher;

public final class LauncherConfig {

    public static final String DEFAULT_SERVER_HOST = "192.168.1.103";
    public static final int DEFAULT_SERVER_PORT = 25565;
    public static final String DEFAULT_LAUNCH_TEMPLATE =
        "{java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}";

    private String username;
    private String javaCommand;
    private String gameDirectory;
    private String workingDirectory;
    private String serverHost;
    private int serverPort;
    private String launchTemplate;

    public static LauncherConfig defaults() {
        LauncherConfig config = new LauncherConfig();
        config.setUsername("Player");
        config.setJavaCommand("java");
        config.setGameDirectory("");
        config.setWorkingDirectory("");
        config.setServerHost(DEFAULT_SERVER_HOST);
        config.setServerPort(DEFAULT_SERVER_PORT);
        config.setLaunchTemplate(DEFAULT_LAUNCH_TEMPLATE);
        return config;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getJavaCommand() {
        return javaCommand;
    }

    public void setJavaCommand(String javaCommand) {
        this.javaCommand = javaCommand;
    }

    public String getGameDirectory() {
        return gameDirectory;
    }

    public void setGameDirectory(String gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getLaunchTemplate() {
        return launchTemplate;
    }

    public void setLaunchTemplate(String launchTemplate) {
        this.launchTemplate = launchTemplate;
    }
}

