package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MinecraftBootstrapService {

    private static final String DEFAULT_VERSION_MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String DEFAULT_ASSET_BASE_URL = "https://resources.download.minecraft.net/";
    private static final Pattern MINECRAFT_ARGUMENT_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");
    private static final String NATIVES_MARKER_FILE = ".natives.properties";
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60000;

    private final ObjectMapper objectMapper;
    private final LaunchCommandBuilder commandTokenizer;

    public MinecraftBootstrapService() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        commandTokenizer = new LaunchCommandBuilder();
    }

    public MinecraftBootstrapResult bootstrap(
        MinecraftBootstrapSettings settings,
        Path gameDirectory,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (settings == null || !settings.isEnabled()) {
            return null;
        }

        String minecraftVersion = requireText(settings.getVersion(), "Minecraft version is missing in manifest.");
        String forgeVersion = requireText(settings.getForgeVersion(), "Forge version is missing in manifest.");
        String forgeVersionId = minecraftVersion + "-forge-" + forgeVersion;

        Path versionsDirectory = Files.createDirectories(gameDirectory.resolve("versions"));
        Path librariesDirectory = Files.createDirectories(gameDirectory.resolve("libraries"));
        Path assetsDirectory = Files.createDirectories(gameDirectory.resolve("assets"));
        Path cacheDirectory = Files.createDirectories(gameDirectory.resolve(".launcher-cache"));

        VersionMetadata baseVersion = loadBaseVersion(settings, minecraftVersion, logSink);
        writeJsonIfMissing(
            versionsDirectory.resolve(minecraftVersion).resolve(minecraftVersion + ".json"),
            baseVersion
        );

        ForgeInstallData forgeInstallData = ensureForgeVersionInstalled(
            settings,
            minecraftVersion,
            forgeVersion,
            forgeVersionId,
            versionsDirectory,
            librariesDirectory,
            cacheDirectory,
            logSink
        );

        Path clientJar = ensureFileDownloaded(
            gameDirectory.resolve("versions").resolve(minecraftVersion).resolve(minecraftVersion + ".jar"),
            baseVersion.downloads == null ? null : baseVersion.downloads.client,
            logSink,
            "Minecraft client"
        );

        List<Library> mergedLibraries = mergeLibraries(baseVersion, forgeInstallData.versionMetadata);
        PlatformInfo platform = PlatformInfo.current();
        List<NativeLibrary> nativeLibraries = new ArrayList<NativeLibrary>();
        LinkedHashMap<String, Path> classpathEntries = new LinkedHashMap<String, Path>();

        for (Library library : mergedLibraries) {
            if (!isAllowed(library, platform)) {
                continue;
            }

            if (library.downloads != null && library.downloads.artifact != null && hasText(library.downloads.artifact.path)) {
                Path artifactPath = librariesDirectory.resolve(toSystemPath(library.downloads.artifact.path)).normalize();
                Download artifactDownload = library.downloads.artifact;
                if (isForgeLibrary(library)) {
                    verifyExistingFile(artifactPath, artifactDownload);
                } else {
                    ensureFileDownloaded(artifactPath, artifactDownload, logSink, "Library " + library.name);
                }
                classpathEntries.put(artifactPath.toString(), artifactPath);
            }

            Download nativeDownload = resolveNativeDownload(library, platform);
            if (nativeDownload != null) {
                Path nativeArchive = librariesDirectory.resolve(toSystemPath(nativeDownload.path)).normalize();
                ensureFileDownloaded(nativeArchive, nativeDownload, logSink, "Native " + library.name);
                nativeLibraries.add(new NativeLibrary(nativeArchive, library.extract == null
                    ? Collections.<String>emptyList()
                    : library.extract.exclude));
            }
        }

        classpathEntries.put(clientJar.toString(), clientJar);

        Path assetIndexFile = ensureAssetIndex(assetsDirectory, baseVersion.assetIndex, logSink);
        AssetIndex assetIndex = readJson(assetIndexFile, AssetIndex.class);
        ensureAssets(settings, assetsDirectory, assetIndex, logSink);

        Path loggingConfigFile = ensureLoggingConfig(assetsDirectory, baseVersion.logging, logSink);
        Path nativesDirectory = ensureNatives(gameDirectory, forgeVersionId, platform, nativeLibraries, logSink);

        String launchTemplate = buildLaunchTemplate(
            forgeInstallData.versionMetadata,
            baseVersion,
            forgeVersionId,
            classpathEntries.values(),
            assetsDirectory,
            loggingConfigFile,
            nativesDirectory
        );

        log(logSink, "Official Minecraft bootstrap prepared: " + forgeVersionId);
        return new MinecraftBootstrapResult(launchTemplate, gameDirectory.toString());
    }

    private VersionMetadata loadBaseVersion(
        MinecraftBootstrapSettings settings,
        String minecraftVersion,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        String versionManifestUrl = hasText(settings.getVersionManifestUrl())
            ? settings.getVersionManifestUrl().trim()
            : DEFAULT_VERSION_MANIFEST_URL;
        log(logSink, "Loading Minecraft version manifest: " + versionManifestUrl);

        VersionManifest versionManifest = readJson(new URL(versionManifestUrl), VersionManifest.class);
        VersionReference versionReference = null;
        if (versionManifest.versions != null) {
            for (VersionReference candidate : versionManifest.versions) {
                if (minecraftVersion.equals(candidate.id)) {
                    versionReference = candidate;
                    break;
                }
            }
        }
        if (versionReference == null || !hasText(versionReference.url)) {
            throw new IllegalArgumentException("Minecraft version not found in official manifest: " + minecraftVersion);
        }
        return readJson(new URL(versionReference.url), VersionMetadata.class);
    }

    private ForgeInstallData ensureForgeVersionInstalled(
        MinecraftBootstrapSettings settings,
        String minecraftVersion,
        String forgeVersion,
        String forgeVersionId,
        Path versionsDirectory,
        Path librariesDirectory,
        Path cacheDirectory,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        Path versionDirectory = Files.createDirectories(versionsDirectory.resolve(forgeVersionId));
        Path versionJsonPath = versionDirectory.resolve(forgeVersionId + ".json");

        if (Files.isRegularFile(versionJsonPath)) {
            VersionMetadata existingVersion = readJson(versionJsonPath, VersionMetadata.class);
            Download forgeArtifact = findForgeArtifact(existingVersion);
            Path forgeArtifactPath = forgeArtifact == null || !hasText(forgeArtifact.path)
                ? null
                : librariesDirectory.resolve(toSystemPath(forgeArtifact.path)).normalize();
            if (forgeArtifactPath != null && Files.isRegularFile(forgeArtifactPath)
                && matchesExistingFile(forgeArtifactPath, forgeArtifact == null ? null : forgeArtifact.sha1,
                    forgeArtifact == null ? null : forgeArtifact.size)) {
                return new ForgeInstallData(existingVersion, versionJsonPath, forgeArtifactPath);
            }
        }

        String installerUrl = hasText(settings.getForgeInstallerUrl())
            ? settings.getForgeInstallerUrl().trim()
            : defaultForgeInstallerUrl(minecraftVersion, forgeVersion);
        Path installerDirectory = Files.createDirectories(cacheDirectory.resolve("forge"));
        Path installerFile = installerDirectory.resolve("forge-" + forgeVersionId + "-installer.jar");
        downloadIfNeeded(new URL(installerUrl), installerFile, null, null, logSink, "Forge installer");

        return extractForgeInstaller(installerFile, versionJsonPath, librariesDirectory, forgeVersionId, logSink);
    }

    private ForgeInstallData extractForgeInstaller(
        Path installerFile,
        Path versionJsonPath,
        Path librariesDirectory,
        String forgeVersionId,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        byte[] versionBytes;
        try (ZipFile zipFile = new ZipFile(installerFile.toFile())) {
            versionBytes = readRequiredEntry(zipFile, "version.json");
            VersionMetadata versionMetadata = readJson(new ByteArrayInputStream(versionBytes), VersionMetadata.class);
            if (!forgeVersionId.equals(versionMetadata.id)) {
                throw new IOException("Unexpected Forge version id in installer: " + versionMetadata.id);
            }

            Download forgeArtifact = findForgeArtifact(versionMetadata);
            if (forgeArtifact == null || !hasText(forgeArtifact.path)) {
                throw new IOException("Forge installer does not describe runtime forge artifact.");
            }

            String installerArtifactEntry = "maven/" + forgeArtifact.path.replace('\\', '/');
            byte[] forgeArtifactBytes = readRequiredEntry(zipFile, installerArtifactEntry);
            Path forgeArtifactPath = librariesDirectory.resolve(toSystemPath(forgeArtifact.path)).normalize();
            writeBytes(versionJsonPath, versionBytes);
            writeBytes(forgeArtifactPath, forgeArtifactBytes);
            verifyExistingFile(forgeArtifactPath, forgeArtifact);
            log(logSink, "Forge metadata installed: " + forgeVersionId);
            return new ForgeInstallData(versionMetadata, versionJsonPath, forgeArtifactPath);
        }
    }

    private Path ensureFileDownloaded(
        Path target,
        Download download,
        ModpackSyncService.LogSink logSink,
        String label
    ) throws IOException {
        if (download == null) {
            throw new IllegalArgumentException(label + " download metadata is missing.");
        }
        String rawUrl = requireText(download.url, label + " URL is missing.");
        downloadIfNeeded(new URL(rawUrl), target, download.sha1, download.size, logSink, label);
        return target;
    }

    private static void verifyExistingFile(Path target, Download download) throws IOException {
        if (download != null) {
            verifyExistingFile(target, download.sha1, download.size);
        }
    }

    private static void verifyExistingFile(Path target, String expectedSha1, Long expectedSize) throws IOException {
        if (!Files.isRegularFile(target)) {
            throw new IOException("Missing required file: " + target);
        }
        if (expectedSize != null && expectedSize.longValue() >= 0L) {
            long actualSize = Files.size(target);
            if (actualSize != expectedSize.longValue()) {
                throw new IOException("Size mismatch for " + target + ". Expected " + expectedSize + ", got " + actualSize + ".");
            }
        }
        if (hasText(expectedSha1)) {
            String actualSha1 = ChecksumUtils.sha1(target);
            if (!actualSha1.equalsIgnoreCase(expectedSha1.trim())) {
                throw new IOException(
                    "SHA-1 mismatch for " + target + ". Expected " + expectedSha1 + ", got " + actualSha1 + "."
                );
            }
        }
    }

    private static void downloadIfNeeded(
        URL url,
        Path target,
        String expectedSha1,
        Long expectedSize,
        ModpackSyncService.LogSink logSink,
        String label
    ) throws IOException {
        if (Files.isRegularFile(target)) {
            if (matchesExistingFile(target, expectedSha1, expectedSize)) {
                log(logSink, "Reused: " + target.getFileName());
                return;
            }
            Files.deleteIfExists(target);
        }

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent == null ? target.toAbsolutePath().getParent() : parent, "download-", ".part");
        try {
            log(logSink, "Downloading " + label + ": " + url);
            download(url, tempFile);
            verifyExistingFile(tempFile, expectedSha1, expectedSize);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private Path ensureAssetIndex(
        Path assetsDirectory,
        AssetIndexReference assetIndex,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (assetIndex == null) {
            throw new IllegalArgumentException("Minecraft asset index metadata is missing.");
        }
        String assetIndexId = requireText(assetIndex.id, "Minecraft asset index id is missing.");
        String assetIndexUrl = requireText(assetIndex.url, "Minecraft asset index URL is missing.");
        Path target = assetsDirectory.resolve("indexes").resolve(assetIndexId + ".json");
        downloadIfNeeded(new URL(assetIndexUrl), target, assetIndex.sha1, assetIndex.size, logSink, "Asset index " + assetIndexId);
        return target;
    }

    private void ensureAssets(
        MinecraftBootstrapSettings settings,
        Path assetsDirectory,
        AssetIndex assetIndex,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (assetIndex == null || assetIndex.objects == null || assetIndex.objects.isEmpty()) {
            return;
        }

        String baseUrl = hasText(settings.getAssetBaseUrl()) ? settings.getAssetBaseUrl().trim() : DEFAULT_ASSET_BASE_URL;
        URL assetsBaseUrl = new URL(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        log(logSink, "Asset objects: " + assetIndex.objects.size());

        for (Map.Entry<String, AssetObject> entry : assetIndex.objects.entrySet()) {
            AssetObject assetObject = entry.getValue();
            String hash = requireText(assetObject.hash, "Asset hash is missing for " + entry.getKey() + ".");
            String relativePath = hash.substring(0, 2) + "/" + hash;
            Path target = assetsDirectory.resolve("objects").resolve(toSystemPath(relativePath));
            downloadIfNeeded(
                new URL(assetsBaseUrl, relativePath),
                target,
                hash,
                assetObject.size,
                logSink,
                "Asset " + entry.getKey()
            );
        }
    }

    private Path ensureLoggingConfig(
        Path assetsDirectory,
        Logging logging,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (logging == null || logging.client == null || logging.client.file == null || !hasText(logging.client.file.id)) {
            return null;
        }

        Download file = logging.client.file;
        Path target = assetsDirectory.resolve("log_configs").resolve(file.id);
        downloadIfNeeded(new URL(requireText(file.url, "Logging config URL is missing.")), target, file.sha1, file.size, logSink, "Logging config");
        return target;
    }

    private Path ensureNatives(
        Path gameDirectory,
        String forgeVersionId,
        PlatformInfo platform,
        List<NativeLibrary> nativeLibraries,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        if (nativeLibraries.isEmpty()) {
            return null;
        }

        String directoryName = sanitizeDirectoryName(forgeVersionId + "-" + platform.getOs() + "-" + platform.getArch());
        Path nativesDirectory = gameDirectory.resolve("natives").resolve(directoryName).normalize();
        Path markerFile = nativesDirectory.resolve(NATIVES_MARKER_FILE);
        String fingerprint = buildNativeFingerprint(nativeLibraries);

        if (Files.isRegularFile(markerFile) && Files.isDirectory(nativesDirectory)) {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(markerFile)) {
                properties.load(inputStream);
            }
            if (fingerprint.equals(properties.getProperty("fingerprint", ""))) {
                log(logSink, "Reused native libraries: " + nativesDirectory);
                return nativesDirectory;
            }
        }

        deleteRecursively(nativesDirectory);
        Files.createDirectories(nativesDirectory);
        for (NativeLibrary nativeLibrary : nativeLibraries) {
            extractNativeArchive(nativeLibrary, nativesDirectory);
        }

        Properties properties = new Properties();
        properties.setProperty("fingerprint", fingerprint);
        try (OutputStream outputStream = Files.newOutputStream(
            markerFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            properties.store(outputStream, "Extracted native libraries");
        }
        log(logSink, "Native libraries extracted: " + nativesDirectory);
        return nativesDirectory;
    }

    private String buildLaunchTemplate(
        VersionMetadata forgeVersion,
        VersionMetadata baseVersion,
        String forgeVersionId,
        Iterable<Path> classpathEntries,
        Path assetsDirectory,
        Path loggingConfigFile,
        Path nativesDirectory
    ) {
        String mainClass = requireText(forgeVersion.mainClass, "Forge mainClass is missing.");
        String minecraftArguments = requireText(
            forgeVersion.minecraftArguments,
            "Forge minecraftArguments are missing. Only legacy 1.12-style launch metadata is supported."
        );

        List<String> tokens = new ArrayList<String>();
        tokens.add("{java}");

        if (nativesDirectory != null) {
            tokens.add("-Djava.library.path=" + toTemplatePath(nativesDirectory));
        }
        if (loggingConfigFile != null && baseVersion.logging != null && baseVersion.logging.client != null
            && hasText(baseVersion.logging.client.argument)) {
            tokens.add(baseVersion.logging.client.argument.replace("${path}", toTemplatePath(loggingConfigFile)));
        }

        tokens.add("-cp");
        tokens.add(joinPaths(classpathEntries));
        tokens.add(mainClass);

        Map<String, String> replacements = new LinkedHashMap<String, String>();
        replacements.put("auth_player_name", "{username}");
        replacements.put("version_name", forgeVersionId);
        replacements.put("game_directory", "{gameDir}");
        replacements.put("assets_root", toTemplatePath(assetsDirectory));
        replacements.put("assets_index_name", requireText(baseVersion.assetIndex.id, "Asset index id is missing."));
        replacements.put("auth_uuid", "{uuid}");
        replacements.put("auth_access_token", "{accessToken}");
        replacements.put("user_type", "{userType}");
        replacements.put("version_type", "Forge");
        replacements.put("user_properties", "{}");

        for (String token : commandTokenizer.tokenize(minecraftArguments)) {
            tokens.add(replaceMinecraftArguments(token, replacements));
        }

        tokens.add("--server");
        tokens.add("{serverHost}");
        tokens.add("--port");
        tokens.add("{serverPort}");

        return toTemplate(tokens);
    }

    private static String replaceMinecraftArguments(String token, Map<String, String> replacements) {
        Matcher matcher = MINECRAFT_ARGUMENT_PATTERN.matcher(token);
        StringBuffer builder = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = replacements.get(name);
            if (replacement == null) {
                throw new IllegalArgumentException("Unsupported Minecraft launch placeholder: ${" + name + "}");
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String toTemplate(List<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(quoteIfNeeded(token));
        }
        return builder.toString();
    }

    private static String quoteIfNeeded(String token) {
        if (token == null) {
            return "";
        }
        if (token.indexOf(' ') < 0 && token.indexOf('\t') < 0 && token.indexOf('"') < 0) {
            return token;
        }
        return '"' + token.replace("\"", "\\\"") + '"';
    }

    private static String joinPaths(Iterable<Path> paths) {
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            if (builder.length() > 0) {
                builder.append(File.pathSeparatorChar);
            }
            builder.append(toTemplatePath(path));
        }
        return builder.toString();
    }

    private static String toTemplatePath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String buildNativeFingerprint(List<NativeLibrary> nativeLibraries) {
        StringBuilder builder = new StringBuilder();
        for (NativeLibrary nativeLibrary : nativeLibraries) {
            builder.append(nativeLibrary.archive.toAbsolutePath().normalize()).append('\n');
            for (String exclude : nativeLibrary.exclude) {
                builder.append(exclude).append('\n');
            }
        }
        return builder.toString();
    }

    private static void extractNativeArchive(NativeLibrary nativeLibrary, Path nativesDirectory) throws IOException {
        try (ZipFile zipFile = new ZipFile(nativeLibrary.archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String normalizedName = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || shouldExclude(normalizedName, nativeLibrary.exclude)) {
                    continue;
                }

                Path target = resolveInside(nativesDirectory, normalizedName);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream inputStream = zipFile.getInputStream(entry);
                     OutputStream outputStream = Files.newOutputStream(
                         target,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE
                     )) {
                    copy(inputStream, outputStream);
                }
            }
        }
    }

    private static boolean matchesExistingFile(Path target, String expectedSha1, Long expectedSize) {
        try {
            verifyExistingFile(target, expectedSha1, expectedSize);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean shouldExclude(String entryName, List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String normalized = entryName.replace('\\', '/');
        for (String exclude : excludes) {
            String normalizedExclude = exclude == null ? "" : exclude.replace('\\', '/');
            if (!normalizedExclude.isEmpty() && normalized.startsWith(normalizedExclude)) {
                return true;
            }
        }
        return false;
    }

    private static Download resolveNativeDownload(Library library, PlatformInfo platform) {
        if (library == null || library.downloads == null || library.downloads.classifiers == null
            || library.natives == null || library.natives.isEmpty()) {
            return null;
        }

        String mojangOs = toMojangOsName(platform);
        String classifier = library.natives.get(mojangOs);
        if (!hasText(classifier)) {
            return null;
        }
        classifier = classifier.replace("${arch}", toNativeArch(platform));
        return library.downloads.classifiers.get(classifier);
    }

    private static boolean isAllowed(Library library, PlatformInfo platform) {
        if (library == null || library.rules == null || library.rules.isEmpty()) {
            return true;
        }

        boolean allowed = false;
        for (Rule rule : library.rules) {
            if (applies(rule, platform)) {
                allowed = "allow".equalsIgnoreCase(rule.action);
            }
        }
        return allowed;
    }

    private static boolean applies(Rule rule, PlatformInfo platform) {
        if (rule == null) {
            return false;
        }
        if (rule.os == null) {
            return true;
        }

        String mojangOs = toMojangOsName(platform);
        if (hasText(rule.os.name) && !rule.os.name.trim().equalsIgnoreCase(mojangOs)) {
            return false;
        }
        if (hasText(rule.os.arch) && !rule.os.arch.trim().equalsIgnoreCase(platform.getArch())) {
            return false;
        }
        return true;
    }

    private static List<Library> mergeLibraries(VersionMetadata baseVersion, VersionMetadata forgeVersion) {
        List<Library> merged = new ArrayList<Library>();
        if (baseVersion.libraries != null) {
            merged.addAll(baseVersion.libraries);
        }
        if (forgeVersion.libraries != null) {
            merged.addAll(forgeVersion.libraries);
        }
        return merged;
    }

    private static Download findForgeArtifact(VersionMetadata versionMetadata) {
        if (versionMetadata == null || versionMetadata.libraries == null) {
            return null;
        }
        for (Library library : versionMetadata.libraries) {
            if (isForgeLibrary(library) && library.downloads != null) {
                return library.downloads.artifact;
            }
        }
        return null;
    }

    private static boolean isForgeLibrary(Library library) {
        return library != null && hasText(library.name) && library.name.startsWith("net.minecraftforge:forge:");
    }

    private static String defaultForgeInstallerUrl(String minecraftVersion, String forgeVersion) {
        String combined = minecraftVersion + "-" + forgeVersion;
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/" + combined
            + "/forge-" + combined + "-installer.jar";
    }

    private static String toSystemPath(String value) {
        return value.replace('/', File.separatorChar).replace('\\', File.separatorChar);
    }

    private static String sanitizeDirectoryName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String toMojangOsName(PlatformInfo platform) {
        if ("macos".equals(platform.getOs())) {
            return "osx";
        }
        return platform.getOs();
    }

    private static String toNativeArch(PlatformInfo platform) {
        return "x86".equals(platform.getArch()) ? "32" : "64";
    }

    private <T> T readJson(URL url, Class<T> type) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        try (InputStream inputStream = connection.getInputStream()) {
            return readJson(inputStream, type);
        }
    }

    private <T> T readJson(Path path, Class<T> type) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return readJson(inputStream, type);
        }
    }

    private <T> T readJson(InputStream inputStream, Class<T> type) throws IOException {
        return objectMapper.readValue(inputStream, type);
    }

    private void writeJsonIfMissing(Path target, Object value) throws IOException {
        if (Files.isRegularFile(target)) {
            return;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), value);
    }

    private static void writeBytes(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            outputStream.write(bytes);
        }
    }

    private static byte[] readRequiredEntry(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new IOException("Missing entry in archive: " + entryName);
        }

        try (InputStream inputStream = zipFile.getInputStream(entry);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            copy(inputStream, outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void download(URL url, Path target) throws IOException {
        DownloadUtils.download(url, target, DOWNLOAD_READ_TIMEOUT_MS);
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        DownloadUtils.copy(inputStream, outputStream);
    }

    private static Path resolveInside(Path root, String entryName) {
        Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Archive entry escapes target directory: " + entryName);
        }
        return resolved;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (java.util.stream.Stream<Path> stream = Files.list(path)) {
                Path[] children = stream.toArray(Path[]::new);
                for (Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static void log(ModpackSyncService.LogSink logSink, String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static final class ForgeInstallData {
        private final VersionMetadata versionMetadata;
        private final Path versionJsonPath;
        private final Path forgeArtifactPath;

        private ForgeInstallData(VersionMetadata versionMetadata, Path versionJsonPath, Path forgeArtifactPath) {
            this.versionMetadata = versionMetadata;
            this.versionJsonPath = versionJsonPath;
            this.forgeArtifactPath = forgeArtifactPath;
        }
    }

    private static final class NativeLibrary {
        private final Path archive;
        private final List<String> exclude;

        private NativeLibrary(Path archive, List<String> exclude) {
            this.archive = archive;
            this.exclude = exclude == null ? Collections.<String>emptyList() : new ArrayList<String>(exclude);
        }
    }

    private static final class VersionManifest {
        public List<VersionReference> versions = new ArrayList<VersionReference>();
    }

    private static final class VersionReference {
        public String id;
        public String url;
    }

    private static final class VersionMetadata {
        public String id;
        public String inheritsFrom;
        public String mainClass;
        public String minecraftArguments;
        public String type;
        public AssetIndexReference assetIndex = new AssetIndexReference();
        public VersionDownloads downloads = new VersionDownloads();
        public List<Library> libraries = new ArrayList<Library>();
        public Logging logging = new Logging();
    }

    private static final class VersionDownloads {
        public Download client;
    }

    private static final class AssetIndexReference {
        public String id;
        public String sha1;
        public Long size;
        public String url;
    }

    private static final class AssetIndex {
        public Map<String, AssetObject> objects = new LinkedHashMap<String, AssetObject>();
    }

    private static final class AssetObject {
        public String hash;
        public Long size;
    }

    private static final class Library {
        public String name;
        public LibraryDownloads downloads = new LibraryDownloads();
        public Map<String, String> natives = new LinkedHashMap<String, String>();
        public Extract extract = new Extract();
        public List<Rule> rules = new ArrayList<Rule>();
    }

    private static final class LibraryDownloads {
        public Download artifact;
        public Map<String, Download> classifiers = new LinkedHashMap<String, Download>();
    }

    private static final class Download {
        public String id;
        public String path;
        public String url;
        public String sha1;
        public Long size;
    }

    private static final class Extract {
        public List<String> exclude = new ArrayList<String>();
    }

    private static final class Rule {
        public String action;
        public RuleOs os = new RuleOs();
    }

    private static final class RuleOs {
        public String name;
        public String arch;
    }

    private static final class Logging {
        public LoggingClient client = new LoggingClient();
    }

    private static final class LoggingClient {
        public String argument;
        public Download file;
    }
}
