package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;

public final class LauncherBackendCli {

    private final ObjectMapper objectMapper;
    private final LauncherConfigStore configStore;
    private final LaunchCommandBuilder commandBuilder;
    private final ModpackSyncService modpackSyncService;
    private final LauncherHomeContentLoader homeContentLoader;
    private final InputStream inputStream;
    private final PrintWriter outputWriter;

    public LauncherBackendCli() {
        this(
            LauncherConfigStore.defaultStore(),
            new LaunchCommandBuilder(),
            new ModpackSyncService(new ModpackManifestClient()),
            new LauncherHomeContentLoader(),
            System.in,
            System.out
        );
    }

    LauncherBackendCli(
        LauncherConfigStore configStore,
        LaunchCommandBuilder commandBuilder,
        ModpackSyncService modpackSyncService,
        LauncherHomeContentLoader homeContentLoader,
        InputStream inputStream,
        OutputStream outputStream
    ) {
        this.configStore = configStore;
        this.commandBuilder = commandBuilder;
        this.modpackSyncService = modpackSyncService;
        this.homeContentLoader = homeContentLoader;
        this.inputStream = inputStream;
        outputWriter = new PrintWriter(outputStream, true, StandardCharsets.UTF_8);
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static void main(String[] args) {
        int exitCode = new LauncherBackendCli().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        if (args == null || args.length == 0) {
            emitError("Укажи команду backend bridge.");
            return 1;
        }

        try {
            String command = args[0].trim().toLowerCase();
            switch (command) {
                case "bootstrap":
                    bootstrap();
                    return 0;
                case "save-config":
                    saveConfig();
                    return 0;
                case "preview-command":
                    previewCommand();
                    return 0;
                case "sync":
                    runAction(false);
                    return 0;
                case "launch":
                    runAction(true);
                    return 0;
                default:
                    emitError("Неизвестная команда backend bridge: " + args[0]);
                    return 1;
            }
        } catch (Exception exception) {
            emitError(resolveMessage(exception));
            return 1;
        }
    }

    private void bootstrap() throws IOException {
        LauncherConfig config = loadConfig();

        ObjectNode result = baseResult();
        result.set("config", objectMapper.valueToTree(config));
        result.set("homeContent", objectMapper.valueToTree(homeContentLoader.loadDefault()));
        result.put("configFile", configStore.getConfigFile().toString());

        ObjectNode brand = result.putObject("brand");
        brand.put("appName", LauncherBrand.APP_NAME);
        brand.put("appTitle", LauncherBrand.APP_TITLE);
        brand.put("appSubtitle", LauncherBrand.APP_SUBTITLE);

        emit(result);
    }

    private void saveConfig() throws IOException {
        LauncherConfig config = LauncherDefaults.applyMissingValues(readConfig());
        configStore.save(config);

        ObjectNode result = baseResult();
        result.set("config", objectMapper.valueToTree(config));
        result.put("configFile", configStore.getConfigFile().toString());
        emit(result);
    }

    private void previewCommand() throws IOException {
        LauncherConfig config = LauncherDefaults.applyMissingValues(readConfig());
        String preview = commandBuilder.preview(commandBuilder.build(config));

        ObjectNode result = baseResult();
        result.put("preview", preview);
        emit(result);
    }

    private void runAction(boolean launch) throws Exception {
        LauncherConfig requestedConfig = LauncherDefaults.applyMissingValues(readConfig());
        LauncherConfig effectiveConfig = requestedConfig.copy();
        ModpackSyncResult syncResult = null;

        if (!launch || shouldSyncBeforeLaunch(effectiveConfig)) {
            emitLog("Синхронизация файлов запущена.");
            syncResult = modpackSyncService.sync(effectiveConfig, this::emitLog);
            effectiveConfig = syncResult.getResolvedConfig();
            emitLog(
                "Синхронизация завершена: скачано "
                    + syncResult.getDownloadedFiles()
                    + ", переиспользовано "
                    + syncResult.getReusedFiles()
                    + ", байт "
                    + syncResult.getDownloadedBytes()
                    + "."
            );
        }

        Integer exitCode = null;
        if (launch) {
            List<String> command = commandBuilder.build(effectiveConfig);
            Path workingDirectory = resolveWorkingDirectory(effectiveConfig);

            emitLog("Запуск: " + commandBuilder.preview(command));
            if (workingDirectory != null) {
                emitLog("Рабочая папка: " + workingDirectory.toAbsolutePath());
            }

            exitCode = Integer.valueOf(runProcess(command, workingDirectory));
            emitLog("Процесс завершился с кодом " + exitCode + ".");
        }

        configStore.save(effectiveConfig);
        ObjectNode result = baseResult();
        result.set("config", objectMapper.valueToTree(effectiveConfig));
        result.put("configFile", configStore.getConfigFile().toString());
        if (exitCode == null) {
            result.putNull("exitCode");
        } else {
            result.put("exitCode", exitCode.intValue());
        }
        if (syncResult != null) {
            ObjectNode summary = result.putObject("syncSummary");
            summary.put("downloadedFiles", syncResult.getDownloadedFiles());
            summary.put("reusedFiles", syncResult.getReusedFiles());
            summary.put("downloadedBytes", syncResult.getDownloadedBytes());
        }
        emit(result);
    }

    private LauncherConfig loadConfig() throws IOException {
        LauncherConfig config = configStore.load();
        return LauncherDefaults.applyMissingValues(config);
    }

    private LauncherConfig readConfig() throws IOException {
        LauncherConfig config = objectMapper.readValue(inputStream, LauncherConfig.class);
        if (config == null) {
            return LauncherConfig.defaults();
        }
        return config;
    }

    private int runProcess(List<String> command, Path workingDirectory) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        processBuilder.redirectErrorStream(true);

        LinkedHashSet<String> emittedHints = new LinkedHashSet<String>();
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                emitLog(line);
                for (String hint : ServerAuthHints.detect(line)) {
                    if (emittedHints.add(hint)) {
                        emitLog(hint);
                    }
                }
            }
        }
        return process.waitFor();
    }

    private Path resolveWorkingDirectory(LauncherConfig config) {
        Path gameDirectory = null;
        if (hasText(config.getGameDirectory())) {
            gameDirectory = Paths.get(config.getGameDirectory().trim()).toAbsolutePath().normalize();
        }

        if (!hasText(config.getWorkingDirectory())) {
            if (gameDirectory == null) {
                return null;
            }
            if (!Files.isDirectory(gameDirectory)) {
                throw new IllegalArgumentException("Папка игры не найдена: " + gameDirectory);
            }
            return gameDirectory;
        }

        Path path = Paths.get(config.getWorkingDirectory().trim());
        Path workingDirectory;
        if (path.isAbsolute() || gameDirectory == null) {
            workingDirectory = path.toAbsolutePath().normalize();
        } else {
            workingDirectory = gameDirectory.resolve(path).normalize();
        }

        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Рабочая папка не найдена: " + workingDirectory);
        }
        return workingDirectory;
    }

    private void emitLog(String message) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "log");
        event.put("message", message == null ? "" : message);
        emit(event);
    }

    private void emitError(String message) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "error");
        event.put("message", message == null ? "Неизвестная ошибка." : message);
        emit(event);
    }

    private ObjectNode baseResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "result");
        return result;
    }

    private void emit(ObjectNode event) {
        outputWriter.println(event.toString());
        outputWriter.flush();
    }

    private static boolean shouldSyncBeforeLaunch(LauncherConfig config) {
        return config.isUpdateFilesBeforeLaunch() && hasText(config.getManifestUrl());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String resolveMessage(Exception exception) {
        String message = exception.getMessage();
        return hasText(message) ? message.trim() : exception.getClass().getSimpleName();
    }
}
