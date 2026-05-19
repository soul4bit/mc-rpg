package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

public final class ModpackSyncService {

    private static final int FILE_DOWNLOAD_READ_TIMEOUT_MS = 30000;

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
        PreparedSyncContext prepared = prepareSync(baseConfig, logSink, "Предпросмотр manifest");
        log(logSink, "Файлов в manifest для предпросмотра: " + prepared.manifest.getFiles().size());

        int downloadFiles = 0;
        int reusedFiles = 0;
        long downloadBytes = 0L;
        List<ModpackSyncPreviewEntry> entries = new ArrayList<ModpackSyncPreviewEntry>(prepared.manifest.getFiles().size());
        VerifiedFileCache verifiedFileCache = VerifiedFileCache.open(prepared.gameDirectory);

        try {
            List<FileInspection> inspections = inspectFiles(
                prepared.gameDirectory,
                prepared.manifest.getFiles(),
                verifiedFileCache
            );

            for (int index = 0; index < prepared.manifest.getFiles().size(); index++) {
                ModpackFile file = prepared.manifest.getFiles().get(index);
                FileInspection inspection = inspections.get(index);
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
        } finally {
            saveCache(verifiedFileCache, logSink);
        }

        log(
            logSink,
            "Предпросмотр завершен. Нужно синхронизировать: " + downloadFiles
                + ", актуальны: " + reusedFiles
                + ", байт к скачиванию: " + downloadBytes
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
        PreparedSyncContext prepared = prepareSync(baseConfig, logSink, "Загружаем manifest");
        LauncherConfig resolvedConfig = prepared.resolvedConfig;
        LoadedManifest loadedManifest = prepared.loadedManifest;
        ModpackManifest manifest = prepared.manifest;
        Path gameDirectory = prepared.gameDirectory;

        log(logSink, "Версия manifest: " + valueOrFallback(manifest.getVersion(), "неизвестно"));
        log(logSink, "Файлов в manifest: " + manifest.getFiles().size());

        int downloadedFiles = 0;
        int reusedFiles = 0;
        long downloadedBytes = 0L;
        VerifiedFileCache verifiedFileCache = VerifiedFileCache.open(gameDirectory);

        try {
            List<FileSyncOutcome> outcomes = syncFiles(
                gameDirectory,
                loadedManifest,
                manifest,
                verifiedFileCache,
                logSink
            );

            for (FileSyncOutcome outcome : outcomes) {
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
                logSink,
                verifiedFileCache
            );
            if (bootstrapResult != null) {
                if (hasText(bootstrapResult.getLaunchTemplate())) {
                    resolvedConfig.setLaunchTemplate(bootstrapResult.getLaunchTemplate());
                }
                if (hasText(bootstrapResult.getWorkingDirectory())) {
                    resolvedConfig.setWorkingDirectory(bootstrapResult.getWorkingDirectory());
                }
            }
        } finally {
            saveCache(verifiedFileCache, logSink);
        }

        int removedFiles = cleanupObsoleteModEntries(gameDirectory, manifest, logSink);
        if (removedFiles > 0) {
            log(logSink, "Устаревших модов убрано: " + removedFiles);
        }

        log(
            logSink,
            "Синхронизация завершена. Скачано: " + downloadedFiles
                + ", переиспользовано: " + reusedFiles
                + ", байт: " + downloadedBytes
        );

        return new ModpackSyncResult(resolvedConfig, manifest, downloadedFiles, reusedFiles, removedFiles, downloadedBytes);
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

    private static List<FileInspection> inspectFiles(
        Path gameDirectory,
        List<ModpackFile> files,
        VerifiedFileCache verifiedFileCache
    ) throws IOException {
        List<Callable<FileInspection>> tasks = new ArrayList<Callable<FileInspection>>(files.size());
        for (ModpackFile file : files) {
            tasks.add(() -> inspectFile(gameDirectory, file, verifiedFileCache));
        }
        return ParallelIo.run("modpack-preview", tasks);
    }

    private List<FileSyncOutcome> syncFiles(
        Path gameDirectory,
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        VerifiedFileCache verifiedFileCache,
        LogSink logSink
    ) throws IOException {
        List<Callable<FileSyncOutcome>> tasks = new ArrayList<Callable<FileSyncOutcome>>(manifest.getFiles().size());
        for (ModpackFile file : manifest.getFiles()) {
            tasks.add(() -> syncFile(gameDirectory, loadedManifest, manifest, file, verifiedFileCache, logSink));
        }
        return ParallelIo.run("modpack-sync", tasks);
    }

    private FileSyncOutcome syncFile(
        Path gameDirectory,
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        ModpackFile file,
        VerifiedFileCache verifiedFileCache,
        LogSink logSink
    ) throws IOException {
        FileInspection inspection = inspectFile(gameDirectory, file, verifiedFileCache);
        Path target = inspection.getTarget();

        if (inspection.isReused()) {
            log(logSink, "Актуально: " + file.getPath());
            return FileSyncOutcome.reused();
        }

        URL downloadUrl = resolveDownloadUrl(loadedManifest, manifest, file);
        log(logSink, "Скачиваем: " + file.getPath() + " <- " + downloadUrl);

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".part");
        try {
            long downloadedBytes = download(downloadUrl, tempFile);
            verifyDownloadedFile(tempFile, file, inspection.getExpectedSha256());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            verifiedFileCache.recordVerified(target, "SHA-256", inspection.getExpectedSha256());

            if (file.isExecutable()) {
                target.toFile().setExecutable(true, false);
            }

            return FileSyncOutcome.downloaded(downloadedBytes);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static FileInspection inspectFile(
        Path gameDirectory,
        ModpackFile file,
        VerifiedFileCache verifiedFileCache
    ) throws IOException {
        Path target = resolveTargetPath(gameDirectory, file.getPath());
        String expectedSha256 = requireText(file.getSha256(), "Для файла " + file.getPath() + " не указан sha256.");

        if (Files.exists(target) && !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Ожидался файл, но найден не файл: " + target);
        }

        if (!Files.isRegularFile(target)) {
            verifiedFileCache.remove(target);
            return FileInspection.download(target, expectedSha256, "missing");
        }

        if (file.getSize() != null && file.getSize().longValue() >= 0L) {
            long existingSize = Files.size(target);
            if (existingSize != file.getSize().longValue()) {
                verifiedFileCache.remove(target);
                return FileInspection.download(target, expectedSha256, "size-mismatch");
            }
        }

        if (verifiedFileCache.matches(target, "SHA-256", expectedSha256, file.getSize())) {
            return FileInspection.reused(target, expectedSha256);
        }

        String existingSha256 = ChecksumUtils.sha256(target);
        if (existingSha256.equalsIgnoreCase(expectedSha256)) {
            verifiedFileCache.recordVerified(target, "SHA-256", expectedSha256);
            return FileInspection.reused(target, expectedSha256);
        }

        verifiedFileCache.remove(target);
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
        return DownloadUtils.download(downloadUrl, target, FILE_DOWNLOAD_READ_TIMEOUT_MS);
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
        return DownloadUrlResolver.resolve(loadedManifest.getSourceUrl(), manifest.getBaseUrl(), relativeUrl);
    }

    private static int cleanupObsoleteModEntries(Path gameDirectory, ModpackManifest manifest, LogSink logSink)
        throws IOException {
        Path modsDirectory = gameDirectory.resolve("mods").normalize();
        if (!modsDirectory.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("mods directory is outside game directory: " + modsDirectory);
        }
        if (!Files.isDirectory(modsDirectory)) {
            return 0;
        }

        Set<String> expectedEntries = collectExpectedManagedEntries(manifest, "mods");
        int removedFiles = 0;
        Path backupDirectory = null;

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(modsDirectory)) {
            for (Path entry : entries) {
                Path fileName = entry.getFileName();
                if (fileName == null || expectedEntries.contains(fileName.toString())) {
                    continue;
                }

                if (backupDirectory == null) {
                    backupDirectory = createObsoleteBackupDirectory(gameDirectory, manifest);
                }

                Path destination = uniqueDestination(backupDirectory, fileName.toString());
                Files.move(entry, destination);
                removedFiles++;
                log(
                    logSink,
                    "Убран устаревший файл сборки: mods/" + fileName + " -> "
                        + gameDirectory.relativize(destination).toString().replace('\\', '/')
                );
            }
        }

        return removedFiles;
    }

    private static Set<String> collectExpectedManagedEntries(ModpackManifest manifest, String rootName) {
        Set<String> expectedEntries = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        String prefix = rootName + "/";

        for (ModpackFile file : manifest.getFiles()) {
            if (!hasText(file.getPath())) {
                continue;
            }

            String normalizedPath = normalizeUrlPath(file.getPath());
            if (!normalizedPath.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }

            String remainder = normalizedPath.substring(prefix.length());
            int slashIndex = remainder.indexOf('/');
            String topLevelEntry = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
            if (hasText(topLevelEntry)) {
                expectedEntries.add(topLevelEntry);
            }
        }

        return expectedEntries;
    }

    private static Path createObsoleteBackupDirectory(Path gameDirectory, ModpackManifest manifest) throws IOException {
        String manifestVersion = manifest == null ? "unknown" : valueOrFallback(manifest.getVersion(), "unknown");
        String directoryName = sanitizePathSegment(manifestVersion) + "-" + System.currentTimeMillis();
        Path backupDirectory = gameDirectory.resolve(".obsolete-mods").resolve(directoryName).normalize();
        if (!backupDirectory.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("obsolete mods backup is outside game directory: " + backupDirectory);
        }
        Files.createDirectories(backupDirectory);
        return backupDirectory;
    }

    private static Path uniqueDestination(Path backupDirectory, String fileName) {
        Path destination = backupDirectory.resolve(fileName);
        if (!Files.exists(destination)) {
            return destination;
        }

        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int counter = 1;
        while (true) {
            Path candidate = backupDirectory.resolve(baseName + "-" + counter + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private static String sanitizePathSegment(String value) {
        String sanitized = valueOrFallback(value, "unknown").replaceAll("[^A-Za-z0-9._-]", "_");
        return hasText(sanitized) ? sanitized : "unknown";
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
        Path path = Paths.get(requireText(workingDirectory, "workingDirectory в manifest пустой."));
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
            synchronized (logSink) {
                logSink.log(message);
            }
        }
    }

    private static void saveCache(VerifiedFileCache verifiedFileCache, LogSink logSink) {
        try {
            verifiedFileCache.save();
        } catch (IOException exception) {
            log(logSink, "Кеш проверенных файлов не сохранен: " + exception.getMessage());
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
