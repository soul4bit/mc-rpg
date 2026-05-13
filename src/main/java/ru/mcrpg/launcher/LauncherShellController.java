package ru.mcrpg.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class LauncherShellController extends AbstractScreenController {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int SERVER_STATUS_TIMEOUT_MS = 1500;
    private static final String DASHBOARD_UNKNOWN = "—";
    private static final int PREVIEW_ENTRY_LIMIT = 4;
    private static final int RECONNECT_TICKET_COUNT = 5;

    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final MinecraftServerListWriter serverListWriter = new MinecraftServerListWriter();
    private final ModpackManifestClient manifestClient = new ModpackManifestClient();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(manifestClient);
    private final LauncherUpdateService launcherUpdateService = new LauncherUpdateService();
    private final AtomicLong endpointPreviewSequence = new AtomicLong();
    private final AtomicLong serverPresenceSequence = new AtomicLong();
    private final AtomicLong syncPreviewSequence = new AtomicLong();

    private LauncherConfig currentConfig = LauncherConfig.defaults();
    private LauncherUpdateCandidate availableLauncherUpdate;

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
    private Label sidebarStatusTitleLabel;

    @FXML
    private Label sidebarStatusCopyLabel;

    @FXML
    private Label sidebarVersionLabel;

    @FXML
    private Label manifestUrlValueLabel;

    @FXML
    private Label downloadBaseValueLabel;

    @FXML
    private Label onlinePlayersValueLabel;

    @FXML
    private Label minecraftVersionValueLabel;

    @FXML
    private Label manifestVersionValueLabel;

    @FXML
    private Button syncButton;

    @FXML
    private Button previewButton;

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
    private Label previewSummaryLabel;

    @FXML
    private VBox previewChangesBox;

    @FXML
    private VBox launcherUpdateCard;

    @FXML
    private Label launcherUpdateTitleLabel;

    @FXML
    private Label launcherUpdateDescriptionLabel;

    @FXML
    private Button launcherUpdateButton;

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
        resetPreviewState();
        onlinePlayersValueLabel.setText(DASHBOARD_UNKNOWN);
        minecraftVersionValueLabel.setText(DASHBOARD_UNKNOWN);
        manifestVersionValueLabel.setText(DASHBOARD_UNKNOWN);
        sidebarStatusTitleLabel.setText("Проверка сервера");
        sidebarStatusCopyLabel.setText("Проверяем маршрут, manifest и состояние сервера.");
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

    @FXML
    private void openSettingsScreen() {
        router().open(ScreenRouter.Screen.SETTINGS);
    }

    @FXML
    private void openServerScreen() {
        router().open(ScreenRouter.Screen.SERVER);
    }

    @FXML
    private void openModsScreen() {
        router().open(ScreenRouter.Screen.MODS);
    }

    private void configureControls() {
        logArea.setEditable(false);
        logArea.setWrapText(true);

        brandLogoLabel.setText(LauncherBrand.APP_TITLE);
        brandSubtitleLabel.setText(LauncherBrand.APP_SUBTITLE);
        sidebarVersionLabel.setText("Launcher " + LauncherBrand.displayVersion());
        profileChevronLabel.setText("⌄");
        applyWindowControlIcons();

        syncButton.setOnAction(event -> syncFiles());
        previewButton.setOnAction(event -> previewSyncChanges());
        launchButton.setOnAction(event -> launchClient());
        launcherUpdateButton.setOnAction(event -> updateLauncher());
        hideLauncherUpdateCard();
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

        syncPreviewSequence.incrementAndGet();
        serverRouteValueLabel.setText(formatRoute(resolvedConfig));
        manifestUrlValueLabel.setText(manifestUrl);
        downloadBaseValueLabel.setText(deriveManifestDirectory(manifestUrl));
        resetPreviewState();
        hideLauncherUpdateCard();
        applyDashboardLoadingState(hasText(manifestUrl));
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

    private void previewSyncChanges() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Set manifest URL in the launcher config before previewing sync.");
        } catch (Exception exception) {
            showLauncherError(exception.getMessage());
            return;
        }

        appendLog("Sync preview requested.");
        long requestId = syncPreviewSequence.incrementAndGet();
        setBusy(true);
        applyPreviewLoadingState();
        updateProgressState(true, "Проверяем локальные файлы", "PREVIEW", ProgressBar.INDETERMINATE_PROGRESS);

        Task<ModpackSyncPreviewResult> task = new Task<ModpackSyncPreviewResult>() {
            @Override
            protected ModpackSyncPreviewResult call() throws Exception {
                return modpackSyncService.preview(config, LauncherShellController.this::appendLogAsync);
            }
        };

        task.setOnSucceeded(event -> {
            if (requestId != syncPreviewSequence.get()) {
                return;
            }
            setBusy(false);
            applyPreviewResult(task.getValue());
        });

        task.setOnFailed(event -> {
            if (requestId != syncPreviewSequence.get()) {
                return;
            }
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Предпросмотр завершился ошибкой", "ОШИБКА", 0.0d);
            syncBytesLabel.setText("Предпросмотр не построен.");
            previewSummaryLabel.setText("Не удалось сравнить локальные файлы с manifest.files[].");
            previewChangesBox.getChildren().setAll(
                createPreviewEmptyState(exception == null ? "Preview failed." : exception.getMessage())
            );
            showLauncherError(exception == null ? "Preview failed." : exception.getMessage());
        });

        Thread thread = new Thread(task, "launcher-shell-preview");
        thread.setDaemon(true);
        thread.start();
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

    private void updateLauncher() {
        LauncherUpdateCandidate update = availableLauncherUpdate;
        if (update == null) {
            showLauncherError("Launcher update is not available.");
            return;
        }
        if (!update.isInstallSupported()) {
            showLauncherError("Auto-update works only when launcher is running from a jar file.");
            return;
        }

        setBusy(true);
        launcherUpdateButton.setDisable(true);
        updateProgressState(true, "Скачиваем обновление лаунчера", "UPDATE", ProgressBar.INDETERMINATE_PROGRESS);
        syncFileLabel.setText("После установки лаунчер перезапустится автоматически.");
        appendLog("Launcher update requested: " + update.getVersion() + ".");

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                launcherUpdateService.installAndRestart(update, LauncherShellController.this::appendLogAsync);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            appendLog("Launcher update downloaded. Exiting for restart.");
            Platform.exit();
            System.exit(0);
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Обновление лаунчера не удалось", "ОШИБКА", 0.0d);
            syncFileLabel.setText("Проверьте manifest launcherUpdate и доступность файла лаунчера.");
            launcherUpdateButton.setDisable(false);
            showLauncherError(exception == null ? "Launcher update failed." : exception.getMessage());
        });

        Thread thread = new Thread(task, "launcher-self-update");
        thread.setDaemon(true);
        thread.start();
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
                        List<GameTicket> reconnectTickets = createReconnectTickets(effectiveConfig, effectiveSession);
                        GameTicket gameTicket = reconnectTickets.get(0);
                        Path sessionFile = context().getSessionFileWriter().write(effectiveConfig, reconnectTickets);
                        effectiveConfig.setUsername(effectiveSession.getAccount().getUsername());
                        appendLogAsync(
                            "Prepared " + reconnectTickets.size() + " launcher reconnect tickets for " + gameTicket.getUsername() + "."
                        );
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

                    try {
                        serverListWriter.upsert(effectiveConfig);
                        appendLogAsync("Saved Minecraft server entry: " + effectiveConfig.getServerHost() + ":" + effectiveConfig.getServerPort());
                    } catch (IOException | IllegalArgumentException exception) {
                        appendLogAsync("Minecraft server list update skipped: " + exception.getMessage());
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
            if (handleExpiredSession(exception)) {
                return;
            }
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
        applyPreviewFromSyncResult(syncResult);
        serverRouteValueLabel.setText(formatRoute(resolvedConfig));
        downloadBaseValueLabel.setText(resolveDisplayDownloadBase(resolvedConfig.getManifestUrl(), syncResult.getManifest()));
        manifestVersionValueLabel.setText(resolveManifestVersion(syncResult.getManifest()));
        minecraftVersionValueLabel.setText(resolveMinecraftVersion(syncResult.getManifest()));
        refreshServerPresenceAsync(
            valueOrFallback(resolvedConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST),
            resolvedConfig.getServerPort(),
            formatRoute(resolvedConfig)
        );
    }

    private void applyPreviewResult(ModpackSyncPreviewResult previewResult) {
        int downloadFiles = previewResult.getDownloadFiles();
        int reusedFiles = previewResult.getReusedFiles();
        int totalFiles = previewResult.getEntries().size();
        String manifestVersion = resolveManifestVersion(previewResult.getManifest());

        if (downloadFiles <= 0) {
            updateProgressState(false, "Локальные файлы актуальны", "PREVIEW", 0.0d);
            syncFileLabel.setText("Все manifest.files[] уже совпадают с локальной сборкой.");
            syncBytesLabel.setText("К скачиванию 0 B");
            previewSummaryLabel.setText(
                "Manifest " + manifestVersion + ": все " + totalFiles
                    + " файлов актуальны. Preview сравнивает только manifest.files[]."
            );
            previewChangesBox.getChildren().setAll(
                createPreviewEmptyState("Изменений не найдено. Синхронизация скачает 0 файлов.")
            );
            return;
        }

        updateProgressState(false, "Нужна синхронизация", "PREVIEW", 0.0d);
        syncFileLabel.setText("Найдено " + downloadFiles + " файлов для обновления до запуска.");
        syncBytesLabel.setText("К скачиванию " + formatBytes(previewResult.getDownloadBytes()));
        previewSummaryLabel.setText(
            "Manifest " + manifestVersion + ": " + downloadFiles + " из " + totalFiles
                + " файлов требуют sync, актуальны " + reusedFiles + "."
        );
        renderPreviewChanges(previewResult.getEntries(), downloadFiles);
    }

    private void applyPreviewFromSyncResult(ModpackSyncResult syncResult) {
        previewSummaryLabel.setText(
            "Manifest " + resolveManifestVersion(syncResult.getManifest())
                + ": sync завершен, локальная копия должна совпадать с manifest.files[]."
        );
        previewChangesBox.getChildren().setAll(
            createPreviewEmptyState("Последняя синхронизация завершена. Для перепроверки запустите предпросмотр снова.")
        );
    }

    private void applyPreviewLoadingState() {
        previewSummaryLabel.setText("Проверяем sha256 локальных файлов и сравниваем их с manifest.files[].");
        previewChangesBox.getChildren().setAll(
            createPreviewEmptyState("Сканируем game directory и строим список изменений...")
        );
    }

    private void resetPreviewState() {
        previewSummaryLabel.setText("Предпросмотр ещё не запускался.");
        previewChangesBox.getChildren().setAll(
            createPreviewEmptyState("Нажмите «Предпросмотр», чтобы заранее увидеть изменения перед sync.")
        );
    }

    private void renderPreviewChanges(List<ModpackSyncPreviewEntry> entries, int totalChanged) {
        List<Node> nodes = new ArrayList<Node>();
        int shown = 0;
        for (ModpackSyncPreviewEntry entry : entries) {
            if (entry.getState() != ModpackSyncPreviewEntry.State.DOWNLOAD) {
                continue;
            }
            if (shown >= PREVIEW_ENTRY_LIMIT) {
                break;
            }
            nodes.add(createPreviewEntryCard(entry));
            shown++;
        }

        if (nodes.isEmpty()) {
            nodes.add(createPreviewEmptyState("Изменений не найдено."));
        } else if (totalChanged > shown) {
            Label moreLabel = new Label("Еще " + (totalChanged - shown) + " файлов ждут синхронизации.");
            moreLabel.getStyleClass().add("sync-preview-note");
            nodes.add(moreLabel);
        }

        previewChangesBox.getChildren().setAll(nodes);
    }

    private Node createPreviewEntryCard(ModpackSyncPreviewEntry entry) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("sync-preview-card");
        card.setPadding(new Insets(12.0, 14.0, 12.0, 14.0));

        HBox titleRow = new HBox(10.0);
        Label pathLabel = new Label(normalizeText(entry.getPath()));
        pathLabel.getStyleClass().add("sync-preview-title");
        pathLabel.setWrapText(true);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);
        titleRow.getChildren().addAll(
            pathLabel,
            createPreviewBadge(resolvePreviewCategory(entry.getPath())),
            createPreviewBadge(resolvePreviewReasonLabel(entry.getReason()))
        );

        Label reasonLabel = new Label(resolvePreviewReasonText(entry.getReason()));
        reasonLabel.getStyleClass().add("sync-preview-detail");
        reasonLabel.setWrapText(true);

        Label targetLabel = new Label("Target: " + normalizeText(entry.getTargetPath()));
        targetLabel.getStyleClass().add("sync-preview-detail");
        targetLabel.setWrapText(true);

        HBox metaRow = new HBox(14.0);
        metaRow.getChildren().add(createPreviewMeta("SIZE", formatBytes(entry.getSize() == null ? 0L : entry.getSize().longValue())));
        metaRow.getChildren().add(createPreviewMeta("SHA", shortenHash(entry.getSha256())));

        card.getChildren().addAll(titleRow, reasonLabel, targetLabel, metaRow);
        return card;
    }

    private Node createPreviewMeta(String labelText, String valueText) {
        VBox meta = new VBox(2.0);
        Label label = new Label(labelText);
        label.getStyleClass().add("sync-preview-meta-label");
        Label value = new Label(valueText);
        value.getStyleClass().add("sync-preview-meta-value");
        meta.getChildren().addAll(label, value);
        return meta;
    }

    private Label createPreviewBadge(String text) {
        Label badge = new Label(text.toUpperCase(Locale.ROOT));
        badge.getStyleClass().add("sync-preview-badge");
        return badge;
    }

    private Node createPreviewEmptyState(String text) {
        VBox box = new VBox();
        box.getStyleClass().add("sync-preview-empty");
        box.setPadding(new Insets(12.0, 14.0, 12.0, 14.0));

        Label label = new Label(text);
        label.getStyleClass().add("sync-preview-detail");
        label.setWrapText(true);
        box.getChildren().add(label);
        return box;
    }

    private void refreshEndpointPreviewAsync(LauncherConfig config) {
        long requestId = endpointPreviewSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            LauncherConfig previewConfig = LauncherDefaults.applyMissingValues(config.copy());
            String manifestUrl = previewConfig.getManifestUrl();
            LauncherUpdateCandidate launcherUpdate = null;
            String downloadBase = deriveManifestDirectory(manifestUrl);
            String manifestVersion = hasText(manifestUrl) ? "Нет данных" : "Не указан";
            String minecraftVersion = hasText(manifestUrl) ? DASHBOARD_UNKNOWN : "Не указан";

            try {
                LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
                ModpackManifest manifest = loadedManifest.getManifest();
                applyManifestSettings(previewConfig, manifest);
                downloadBase = resolveDisplayDownloadBase(loadedManifest);
                manifestVersion = resolveManifestVersion(manifest);
                minecraftVersion = resolveMinecraftVersion(manifest);
                launcherUpdate = launcherUpdateService.findUpdate(loadedManifest, LauncherBrand.displayVersion());
            } catch (Exception ignored) {
            }

            String resolvedRoute = formatRoute(previewConfig);
            String resolvedHost = valueOrFallback(previewConfig.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST);
            int resolvedPort = previewConfig.getServerPort();
            String resolvedDownloadBase = downloadBase;
            String resolvedManifestVersion = manifestVersion;
            String resolvedMinecraftVersion = minecraftVersion;
            LauncherUpdateCandidate resolvedLauncherUpdate = launcherUpdate;

            Platform.runLater(() -> {
                if (requestId != endpointPreviewSequence.get()) {
                    return;
                }
                serverRouteValueLabel.setText(resolvedRoute);
                downloadBaseValueLabel.setText(resolvedDownloadBase);
                manifestVersionValueLabel.setText(resolvedManifestVersion);
                minecraftVersionValueLabel.setText(resolvedMinecraftVersion);
                applyLauncherUpdateState(resolvedLauncherUpdate);
                refreshServerPresenceAsync(resolvedHost, resolvedPort, resolvedRoute);
            });
        }, "launcher-shell-endpoint-preview");

        thread.setDaemon(true);
        thread.start();
    }

    private void applyLauncherUpdateState(LauncherUpdateCandidate update) {
        availableLauncherUpdate = update;
        if (update == null) {
            hideLauncherUpdateCard();
            return;
        }

        launcherUpdateCard.setManaged(true);
        launcherUpdateCard.setVisible(true);
        launcherUpdateTitleLabel.setText(
            update.isRequired()
                ? "Требуется обновление лаунчера"
                : "Доступно обновление лаунчера"
        );
        launcherUpdateDescriptionLabel.setText(
            "Текущая версия: " + valueOrFallback(update.getCurrentVersion(), "unknown")
                + ". Новая версия: " + update.getVersion() + "."
        );
        launcherUpdateButton.setDisable(!update.isInstallSupported());
        launcherUpdateButton.setText(update.isInstallSupported() ? "Обновить" : "Скачать вручную");
    }

    private void hideLauncherUpdateCard() {
        availableLauncherUpdate = null;
        if (launcherUpdateCard == null) {
            return;
        }
        launcherUpdateCard.setManaged(false);
        launcherUpdateCard.setVisible(false);
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
        onlinePlayersValueLabel.setText(DASHBOARD_UNKNOWN);
        sidebarStatusTitleLabel.setText("Проверка сервера");
        sidebarStatusCopyLabel.setText("Обновляем пинг и список игроков.");
        long requestId = serverPresenceSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            MinecraftServerStatusProbe.ServerStatus status = null;
            try {
                status = MinecraftServerStatusProbe.probe(host, port, SERVER_STATUS_TIMEOUT_MS);
            } catch (IOException ignored) {
            }

            MinecraftServerStatusProbe.ServerStatus resolvedStatus = status;
            Platform.runLater(() -> {
                if (requestId != serverPresenceSequence.get()) {
                    return;
                }

                if (resolvedStatus != null) {
                    updateServerPresence("Онлайн", "online");
                    onlinePlayersValueLabel.setText(formatPlayers(resolvedStatus));
                    sidebarStatusTitleLabel.setText("Сервер отвечает • " + resolvedStatus.getPingMs() + " мс");
                    sidebarStatusCopyLabel.setText(
                        "Онлайн " + formatPlayers(resolvedStatus) + ", версия " + resolveServerVersion(resolvedStatus) + "."
                    );
                    return;
                }

                updateServerPresence("Оффлайн", "offline");
                onlinePlayersValueLabel.setText(DASHBOARD_UNKNOWN);
                sidebarStatusTitleLabel.setText("Сервер недоступен");
                sidebarStatusCopyLabel.setText("Маршрут " + route + " не отвечает. Проверьте сервер или сеть.");
                appendLog("No response from " + route + ".");
            });
        }, "launcher-shell-presence");

        thread.setDaemon(true);
        thread.start();
    }

    private void applyDashboardLoadingState(boolean hasManifestUrl) {
        manifestVersionValueLabel.setText(hasManifestUrl ? "проверка..." : "не указан");
        minecraftVersionValueLabel.setText(hasManifestUrl ? "проверка..." : "не указан");
        onlinePlayersValueLabel.setText(DASHBOARD_UNKNOWN);
        sidebarStatusTitleLabel.setText("Проверка сервера");
        sidebarStatusCopyLabel.setText(
            hasManifestUrl
                ? "Проверяем маршрут, manifest и состояние сервера."
                : "Укажите manifest URL, чтобы получить сборку клиента и статус сервера."
        );
    }

    private static String resolveManifestVersion(ModpackManifest manifest) {
        if (manifest == null || !hasText(manifest.getVersion())) {
            return "нет данных";
        }
        return manifest.getVersion().trim();
    }

    private static String resolveMinecraftVersion(ModpackManifest manifest) {
        if (manifest == null || manifest.getMinecraft() == null) {
            return "нет данных";
        }

        String minecraftVersion = manifest.getMinecraft().getVersion();
        String forgeVersion = manifest.getMinecraft().getForgeVersion();
        if (hasText(minecraftVersion) && hasText(forgeVersion)) {
            return minecraftVersion.trim() + " / " + forgeVersion.trim();
        }
        if (hasText(minecraftVersion)) {
            return minecraftVersion.trim();
        }
        if (hasText(forgeVersion)) {
            return "Forge " + forgeVersion.trim();
        }
        return "нет данных";
    }

    private static String formatPlayers(MinecraftServerStatusProbe.ServerStatus status) {
        if (status == null || status.getOnlinePlayers() < 0) {
            return DASHBOARD_UNKNOWN;
        }
        if (status.getMaxPlayers() < 0) {
            return Integer.toString(status.getOnlinePlayers());
        }
        return status.getOnlinePlayers() + " / " + status.getMaxPlayers();
    }

    private static String resolveServerVersion(MinecraftServerStatusProbe.ServerStatus status) {
        if (status == null || !hasText(status.getVersionName())) {
            return "неизвестно";
        }
        return status.getVersionName().trim();
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

    private List<GameTicket> createReconnectTickets(LauncherConfig config, AuthSession session) throws IOException {
        List<GameTicket> tickets = new ArrayList<GameTicket>(RECONNECT_TICKET_COUNT);
        for (int index = 0; index < RECONNECT_TICKET_COUNT; index++) {
            tickets.add(context().getAuthService().createGameTicket(config, session));
        }
        return tickets;
    }

    private void setBusy(boolean busy) {
        syncButton.setDisable(busy);
        previewButton.setDisable(busy);
        launchButton.setDisable(busy);
        if (launcherUpdateButton != null) {
            launcherUpdateButton.setDisable(busy || availableLauncherUpdate == null || !availableLauncherUpdate.isInstallSupported());
        }
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

    private boolean handleExpiredSession(Throwable exception) {
        if (!(exception instanceof AuthSessionExpiredException)) {
            return false;
        }

        String message = exception.getMessage();
        state().setSession(null);
        state().setAuthNotice(message);
        context().persistStateQuietly();
        applyProfileState();
        updateProgressState(false, "Требуется повторный вход", "СЕССИЯ", 0.0d);
        syncFileLabel.setText(message);
        syncBytesLabel.setText("Сохраненная сессия сброшена. Авторизуйтесь снова.");
        appendLog("Saved session expired. Redirecting to login.");
        router().open(ScreenRouter.Screen.AUTH);
        return true;
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

    private static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0 B";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kilobytes = bytes / 1024.0d;
        if (kilobytes < 1024.0d) {
            return String.format(Locale.US, "%.1f KB", Double.valueOf(kilobytes));
        }
        double megabytes = kilobytes / 1024.0d;
        return String.format(Locale.US, "%.1f MB", Double.valueOf(megabytes));
    }

    private static String normalizeText(String value) {
        return hasText(value) ? value.trim() : DASHBOARD_UNKNOWN;
    }

    private static String shortenHash(String value) {
        if (!hasText(value)) {
            return DASHBOARD_UNKNOWN;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 16) {
            return trimmed;
        }
        return trimmed.substring(0, 12) + "..." + trimmed.substring(trimmed.length() - 8);
    }

    private static String resolvePreviewCategory(String path) {
        if (!hasText(path)) {
            return "file";
        }
        String normalized = path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("mods/")) {
            return "mod";
        }
        if (normalized.startsWith("config/")) {
            return "config";
        }
        if (normalized.startsWith("resourcepacks/")) {
            return "assets";
        }
        return "file";
    }

    private static String resolvePreviewReasonLabel(String reason) {
        if ("missing".equals(reason)) {
            return "missing";
        }
        if ("sha256-mismatch".equals(reason)) {
            return "replace";
        }
        if ("size-mismatch".equals(reason)) {
            return "replace";
        }
        return "ready";
    }

    private static String resolvePreviewReasonText(String reason) {
        if ("size-mismatch".equals(reason)) {
            return "Размер файла не совпадает с manifest, поэтому файл будет заменён.";
        }
        if ("missing".equals(reason)) {
            return "Файл отсутствует локально и будет скачан заново.";
        }
        if ("sha256-mismatch".equals(reason)) {
            return "SHA-256 не совпадает с manifest, поэтому файл будет заменен.";
        }
        return "Локальная копия уже совпадает с manifest.";
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
