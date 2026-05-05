package ru.mcrpg.launcher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ModpackManifestGenerator {

    private final ObjectMapper objectMapper;

    public ModpackManifestGenerator() {
        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ModpackManifest generate(ManifestGeneratorConfig config) throws IOException {
        Path sourceDirectory = resolveRequiredDirectory(config.getSourceDirectory(), "Укажи исходную папку клиента.");
        Path outputFile = resolveOutputPath(config, sourceDirectory);

        List<Pattern> excludePatterns = buildExcludePatterns(config.getExcludePatterns());
        String autoExcludedOutput = relativizeIfInside(sourceDirectory, outputFile);

        List<ModpackFile> files = collectFiles(sourceDirectory, excludePatterns, autoExcludedOutput);

        ModpackManifest manifest = new ModpackManifest();
        manifest.setSchemaVersion(1);
        manifest.setId(requireText(config.getModpackId(), "Укажи id сборки."));
        manifest.setVersion(requireText(config.getModpackVersion(), "Укажи version сборки."));
        manifest.setBaseUrl(trimToNull(config.getBaseUrl()));

        LauncherManifestSettings launcher = new LauncherManifestSettings();
        launcher.setServerHost(trimToNull(config.getServerHost()));
        launcher.setServerPort(config.getServerPort());
        launcher.setWorkingDirectory(trimToNull(config.getWorkingDirectory()));
        launcher.setLaunchTemplate(trimToNull(config.getLaunchTemplate()));
        manifest.setLauncher(launcher);
        manifest.setRuntime(buildRuntime(config));
        manifest.setMinecraft(buildMinecraft(config));
        manifest.setFiles(files);
        return manifest;
    }

    public ManifestGenerationResult generateAndWrite(ManifestGeneratorConfig config) throws IOException {
        Path sourceDirectory = resolveRequiredDirectory(config.getSourceDirectory(), "Укажи исходную папку клиента.");
        Path outputFile = resolveOutputPath(config, sourceDirectory);
        ModpackManifest manifest = generate(config);

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(outputFile.toFile(), manifest);
        return new ManifestGenerationResult(outputFile, manifest);
    }

    private List<ModpackFile> collectFiles(Path sourceDirectory, List<Pattern> excludePatterns, String autoExcludedOutput)
        throws IOException {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(sourceDirectory, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)) {
            paths = stream
                .filter(Files::isRegularFile)
                .filter(path -> shouldInclude(sourceDirectory, path, excludePatterns, autoExcludedOutput))
                .sorted(Comparator.comparing(path -> toUnixPath(sourceDirectory.relativize(path))))
                .collect(Collectors.toList());
        }

        List<ModpackFile> files = new ArrayList<ModpackFile>(paths.size());
        for (Path path : paths) {
            ModpackFile file = new ModpackFile();
            file.setPath(toUnixPath(sourceDirectory.relativize(path)));
            file.setSha256(ChecksumUtils.sha256(path));
            file.setSize(Long.valueOf(Files.size(path)));
            if (Files.isExecutable(path)) {
                file.setExecutable(true);
            }
            files.add(file);
        }
        return files;
    }

    private static boolean shouldInclude(
        Path sourceDirectory,
        Path path,
        List<Pattern> excludePatterns,
        String autoExcludedOutput
    ) {
        String relativePath = toUnixPath(sourceDirectory.relativize(path));
        if (autoExcludedOutput != null && autoExcludedOutput.equals(relativePath)) {
            return false;
        }

        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(relativePath).matches()) {
                return false;
            }
        }
        return true;
    }

    private static List<Pattern> buildExcludePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }

        List<Pattern> compiled = new ArrayList<Pattern>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(globToRegex(requireText(pattern, "Exclude pattern must not be empty.")));
        }
        return compiled;
    }

    private static Pattern globToRegex(String glob) {
        String normalized = glob.replace('\\', '/');
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < normalized.length() && normalized.charAt(index + 1) == '*';
                if (doubleStar) {
                    regex.append(".*");
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                regex.append('\\').append(current);
            } else {
                regex.append(current);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private static Path resolveRequiredDirectory(Path directory, String message) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException(message);
        }
        Path resolved = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("Исходная папка клиента не найдена: " + resolved);
        }
        return resolved;
    }

    private static Path resolveOutputPath(ManifestGeneratorConfig config, Path sourceDirectory) {
        Path output = config.getOutputFile();
        if (output == null) {
            output = sourceDirectory.resolve("manifest.json");
        }
        return output.toAbsolutePath().normalize();
    }

    private static ModpackRuntime buildRuntime(ManifestGeneratorConfig config) throws IOException {
        if (config.getRuntimeArchive() == null) {
            return new ModpackRuntime();
        }

        Path runtimeArchive = config.getRuntimeArchive().toAbsolutePath().normalize();
        if (!Files.isRegularFile(runtimeArchive)) {
            throw new IllegalArgumentException("Runtime archive not found: " + runtimeArchive);
        }

        RuntimePackage runtimePackage = new RuntimePackage();
        runtimePackage.setOs(requireText(config.getRuntimeOs(), "Runtime OS is missing."));
        runtimePackage.setArch(requireText(config.getRuntimeArch(), "Runtime arch is missing."));
        runtimePackage.setUrl(requireText(config.getRuntimeUrl(), "Runtime URL is missing."));
        runtimePackage.setExtractDir(requireText(config.getRuntimeExtractDir(), "Runtime extractDir is missing."));
        runtimePackage.setJavaPath(requireText(config.getRuntimeJavaPath(), "Runtime javaPath is missing."));
        runtimePackage.setSha256(ChecksumUtils.sha256(runtimeArchive));
        runtimePackage.setSize(Long.valueOf(Files.size(runtimeArchive)));

        ModpackRuntime runtime = new ModpackRuntime();
        List<RuntimePackage> packages = new ArrayList<RuntimePackage>(1);
        packages.add(runtimePackage);
        runtime.setPackages(packages);
        return runtime;
    }

    private static MinecraftBootstrapSettings buildMinecraft(ManifestGeneratorConfig config) {
        boolean hasMinecraftVersion = hasText(config.getMinecraftVersion());
        boolean hasForgeVersion = hasText(config.getForgeVersion());
        boolean hasVersionManifestUrl = hasText(config.getVersionManifestUrl());
        boolean hasForgeInstallerUrl = hasText(config.getForgeInstallerUrl());
        boolean hasAssetBaseUrl = hasText(config.getAssetBaseUrl());

        if (!hasMinecraftVersion && !hasForgeVersion && !hasVersionManifestUrl && !hasForgeInstallerUrl && !hasAssetBaseUrl) {
            return new MinecraftBootstrapSettings();
        }
        if (!hasMinecraftVersion || !hasForgeVersion) {
            throw new IllegalArgumentException("Both minecraftVersion and forgeVersion are required for official bootstrap.");
        }

        MinecraftBootstrapSettings minecraft = new MinecraftBootstrapSettings();
        minecraft.setVersion(requireText(config.getMinecraftVersion(), "Minecraft version is missing."));
        minecraft.setForgeVersion(requireText(config.getForgeVersion(), "Forge version is missing."));
        minecraft.setVersionManifestUrl(trimToNull(config.getVersionManifestUrl()));
        minecraft.setForgeInstallerUrl(trimToNull(config.getForgeInstallerUrl()));
        minecraft.setAssetBaseUrl(trimToNull(config.getAssetBaseUrl()));
        return minecraft;
    }

    private static String relativizeIfInside(Path sourceDirectory, Path candidate) {
        if (candidate == null) {
            return null;
        }
        if (!candidate.startsWith(sourceDirectory)) {
            return null;
        }
        return toUnixPath(sourceDirectory.relativize(candidate));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
