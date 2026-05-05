package ru.mcrpg.launcher;

public final class MinecraftBootstrapResult {

    private final String launchTemplate;
    private final String workingDirectory;

    public MinecraftBootstrapResult(String launchTemplate, String workingDirectory) {
        this.launchTemplate = launchTemplate == null ? "" : launchTemplate;
        this.workingDirectory = workingDirectory == null ? "" : workingDirectory;
    }

    public String getLaunchTemplate() {
        return launchTemplate;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }
}
