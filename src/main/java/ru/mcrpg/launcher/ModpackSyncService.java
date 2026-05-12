package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class ModpackSyncService {

    public interface LogSink {
        void log(String message);
    }

    private final ModpackManifestClient manifestClient;
    private final RuntimeSyncService runtimeSyncService;
    private final MinecraftBootstrapService minecraftBootstrapService;

    public ModpackSyncService(ModpackManifestClient manifestClient) {
        this(manifestClient, new RuntimeSyncService(), new MinecraftBootstrapService());
    }

    ModpackSyncService(
        ModpackManifestClient manifestClient,
        RuntimeSyncService runtimeSyncService,
        MinecraftBootstrapService minecraftBootstrapService
    ) {
        this.manifestClient = manifestClient;
        this.runtimeSyncService = runtimeSyncService;
        this.minecraftBootstrapService = minecraftBootstrapService;
    }

    public ModpackSyncPreviewResult preview(LauncherConfig baseConfig, LogSink logSink) throws IOException {
        PreparedSyncContext prepared = prepareSync(baseConfig, logSink, "Preview manifest");
        log(logSink, "Preview files in manifest: " + prepared.manifest.getFiles().size());

        int downloadFiles = 0;
        int reusedFiles = 0;
        long downloadBytes = 0L;
        List<ModpackSyncPreviewEntry> entries = new ArrayList<ModpackSyncPreviewEntry>();

        for (ModpackFile file : prepared.manifest.getFiles()) {
            FileInspection inspection = inspectFile(prepared.gameDirectory, file);
            entries.add(toPreviewEntry(file, inspection));
            if (inspection.isReused()) {
                reusedFiles++;
            } else {
                downloadFiles++;
                if (file.getSize() != null && file.getSize().longValue() > 0L) {
                    downloadBytes += file.getSize().longValue();
                }
            }
        }

        log(
            logSink,
            "Preview complete. Need sync: " + downloadFiles
                + ", up to date: " + reusedFiles
                + ", bytes to download: " + downloadBytes
        );

        return new ModpackSyncPreviewResult(
            prepared.resolvedConfig,
            prepared.manifest,
            entries,
            downloadFiles,
            reusedFiles,
            downloadBytes
        );
    }

    public ModpackSyncResult sync(LauncherConfig baseConfig, LogSink logSink) throws IOException {
        PreparedSyncContext prepared = prepareSync(baseConfig, logSink, "Loading manifest");
        LauncherConfig resolvedConfig = prepared.resolvedConfig;
        LoadedManifest loadedManifest = prepared.loadedManifest;
        ModpackManifest manifest = prepared.manifest;
        Path gameDirectory = prepared.gameDirectory;

        log(logSink, "Manifest version: " + valueOrFallback(manifest.getVersion(), "unknown"));
        log(logSink, "Files in manifest: " + manifest.getFiles().size());

        int downloadedFiles = 0;
        int reusedFiles = 0;
        long downloadedBytes = 0L;

        for (ModpackFile file : manifest.getFiles()) {
            FileSyncOutcome outcome = syncFile(gameDirectory, loadedManifest, manifest, file, logSink);
            if (outcome.isDownloaded()) {
                downloadedFiles++;
                downloadedBytes += outcome.getDownloadedBytes();
            } else {
                reusedFiles++;
            }
        }

        RuntimeResolution runtimeResolution = runtimeSyncService.sync(loadedManifest, manifest, gameDirectory, logSink);
        if (runtimeResolution != null) {
            resolvedConfig.setJavaCommand(runtimeResolution.getJavaExecutable().toString());
        }

        MinecraftBootstrapResult bootstrapResult = minecraftBootstrapService.bootstrap(
            manifest.getMinecraft(),
            gameDirectory,
            logSink
        );
        if (bootstrapResult != null) {
            if (hasText(bootstrapResult.getLaunchTemplate())) {
                resolvedConfig.setLaunchTemplate(bootstrapResult.getLaunchTemplate());
            }
            if (hasText(bootstrapResult.getWorkingDirectory())) {
                resolvedConfig.setWorkingDirectory(bootstrapResult.getWorkingDirectory());
            }
        }

        log(
            logSink,
            "Sync completed. Downloaded: " + downloadedFiles
                + ", reused: " + reusedFiles
                + ", bytes: " + downloadedBytes
        );

        return new ModpackSyncResult(resolvedConfig, manifest, downloadedFiles, reusedFiles, downloadedBytes);
    }

    private PreparedSyncContext prepareSync(LauncherConfig baseConfig, LogSink logSink, String manifestLogPrefix)
        throws IOException {
        LauncherConfig resolvedConfig = LauncherDefaults.applyMissingValues(baseConfig.copy());
        String manifestUrl = requireText(resolvedConfig.getManifestUrl(), "Укажи URL manifest.json.");
        Path gameDirectory = resolveGameDirectory(resolvedConfig.getGameDirectory());

        log(logSink, manifestLogPrefix + ": " + manifestUrl);
        LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
        ModpackManifest manifest = loadedManifest.getManifest();
        applyManifestSettings(resolvedConfig, manifest, gameDirectory);
        return new PreparedSyncContext(resolvedConfig, loadedManifest, manifest, gameDirectory);
    }

    private FileSyncOutcome syncFile(
        Path gameDirectory,
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        ModpackFile file,
        LogSink logSink
    ) throws IOException {
        FileInspection inspection = inspectFile(gameDirectory, file);
        Path target = inspection.getTarget();

        if (inspection.isReused()) {
            log(logSink, "Up to date: " + file.getPath());
            return FileSyncOutcome.reused();
        }

        URL downloadUrl = resolveDownloadUrl(loadedManifest, manifest, file);
        log(logSink, "Download: " + file.getPath() + " <- " + downloadUrl);

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".part");
        try {
            long downloadedBytes = download(downloadUrl, tempFile);
            verifyDownloadedFile(tempFile, file, inspection.getExpectedSha256());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

            if (file.isExecutable()) {
                target.toFile().setExecutable(true, false);
            }

            return FileSyncOutcome.downloaded(downloadedBytes);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static FileInspection inspectFile(Path gameDirectory, ModpackFile file) throws IOException {
        Path target = resolveTargetPath(gameDirectory, file.getPath());
        String expectedSha256 = requireText(file.getSha256(), "Для файла " + file.getPath() + " не указан sha256.");

        if (Files.exists(target) && !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Ожидался файл, но найден не файл: " + target);
        }

        if (!Files.isRegularFile(target)) {
            return FileInspection.download(target, expectedSha256, "missing");
        }

        String existingSha256 = ChecksumUtils.sha256(target);
        if (existingSha256.equalsIgnoreCase(expectedSha256)) {
            return FileInspection.reused(target, expectedSha256);
        }

        return FileInspection.download(target, expectedSha256, "sha256-mismatch");
    }

    private static ModpackSyncPreviewEntry toPreviewEntry(ModpackFile file, FileInspection inspection) {
        return new ModpackSyncPreviewEntry(
            file.getPath(),
            inspection.getTarget().toString(),
            inspection.getExpectedSha256(),
            file.getSize(),
            inspection.isReused() ? ModpackSyncPreviewEntry.State.REUSED : ModpackSyncPreviewEntry.State.DOWNLOAD,
            inspection.getReason()
        );
    }

    private static long download(URL downloadUrl, Path target) throws IOException {
        URLConnection connection = downloadUrl.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        long totalBytes = 0L;
        try (InputStream inputStream = connection.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                 target,
                 StandardOpenOption.TRUNCATE_EXISTING,
                 StandardOpenOption.WRITE
             )) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                totalBytes += read;
            }
        }
        return totalBytes;
    }

    private static void verifyDownloadedFile(Path path, ModpackFile file, String expectedSha256) throws IOException {
        if (file.getSize() != null && file.getSize().longValue() >= 0L) {
            long actualSize = Files.size(path);
            if (actualSize != file.getSize().longValue()) {
                throw new IOException(
                    "Размер файла " + file.getPath() + " не совпал. Ожидалось "
                        + file.getSize() + ", получено " + actualSize + "."
                );
            }
        }

        String actualSha256 = ChecksumUtils.sha256(path);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            throw new IOException(
                "SHA-256 файла " + file.getPath() + " не совпал. Ожидалось "
                    + expectedSha256 + ", получено " + actualSha256 + "."
            );
        }
    }

    private static URL resolveDownloadUrl(LoadedManifest loadedManifest, ModpackManifest manifest, ModpackFile file)
        throws IOException {
        String relativeUrl = hasText(file.getUrl()) ? file.getUrl().trim() : normalizeUrlPath(file.getPath());
        URL baseUrl = loadedManifest.getSourceUrl();
        if (hasText(manifest.getBaseUrl())) {
            baseUrl = new URL(loadedManifest.getSourceUrl(), manifest.getBaseUrl().trim());
        }
        return new URL(baseUrl, relativeUrl);
    }

    private static void applyManifestSettings(LauncherConfig config, ModpackManifest manifest, Path gameDirectory) {
        LauncherManifestSettings settings = manifest.getLauncher();
        if (settings == null) {
            return;
        }

        if (hasText(settings.getServerHost())) {
            config.setServerHost(settings.getServerHost().trim());
        }
        if (settings.getServerPort() != null) {
            config.setServerPort(settings.getServerPort().intValue());
        }
        if (hasText(settings.getLaunchTemplate())) {
            config.setLaunchTemplate(settings.getLaunchTemplate().trim());
        }
        if (hasText(settings.getWorkingDirectory())) {
            Path resolvedWorkingDirectory = resolveManifestWorkingDirectory(gameDirectory, settings.getWorkingDirectory());
            config.setWorkingDirectory(resolvedWorkingDirectory.toString());
        }
        if (hasText(settings.getAuthBaseUrl())) {
            config.setAuthBaseUrl(settings.getAuthBaseUrl().trim());
        }
        if (hasText(settings.getServerId())) {
            config.setServerId(settings.getServerId().trim());
        }
    }

    private static Path resolveGameDirectory(String rawGameDirectory) throws IOException {
        String gameDirectory = requireText(rawGameDirectory, "Укажи папку игры для синхронизации файлов.");
        Path path = Paths.get(gameDirectory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    private static Path resolveManifestWorkingDirectory(Path gameDirectory, String workingDirectory) {
        Path path = Paths.get(requireText(workingDirectory, "Working directory in manifest is empty."));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("workingDirectory в manifest должен быть относительным.");
        }

        Path resolved = gameDirectory.resolve(path).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("workingDirectory в manifest выходит за пределы папки игры.");
        }
        return resolved;
    }

    private static Path resolveTargetPath(Path gameDirectory, String relativePath) {
        String normalizedPath = requireText(relativePath, "В manifest найден файл без path.").replace('\\', '/');
        if (normalizedPath.startsWith("/")) {
            throw new IllegalArgumentException("Путь файла должен быть относительным: " + relativePath);
        }

        Path candidate = Paths.get(normalizedPath);
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("Путь файла должен быть относительным: " + relativePath);
        }

        Path resolved = gameDirectory.resolve(candidate).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("Путь файла выходит за пределы папки игры: " + relativePath);
        }
        return resolved;
    }

    private static void log(LogSink logSink, String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeUrlPath(String value) {
        return value.replace('\\', '/');
    }

    private static final class FileSyncOutcome {

        private final boolean downloaded;
        private final long downloadedBytes;

        private FileSyncOutcome(boolean downloaded, long downloadedBytes) {
            this.downloaded = downloaded;
            this.downloadedBytes = downloadedBytes;
        }

        static FileSyncOutcome reused() {
            return new FileSyncOutcome(false, 0L);
        }

        static FileSyncOutcome downloaded(long downloadedBytes) {
            return new FileSyncOutcome(true, downloadedBytes);
        }

        boolean isDownloaded() {
            return downloaded;
        }

        long getDownloadedBytes() {
            return downloadedBytes;
        }
    }

    private static final class FileInspection {

        private final Path target;
        private final String expectedSha256;
        private final boolean reused;
        private final String reason;

        private FileInspection(Path target, String expectedSha256, boolean reused, String reason) {
            this.target = target;
            this.expectedSha256 = expectedSha256;
            this.reused = reused;
            this.reason = reason;
        }

        static FileInspection reused(Path target, String expectedSha256) {
            return new FileInspection(target, expectedSha256, true, "up-to-date");
        }

        static FileInspection download(Path target, String expectedSha256, String reason) {
            return new FileInspection(target, expectedSha256, false, reason);
        }

        Path getTarget() {
            return target;
        }

        String getExpectedSha256() {
            return expectedSha256;
        }

        boolean isReused() {
            return reused;
        }

        String getReason() {
            return reason;
        }
    }

    private static final class PreparedSyncContext {

        private final LauncherConfig resolvedConfig;
        private final LoadedManifest loadedManifest;
        private final ModpackManifest manifest;
        private final Path gameDirectory;

        private PreparedSyncContext(
            LauncherConfig resolvedConfig,
            LoadedManifest loadedManifest,
            ModpackManifest manifest,
            Path gameDirectory
        ) {
            this.resolvedConfig = resolvedConfig;
            this.loadedManifest = loadedManifest;
            this.manifest = manifest;
            this.gameDirectory = gameDirectory;
        }
    }
}
