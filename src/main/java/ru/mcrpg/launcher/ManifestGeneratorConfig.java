package ru.mcrpg.launcher;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ManifestGeneratorConfig {

    private static final DateTimeFormatter VERSION_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private Path sourceDirectory;
    private Path outputFile;
    private String modpackId = "mc-rpg";
    private String modpackVersion = LocalDate.now().format(VERSION_DATE_FORMAT);
    private String baseUrl = "";
    private String serverHost = LauncherConfig.DEFAULT_SERVER_HOST;
    private Integer serverPort = Integer.valueOf(LauncherConfig.DEFAULT_SERVER_PORT);
    private String workingDirectory = ".";
    private String launchTemplate = LauncherConfig.DEFAULT_LAUNCH_TEMPLATE;
    private final List<String> excludePatterns = new ArrayList<String>();

    public Path getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(Path sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(Path outputFile) {
        this.outputFile = outputFile;
    }

    public String getModpackId() {
        return modpackId;
    }

    public void setModpackId(String modpackId) {
        this.modpackId = modpackId;
    }

    public String getModpackVersion() {
        return modpackVersion;
    }

    public void setModpackVersion(String modpackVersion) {
        this.modpackVersion = modpackVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

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

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getLaunchTemplate() {
        return launchTemplate;
    }

    public void setLaunchTemplate(String launchTemplate) {
        this.launchTemplate = launchTemplate;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }
}

