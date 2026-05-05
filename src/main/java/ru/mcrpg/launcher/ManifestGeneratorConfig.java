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
    private Path runtimeArchive;
    private String runtimeUrl = "";
    private String runtimeOs = "windows";
    private String runtimeArch = "x86_64";
    private String runtimeExtractDir = "runtime/jre8";
    private String runtimeJavaPath = "bin/java.exe";
    private String minecraftVersion = "";
    private String forgeVersion = "";
    private String versionManifestUrl = "";
    private String forgeInstallerUrl = "";
    private String assetBaseUrl = "";
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

    public Path getRuntimeArchive() {
        return runtimeArchive;
    }

    public void setRuntimeArchive(Path runtimeArchive) {
        this.runtimeArchive = runtimeArchive;
    }

    public String getRuntimeUrl() {
        return runtimeUrl;
    }

    public void setRuntimeUrl(String runtimeUrl) {
        this.runtimeUrl = runtimeUrl;
    }

    public String getRuntimeOs() {
        return runtimeOs;
    }

    public void setRuntimeOs(String runtimeOs) {
        this.runtimeOs = runtimeOs;
    }

    public String getRuntimeArch() {
        return runtimeArch;
    }

    public void setRuntimeArch(String runtimeArch) {
        this.runtimeArch = runtimeArch;
    }

    public String getRuntimeExtractDir() {
        return runtimeExtractDir;
    }

    public void setRuntimeExtractDir(String runtimeExtractDir) {
        this.runtimeExtractDir = runtimeExtractDir;
    }

    public String getRuntimeJavaPath() {
        return runtimeJavaPath;
    }

    public void setRuntimeJavaPath(String runtimeJavaPath) {
        this.runtimeJavaPath = runtimeJavaPath;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String getForgeVersion() {
        return forgeVersion;
    }

    public void setForgeVersion(String forgeVersion) {
        this.forgeVersion = forgeVersion;
    }

    public String getVersionManifestUrl() {
        return versionManifestUrl;
    }

    public void setVersionManifestUrl(String versionManifestUrl) {
        this.versionManifestUrl = versionManifestUrl;
    }

    public String getForgeInstallerUrl() {
        return forgeInstallerUrl;
    }

    public void setForgeInstallerUrl(String forgeInstallerUrl) {
        this.forgeInstallerUrl = forgeInstallerUrl;
    }

    public String getAssetBaseUrl() {
        return assetBaseUrl;
    }

    public void setAssetBaseUrl(String assetBaseUrl) {
        this.assetBaseUrl = assetBaseUrl;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }
}
