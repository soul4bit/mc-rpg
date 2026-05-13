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
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RuntimeSyncService {

    private static final String RUNTIME_MARKER_FILE = ".runtime-package.properties";

    public RuntimeResolution sync(
        LoadedManifest loadedManifest,
        ModpackManifest manifest,
        Path gameDirectory,
        ModpackSyncService.LogSink logSink
    ) throws IOException {
        RuntimePackage runtimePackage = resolvePackage(manifest.getRuntime(), PlatformInfo.current());
        if (runtimePackage == null) {
            return null;
        }

        Path installDirectory = resolveInstallDirectory(gameDirectory, runtimePackage);
        Path javaExecutable = resolveJavaExecutable(installDirectory, runtimePackage);
        if (isInstalled(installDirectory, javaExecutable, runtimePackage)) {
            log(logSink, "Portable Java already installed: " + javaExecutable);
            return new RuntimeResolution(runtimePackage, javaExecutable);
        }

        URL downloadUrl = resolveDownloadUrl(loadedManifest, manifest, runtimePackage.getUrl());
        log(logSink, "Downloading runtime: " + downloadUrl);

        Path parent = installDirectory.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path archiveFile = Files.createTempFile(gameDirectory, "runtime-", ".zip");
        Path tempInstallDirectory = Files.createTempDirectory(gameDirectory, "runtime-install-");
        try {
            download(downloadUrl, archiveFile);
            verifyArchive(runtimePackage, archiveFile);
            unzip(archiveFile, tempInstallDirectory);

            Path extractedJava = resolveJavaExecutable(tempInstallDirectory, runtimePackage);
            if (!Files.isRegularFile(extractedJava)) {
                throw new IOException("Runtime archive does not contain java executable: " + runtimePackage.getJavaPath());
            }

            deleteRecursively(installDirectory);
            Files.move(tempInstallDirectory, installDirectory, StandardCopyOption.REPLACE_EXISTING);

            javaExecutable = resolveJavaExecutable(installDirectory, runtimePackage);
            javaExecutable.toFile().setExecutable(true, false);
            writeMarker(installDirectory, runtimePackage);
            log(logSink, "Portable Java installed: " + javaExecutable);
            return new RuntimeResolution(runtimePackage, javaExecutable);
        } finally {
            Files.deleteIfExists(archiveFile);
            deleteRecursively(tempInstallDirectory);
        }
    }

    private static RuntimePackage resolvePackage(ModpackRuntime runtime, PlatformInfo platform) {
        if (runtime == null) {
            return null;
        }

        List<RuntimePackage> packages = runtime.getPackages();
        if (packages == null || packages.isEmpty()) {
            return null;
        }

        for (RuntimePackage runtimePackage : packages) {
            if (matches(runtimePackage, platform)) {
                validatePackage(runtimePackage);
                return runtimePackage;
            }
        }

        throw new IllegalArgumentException(
            "No runtime package for platform " + platform.getOs() + "/" + platform.getArch() + "."
        );
    }

    private static boolean matches(RuntimePackage runtimePackage, PlatformInfo platform) {
        return matchesValue(runtimePackage.getOs(), platform.getOs())
            && matchesValue(runtimePackage.getArch(), platform.getArch());
    }

    private static boolean matchesValue(String expected, String actual) {
        return expected == null || expected.trim().isEmpty() || expected.trim().equalsIgnoreCase(actual);
    }

    private static void validatePackage(RuntimePackage runtimePackage) {
        requireText(runtimePackage.getUrl(), "Runtime package url is missing.");
        requireText(runtimePackage.getSha256(), "Runtime package sha256 is missing.");
        requireText(runtimePackage.getExtractDir(), "Runtime package extractDir is missing.");
        requireText(runtimePackage.getJavaPath(), "Runtime package javaPath is missing.");
    }

    private static boolean isInstalled(Path installDirectory, Path javaExecutable, RuntimePackage runtimePackage)
        throws IOException {
        if (!Files.isRegularFile(javaExecutable)) {
            return false;
        }

        Path markerFile = installDirectory.resolve(RUNTIME_MARKER_FILE);
        if (!Files.isRegularFile(markerFile)) {
            return false;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(markerFile)) {
            properties.load(inputStream);
        }

        return runtimePackage.getSha256().equalsIgnoreCase(properties.getProperty("sha256", ""))
            && runtimePackage.getJavaPath().equals(properties.getProperty("javaPath", ""));
    }

    private static void writeMarker(Path installDirectory, RuntimePackage runtimePackage) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sha256", runtimePackage.getSha256());
        properties.setProperty("javaPath", runtimePackage.getJavaPath());
        properties.setProperty("url", runtimePackage.getUrl());

        try (OutputStream outputStream = Files.newOutputStream(
            installDirectory.resolve(RUNTIME_MARKER_FILE),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            properties.store(outputStream, "Installed portable runtime");
        }
    }

    private static URL resolveDownloadUrl(LoadedManifest loadedManifest, ModpackManifest manifest, String rawUrl)
        throws IOException {
        return DownloadUrlResolver.resolve(
            loadedManifest.getSourceUrl(),
            manifest.getBaseUrl(),
            requireText(rawUrl, "Runtime package url is missing.")
        );
    }

    private static void verifyArchive(RuntimePackage runtimePackage, Path archiveFile) throws IOException {
        if (runtimePackage.getSize() != null && runtimePackage.getSize().longValue() >= 0L) {
            long actualSize = Files.size(archiveFile);
            if (actualSize != runtimePackage.getSize().longValue()) {
                throw new IOException(
                    "Runtime archive size mismatch. Expected " + runtimePackage.getSize() + ", got " + actualSize + "."
                );
            }
        }

        String actualSha256 = ChecksumUtils.sha256(archiveFile);
        if (!actualSha256.equalsIgnoreCase(runtimePackage.getSha256())) {
            throw new IOException(
                "Runtime archive SHA-256 mismatch. Expected " + runtimePackage.getSha256()
                    + ", got " + actualSha256 + "."
            );
        }
    }

    private static void unzip(Path archiveFile, Path targetDirectory) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archiveFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = resolveZipEntry(targetDirectory, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(
                        targetPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    )) {
                        copy(zipInputStream, outputStream);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static Path resolveZipEntry(Path targetDirectory, String entryName) {
        String normalized = entryName.replace('\\', '/');
        Path resolved = targetDirectory.resolve(normalized).normalize();
        if (!resolved.startsWith(targetDirectory)) {
            throw new IllegalArgumentException("Runtime archive contains invalid path: " + entryName);
        }
        return resolved;
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
    }

    private static long download(URL downloadUrl, Path target) throws IOException {
        URLConnection connection = downloadUrl.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);

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

    private static Path resolveInstallDirectory(Path gameDirectory, RuntimePackage runtimePackage) {
        Path relative = Paths.get(requireText(runtimePackage.getExtractDir(), "Runtime package extractDir is missing."));
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Runtime extractDir must be relative.");
        }
        Path resolved = gameDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IllegalArgumentException("Runtime extractDir escapes game directory.");
        }
        return resolved;
    }

    private static Path resolveJavaExecutable(Path installDirectory, RuntimePackage runtimePackage) {
        Path relative = Paths.get(requireText(runtimePackage.getJavaPath(), "Runtime package javaPath is missing."));
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Runtime javaPath must be relative.");
        }
        Path resolved = installDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(installDirectory)) {
            throw new IllegalArgumentException("Runtime javaPath escapes runtime directory.");
        }
        return resolved;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
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
}
