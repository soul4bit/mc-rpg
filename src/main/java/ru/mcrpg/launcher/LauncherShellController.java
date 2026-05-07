package ru.mcrpg.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class LauncherShellController extends AbstractScreenController {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final ModpackManifestClient manifestClient = new ModpackManifestClient();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(manifestClient);
    private final AtomicLong endpointPreviewSequence = new AtomicLong();
    private final AtomicLong serverPresenceSequence = new AtomicLong();

    private LauncherConfig currentConfig = LauncherConfig.defaults();

    @FXML
    private Label brandLogoLabel;

    @FXML
    private Label brandSubtitleLabel;

    @FXML
    private Region serverPresenceIndicator;

    @FXML
    private Label serverPresenceLabel;

    @FXML
    private Label serverRouteValueLabel;

    @FXML
    private Label manifestUrlValueLabel;

    @FXML
    private Label downloadBaseValueLabel;

    @FXML
    private Button syncButton;

    @FXML
    private Button launchButton;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private Label syncFileLabel;

    @FXML
    private ProgressBar syncProgressBar;

    @FXML
    private Label syncPercentLabel;

    @FXML
    private Label syncStatusLabel;

    @FXML
    private Label syncBytesLabel;

    @FXML
    private TextArea logArea;

    @FXML
    private Label profileNameLabel;

    @FXML
    private Label profileRankLabel;

    @FXML
    private Label profileChevronLabel;

    @FXML
    private void initialize() {
        configureControls();
        updateProgressState(false, "Готово к запуску", "ГОТОВО", 0.0d);
        syncFileLabel.setText("Лаунчер готов к работе.");
        syncBytesLabel.setText("Файловая активность пока отсутствует.");
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        currentConfig = LauncherDefaults.applyMissingValues(state().getConfig().copy());
        applyConfigToView(currentConfig);
        applyProfileState();
        appendLog("Launcher view loaded.");
    }

    @FXML
    private void openProfileScreen() {
        router().open(state().isAuthenticated() ? ScreenRouter.Screen.PROFILE : ScreenRouter.Screen.AUTH);
    }

    private void configureControls() {
        logArea.setEditable(false);
        logArea.setWrapText(true);

        brandLogoLabel.setText(LauncherBrand.APP_TITLE);
        brandSubtitleLabel.setText(LauncherBrand.APP_SUBTITLE);
        profileChevronLabel.setText("⌄");
        applyWindowControlIcons();

        syncButton.setOnAction(event -> syncFiles());
        launchButton.setOnAction(event -> launchClient());
    }

    private void applyProfileState() {
        if (state().isAuthenticated()) {
            AuthAccount account = state().getSession().getAccount();
            profileNameLabel.setText(account.getUsername());
            profileRankLabel.setText(resolveRoleLabel(account.getRole()));
            syncFileLabel.setText("Сессия активна. Перед запуском будет получен игровой ticket.");
            return;
        }

        profileNameLabel.setText("Гость");
        profileRankLabel.setText("Оффлайн режим");
        syncFileLabel.setText("Авторизуйтесь для запуска через серверный аккаунт или продолжайте оффлайн.");
    }

    private void applyConfigToView(LauncherConfig config) {
        LauncherConfig resolvedConfig = LauncherDefaults.applyMissingValues(config.copy());
        String host = valueOrFallback(resolvedConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST);
        String manifestUrl = valueOrFallback(
            resolvedConfig.getManifestUrl(),
            LauncherDefaults.defaultManifestUrl(host)
        );

        serverRouteValueLabel.setText(formatRoute(resolvedConfig));
        manifestUrlValueLabel.setText(manifestUrl);
        downloadBaseValueLabel.setText(deriveManifestDirectory(manifestUrl));
        updateServerPresence("ПРОВЕРКА", "checking");
        refreshEndpointPreviewAsync(resolvedConfig);
    }

    private LauncherConfig buildCurrentConfig() {
        LauncherConfig config = LauncherDefaults.applyMissingValues(currentConfig.copy());
        if (state().isAuthenticated()) {
            config.setUsername(state().getSession().getAccount().getUsername());
        }
        return config;
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Set manifest URL in the launcher config before syncing.");
            persistConfig(config, false);
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        appendLog("Sync requested.");
        runTask(LauncherAction.SYNC_ONLY, config);
    }

    private void launchClient() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            persistConfig(config, false);
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        if (shouldSyncBeforeLaunch(config)) {
            appendLog("Auto update is enabled. Files will sync before launch.");
        }

        runTask(LauncherAction.SYNC_AND_LAUNCH, config);
    }

    private void runTask(LauncherAction action, LauncherConfig requestedConfig) {
        setBusy(true);
        updateProgressState(
            true,
            action == LauncherAction.SYNC_ONLY ? "Проверка манифеста и файлов" : "Подготовка клиента",
            "...",
            ProgressBar.INDETERMINATE_PROGRESS
        );

        Task<LauncherTaskResult> task = new Task<LauncherTaskResult>() {
            @Override
            protected LauncherTaskResult call() throws Exception {
                LauncherConfig effectiveConfig = requestedConfig.copy();
                ModpackSyncResult syncResult = null;
                AuthSession effectiveSession = state().getSession();

                if (action == LauncherAction.SYNC_ONLY || shouldSyncBeforeLaunch(effectiveConfig)) {
                    syncResult = modpackSyncService.sync(effectiveConfig, LauncherShellController.this::appendLogAsync);
                    effectiveConfig = syncResult.getResolvedConfig();
                }

                Integer exitCode = null;
                if (action == LauncherAction.SYNC_AND_LAUNCH) {
                    List<String> command;
                    if (effectiveSession != null && effectiveSession.getAccount() != null) {
                        effectiveSession = context().getAuthService().refreshIfNeeded(effectiveConfig, effectiveSession);
                        GameTicket gameTicket = context().getAuthService().createGameTicket(effectiveConfig, effectiveSession);
                        Path sessionFile = context().getSessionFileWriter().write(effectiveConfig, gameTicket);
                        effectiveConfig.setUsername(effectiveSession.getAccount().getUsername());
                        appendLogAsync("Created game ticket for " + gameTicket.getUsername() + ".");
                        command = commandBuilder.build(
                            effectiveConfig,
                            LaunchIdentity.authenticated(
                                effectiveSession.getAccount().getUsername(),
                                gameTicket.getUuid(),
                                effectiveSession.getAccessToken(),
                                sessionFile
                            )
                        );
                    } else {
                        command = commandBuilder.build(effectiveConfig);
                    }

                    Path workingDirectory = resolveWorkingDirectory(effectiveConfig);
                    appendLogAsync("Launch command: " + commandBuilder.preview(command));
                    if (workingDirectory != null) {
                        appendLogAsync("Working directory: " + workingDirectory.toAbsolutePath());
                    }

                    exitCode = Integer.valueOf(runProcess(command, workingDirectory));
                }

                return new LauncherTaskResult(effectiveConfig, syncResult, exitCode, effectiveSession);
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            LauncherTaskResult result = task.getValue();
            try {
                if (result.session != null) {
                    state().setSession(result.session);
                    context().getAuthService().persist(result.session);
                }
                persistConfig(result.resolvedConfig, false);
                applyProfileState();

                if (result.syncResult != null) {
                    applySyncResult(result.syncResult, result.resolvedConfig);
                } else if (result.exitCode != null) {
                    updateProgressState(false, "Игровая сессия завершена", "ГОТОВО", 0.0d);
                    syncBytesLabel.setText("Код выхода " + result.exitCode);
                } else {
                    updateProgressState(false, "Готово к запуску", "ГОТОВО", 0.0d);
                }

                if (result.exitCode != null) {
                    appendLog("Client process exited with code " + result.exitCode + ".");
                }
            } catch (IOException exception) {
                showLauncherError("Failed to save launcher state: " + exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Операция завершилась ошибкой", "ОШИБКА", 0.0d);
            syncBytesLabel.setText("Подробности смотрите в журнале лаунчера.");
            showLauncherError(exception == null ? "Unknown launcher error." : exception.getMessage());
        });

        task.setOnCancelled(event -> {
            setBusy(false);
            updateProgressState(false, "Операция отменена", "СТОП", 0.0d);
            syncBytesLabel.setText("Файлы не изменялись.");
        });

        Thread thread = new Thread(task, "launcher-shell-" + action.name().toLowerCase(Locale.ROOT));
        thread.setDaemon(true);
        thread.start();
    }

    private void applySyncResult(ModpackSyncResult syncResult, LauncherConfig resolvedConfig) {
        updateProgressState(false, "Синхронизация завершена", "100%", 1.0d);
        syncBytesLabel.setText(formatSyncSummary(syncResult));
        syncFileLabel.setText(
            "Загружено " + syncResult.getDownloadedFiles() + " файлов, переиспользовано "
                + syncResult.getReusedFiles() + "."
        );
        serverRouteValueLabel.setText(formatRoute(resolvedConfig));
        downloadBaseValueLabel.setText(resolveDisplayDownloadBase(resolvedConfig.getManifestUrl(), syncResult.getManifest()));
    }

    private void refreshEndpointPreviewAsync(LauncherConfig config) {
        long requestId = endpointPreviewSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            LauncherConfig previewConfig = LauncherDefaults.applyMissingValues(config.copy());
            String manifestUrl = previewConfig.getManifestUrl();
            String downloadBase = deriveManifestDirectory(manifestUrl);

            try {
                LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
                ModpackManifest manifest = loadedManifest.getManifest();
                applyManifestSettings(previewConfig, manifest);
                downloadBase = resolveDisplayDownloadBase(loadedManifest);
            } catch (Exception ignored) {
            }

            String resolvedRoute = formatRoute(previewConfig);
            String resolvedHost = valueOrFallback(previewConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST);
            int resolvedPort = previewConfig.getServerPort();
            String resolvedDownloadBase = downloadBase;

            Platform.runLater(() -> {
                if (requestId != endpointPreviewSequence.get()) {
                    return;
                }
                serverRouteValueLabel.setText(resolvedRoute);
                downloadBaseValueLabel.setText(resolvedDownloadBase);
                refreshServerPresenceAsync(resolvedHost, resolvedPort, resolvedRoute);
            });
        }, "launcher-shell-endpoint-preview");

        thread.setDaemon(true);
        thread.start();
    }

    private void applyManifestSettings(LauncherConfig config, ModpackManifest manifest) {
        if (config == null || manifest == null || manifest.getLauncher() == null) {
            return;
        }
        LauncherManifestSettings settings = manifest.getLauncher();
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
            config.setWorkingDirectory(settings.getWorkingDirectory().trim());
        }
        if (hasText(settings.getAuthBaseUrl())) {
            config.setAuthBaseUrl(settings.getAuthBaseUrl().trim());
        }
        if (hasText(settings.getServerId())) {
            config.setServerId(settings.getServerId().trim());
        }
    }

    private void refreshServerPresenceAsync(String host, int port, String route) {
        updateServerPresence("ПРОВЕРКА", "checking");
        long requestId = serverPresenceSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            boolean online = false;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1500);
                online = true;
            } catch (IOException ignored) {
            }

            boolean resolvedOnline = online;
            Platform.runLater(() -> {
                if (requestId != serverPresenceSequence.get()) {
                    return;
                }
                updateServerPresence(resolvedOnline ? "Онлайн" : "Оффлайн", resolvedOnline ? "online" : "offline");
                if (!resolvedOnline) {
                    appendLog("No response from " + route + ".");
                }
            });
        }, "launcher-shell-presence");

        thread.setDaemon(true);
        thread.start();
    }

    private void updateServerPresence(String title, String tone) {
        serverPresenceLabel.setText(title);
        toggleStyleClass(serverPresenceIndicator, "presence-checking", "checking".equals(tone));
        toggleStyleClass(serverPresenceIndicator, "presence-online", "online".equals(tone));
        toggleStyleClass(serverPresenceIndicator, "presence-offline", "offline".equals(tone));
    }

    private int runProcess(List<String> command, Path workingDirectory) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLogAsync(line);
            }
        }
        return process.waitFor();
    }

    private void setBusy(boolean busy) {
        syncButton.setDisable(busy);
        launchButton.setDisable(busy);
    }

    private void updateProgressState(boolean busy, String statusText, String percentText, double progress) {
        syncProgressBar.setProgress(progress);
        syncStatusLabel.setText(statusText);
        syncPercentLabel.setText(percentText);
        if (busy) {
            syncBytesLabel.setText("Лаунчер выполняет операцию...");
        }
    }

    private void persistConfig(LauncherConfig config, boolean logPath) throws IOException {
        currentConfig = LauncherDefaults.applyMissingValues(config.copy());
        context().saveConfig(currentConfig);
        applyConfigToView(currentConfig);
        if (logPath) {
            appendLog("Config saved: " + context().getConfigStore().getConfigFile());
        }
    }

    private void appendLog(String message) {
        String resolvedMessage = message == null ? "" : message;
        logArea.appendText("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + resolvedMessage + System.lineSeparator());
        logArea.positionCaret(logArea.getLength());
    }

    private void appendLogAsync(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void showLauncherError(String message) {
        appendLog("Error: " + message);
        showError(message);
    }

    private void applySvgIconOnlyButton(Button button, String iconName, double size, String color) {
        button.setText("");
        button.setGraphic(SvgIcons.icon(iconName, size, color));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void applyWindowControlIcons() {
        applySvgIconOnlyButton(minimizeWindowButton, "minimize", 18, "#D9D9D9");
        applySvgIconOnlyButton(closeWindowButton, "close", 18, "#D9D9D9");
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
                throw new IllegalArgumentException("Game directory was not found: " + gameDirectory);
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
            throw new IllegalArgumentException("Working directory was not found: " + workingDirectory);
        }
        return workingDirectory;
    }

    private static String formatRoute(LauncherConfig config) {
        return valueOrFallback(config.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST) + ":" + config.getServerPort();
    }

    private static String deriveManifestDirectory(String manifestUrl) {
        if (!hasText(manifestUrl)) {
            return "Manifest URL is not configured.";
        }
        String resolved = manifestUrl.trim();
        int separator = resolved.lastIndexOf('/');
        if (separator < 0) {
            return resolved;
        }
        return resolved.substring(0, separator + 1);
    }

    private static String resolveDisplayDownloadBase(LoadedManifest loadedManifest) {
        ModpackManifest manifest = loadedManifest.getManifest();
        if (manifest != null && hasText(manifest.getBaseUrl())) {
            try {
                return new URL(loadedManifest.getSourceUrl(), manifest.getBaseUrl().trim()).toString();
            } catch (Exception ignored) {
            }
        }
        return deriveManifestDirectory(loadedManifest.getSourceUrl().toString());
    }

    private static String resolveDisplayDownloadBase(String manifestUrl, ModpackManifest manifest) {
        if (manifest != null && hasText(manifest.getBaseUrl()) && hasText(manifestUrl)) {
            try {
                return new URL(new URL(manifestUrl.trim()), manifest.getBaseUrl().trim()).toString();
            } catch (Exception ignored) {
            }
        }
        return deriveManifestDirectory(manifestUrl);
    }

    private static String formatSyncSummary(ModpackSyncResult syncResult) {
        return syncResult.getDownloadedFiles()
            + " загружено / "
            + syncResult.getReusedFiles()
            + " повторно использовано / "
            + formatMegabytes(syncResult.getDownloadedBytes());
    }

    private static String formatMegabytes(long bytes) {
        double megabytes = bytes / 1024.0d / 1024.0d;
        return String.format(Locale.US, "%.1f MB", Double.valueOf(megabytes));
    }

    private static boolean shouldSyncBeforeLaunch(LauncherConfig config) {
        return config.isUpdateFilesBeforeLaunch() && hasText(config.getManifestUrl());
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

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String resolveRoleLabel(String role) {
        if (!hasText(role)) {
            return "Игрок";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(normalized)) {
            return "Администратор";
        }
        if ("moderator".equals(normalized)) {
            return "Модератор";
        }
        if ("vip".equals(normalized)) {
            return "VIP";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static void toggleStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private enum LauncherAction {
        SYNC_ONLY,
        SYNC_AND_LAUNCH
    }

    private static final class LauncherTaskResult {
        private final LauncherConfig resolvedConfig;
        private final ModpackSyncResult syncResult;
        private final Integer exitCode;
        private final AuthSession session;

        private LauncherTaskResult(
            LauncherConfig resolvedConfig,
            ModpackSyncResult syncResult,
            Integer exitCode,
            AuthSession session
        ) {
            this.resolvedConfig = resolvedConfig;
            this.syncResult = syncResult;
            this.exitCode = exitCode;
            this.session = session;
        }
    }
}
