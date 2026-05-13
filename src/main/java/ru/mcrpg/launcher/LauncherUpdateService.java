package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class LauncherUpdateService {

    private static final int LAUNCHER_DOWNLOAD_READ_TIMEOUT_MS = 60000;

    interface LogSink {
        void log(String message);
    }

    private final Path currentLauncherPath;

    LauncherUpdateService() {
        this(detectCurrentLauncherPath());
    }

    LauncherUpdateService(Path currentLauncherPath) {
        this.currentLauncherPath = currentLauncherPath;
    }

    LauncherUpdateCandidate findUpdate(LoadedManifest loadedManifest, String currentVersion) throws IOException {
        if (loadedManifest == null || loadedManifest.getManifest() == null) {
            return null;
        }

        LauncherUpdateSettings settings = loadedManifest.getManifest().getLauncherUpdate();
        if (!isComplete(settings)) {
            return null;
        }

        URL downloadUrl = DownloadUrlResolver.resolve(loadedManifest.getSourceUrl(), settings.getUrl());
        if (!shouldOfferUpdate(settings, currentVersion)) {
            return null;
        }

        return new LauncherUpdateCandidate(settings, currentVersion, downloadUrl, currentLauncherPath);
    }

    void installAndRestart(LauncherUpdateCandidate candidate, LogSink logSink) throws IOException {
        if (candidate == null) {
            throw new IllegalArgumentException("Launcher update is not available.");
        }
        if (!candidate.isInstallSupported()) {
            throw new IOException("Auto-update is available only when launcher is running from a jar file.");
        }

        Path updateFile = downloadUpdate(candidate, logSink);
        Path script = createRestartScript(updateFile, candidate.getCurrentLauncherPath());
        log(logSink, "Starting launcher updater script: " + script);
        startUpdaterScript(script);
    }

    private Path downloadUpdate(LauncherUpdateCandidate candidate, LogSink logSink) throws IOException {
        Path updatesDirectory = Paths.get(
            System.getProperty("user.home"),
            ".obsidian-gate-launcher",
            "updates"
        ).toAbsolutePath().normalize();
        Files.createDirectories(updatesDirectory);

        Path fileName = Paths.get(candidate.getCurrentLauncherPath().getFileName().toString());
        Path target = updatesDirectory.resolve(fileName).normalize();
        log(logSink, "Downloading launcher update " + candidate.getVersion() + " from " + candidate.getDownloadUrl());
        DownloadUtils.download(candidate.getDownloadUrl(), target, LAUNCHER_DOWNLOAD_READ_TIMEOUT_MS);
        verifyUpdateFile(target, candidate);
        return target;
    }

    private void verifyUpdateFile(Path updateFile, LauncherUpdateCandidate candidate) throws IOException {
        if (candidate.getSize() != null && candidate.getSize().longValue() >= 0L) {
            long actualSize = Files.size(updateFile);
            if (actualSize != candidate.getSize().longValue()) {
                throw new IOException(
                    "Launcher update size mismatch. Expected " + candidate.getSize() + ", got " + actualSize + "."
                );
            }
        }

        String actualSha256 = ChecksumUtils.sha256(updateFile);
        if (!actualSha256.equalsIgnoreCase(candidate.getSha256())) {
            throw new IOException(
                "Launcher update SHA-256 mismatch. Expected " + candidate.getSha256() + ", got " + actualSha256 + "."
            );
        }
    }

    private static Path createRestartScript(Path updateFile, Path currentLauncherPath) throws IOException {
        Path script = Files.createTempFile("obsidiangate-launcher-update-", isWindows() ? ".cmd" : ".sh");
        String javaCommand = resolveJavaCommand();
        String content = isWindows()
            ? windowsScript(updateFile, currentLauncherPath, javaCommand)
            : unixScript(updateFile, currentLauncherPath, javaCommand);
        Files.write(script, content.getBytes(StandardCharsets.UTF_8));
        script.toFile().setExecutable(true, false);
        return script;
    }

    private static void startUpdaterScript(Path script) throws IOException {
        ProcessBuilder builder = isWindows()
            ? new ProcessBuilder("cmd.exe", "/c", "start", "", script.toString())
            : new ProcessBuilder("sh", script.toString());
        builder.start();
    }

    private static String windowsScript(Path updateFile, Path currentLauncherPath, String javaCommand) {
        return "@echo off\r\n"
            + "setlocal\r\n"
            + "set \"SRC=" + updateFile.toAbsolutePath() + "\"\r\n"
            + "set \"DST=" + currentLauncherPath.toAbsolutePath() + "\"\r\n"
            + "set \"JAVA=" + javaCommand + "\"\r\n"
            + "timeout /t 2 /nobreak > nul\r\n"
            + "copy /Y \"%SRC%\" \"%DST%\" > nul\r\n"
            + "if errorlevel 1 exit /b 1\r\n"
            + "start \"\" \"%JAVA%\" -jar \"%DST%\"\r\n"
            + "del \"%~f0\"\r\n";
    }

    private static String unixScript(Path updateFile, Path currentLauncherPath, String javaCommand) {
        return "#!/bin/sh\n"
            + "sleep 2\n"
            + "mv -f " + shellQuote(updateFile.toAbsolutePath().toString()) + " "
            + shellQuote(currentLauncherPath.toAbsolutePath().toString()) + "\n"
            + "chmod +x " + shellQuote(currentLauncherPath.toAbsolutePath().toString()) + "\n"
            + shellQuote(javaCommand) + " -jar " + shellQuote(currentLauncherPath.toAbsolutePath().toString())
            + " >/dev/null 2>&1 &\n"
            + "rm -- \"$0\"\n";
    }

    private boolean shouldOfferUpdate(LauncherUpdateSettings settings, String currentVersion) throws IOException {
        if (currentLauncherPath != null && Files.isRegularFile(currentLauncherPath)) {
            String currentSha256 = ChecksumUtils.sha256(currentLauncherPath);
            return !currentSha256.equalsIgnoreCase(settings.getSha256());
        }
        return isNewerVersion(settings.getVersion(), currentVersion);
    }

    static boolean isNewerVersion(String candidateVersion, String currentVersion) {
        String candidate = normalizeVersion(candidateVersion);
        String current = normalizeVersion(currentVersion);
        if (!hasText(candidate) || !hasText(current) || "dev".equalsIgnoreCase(current)) {
            return false;
        }
        if (candidate.equalsIgnoreCase(current)) {
            return false;
        }

        String[] candidateParts = candidate.split("[^A-Za-z0-9]+");
        String[] currentParts = current.split("[^A-Za-z0-9]+");
        int max = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < max; index++) {
            String left = index < candidateParts.length ? candidateParts[index] : "0";
            String right = index < currentParts.length ? currentParts[index] : "0";
            int comparison = compareVersionPart(left, right);
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return false;
    }

    private static int compareVersionPart(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            long leftValue = Long.parseLong(left);
            long rightValue = Long.parseLong(right);
            return Long.compare(leftValue, rightValue);
        }
        return left.compareToIgnoreCase(right);
    }

    private static String normalizeVersion(String value) {
        return value == null ? "" : value.trim().replace("-SNAPSHOT", "");
    }

    private static boolean isComplete(LauncherUpdateSettings settings) {
        return settings != null
            && hasText(settings.getVersion())
            && hasText(settings.getUrl())
            && hasText(settings.getSha256());
    }

    private static Path detectCurrentLauncherPath() {
        try {
            Path path = Paths.get(LauncherUpdateService.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return path;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
        }
        return null;
    }

    private static String resolveJavaCommand() {
        String executable = isWindows() ? "javaw.exe" : "java";
        Path javaPath = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (Files.isRegularFile(javaPath)) {
            return javaPath.toAbsolutePath().toString();
        }
        Path javaFallback = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(javaFallback)) {
            return javaFallback.toAbsolutePath().toString();
        }
        return isWindows() ? "javaw.exe" : "java";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void log(LogSink logSink, String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
