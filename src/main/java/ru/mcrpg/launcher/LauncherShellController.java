package ru.mcrpg.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class LauncherShellController {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String BUILD_LABEL = "1.12.2 // Forge 14.23.5.2864";
    private static final String ASSET_ROOT = "/ru/mcrpg/launcher/redstone/";
    private static final String LOGO_ASSET = ASSET_ROOT + "logo/redstone_logo_full_transparent.png";
    private static final String HOME_ICON_ASSET = ASSET_ROOT + "ui_icons/home_red.png";
    private static final String PLAY_ICON_ASSET = ASSET_ROOT + "ui_icons/play_red.png";
    private static final String MODS_ICON_ASSET = ASSET_ROOT + "ui_icons/mods_gray.png";
    private static final String SETTINGS_ICON_ASSET = ASSET_ROOT + "ui_icons/settings_red.png";
    private static final String GLOBE_ICON_ASSET = ASSET_ROOT + "ui_icons/utility_globe_gray.png";

    private final LauncherConfigStore configStore = LauncherConfigStore.defaultStore();
    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(new ModpackManifestClient());
    private final LauncherHomeContent homeContent = new LauncherHomeContentLoader().loadDefault();
    private final AtomicLong serverPresenceSequence = new AtomicLong();

    private Stage primaryStage;
    private HostServices hostServices;
    private LauncherConfig currentConfig = LauncherConfig.defaults();
    private String lastPresenceRoute = "";
    private double dragOffsetX;
    private double dragOffsetY;

    @FXML
    private StackPane appRoot;

    @FXML
    private HBox shell;

    @FXML
    private HBox communityBar;

    @FXML
    private VBox logDrawerBox;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button playNavButton;

    @FXML
    private Button modsNavButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private Button launchButton;

    @FXML
    private Button syncButton;

    @FXML
    private Button toggleLogButton;

    @FXML
    private Button newsActionButton;

    @FXML
    private Label brandTitleLabel;

    @FXML
    private Label brandSubtitleLabel;

    @FXML
    private Label headerProfileLabel;

    @FXML
    private Label headerModeLabel;

    @FXML
    private Label heroTitleLabel;

    @FXML
    private Label heroSubtitleLabel;

    @FXML
    private Region realmPresenceIndicator;

    @FXML
    private Label realmPresenceLabel;

    @FXML
    private Label realmPresenceHintLabel;

    @FXML
    private Label serverRouteLabel;

    @FXML
    private Label serverVersionLabel;

    @FXML
    private Label serverProfileLabel;

    @FXML
    private Label serverFolderLabel;

    @FXML
    private Label latestNewsTitleLabel;

    @FXML
    private Label latestNewsCopyLabel;

    @FXML
    private Label latestNewsDateLabel;

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
    private void initialize() {
        styleControls();
        populateStaticContent();
        bindActions();
        setActiveNav(homeNavButton);
        setLogDrawerVisible(false);
        updateProgressState(false, "Waiting for sync", "0%", 0.0d);
        syncFileLabel.setText("Press Update or Play to verify client files.");
        syncBytesLabel.setText("Client state is idle.");
        updateServerPresence("CHECKING", "Preparing server route probe.", "checking");
    }

    public void attach(Stage stage, HostServices services) {
        primaryStage = stage;
        hostServices = services;
        currentConfig = loadConfig();
        applyConfigToView(currentConfig);
        installResponsiveBehavior(stage.getScene());
    }

    public void onCloseRequest() {
        persistCurrentConfigQuietly();
    }

    private void styleControls() {
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(true);
    }

    private void populateStaticContent() {
        homeNavButton.setText("Home");
        playNavButton.setText("Play");
        modsNavButton.setText("Mods");
        settingsButton.setText("Settings");
        brandTitleLabel.setText(LauncherBrand.APP_TITLE.toUpperCase());
        brandSubtitleLabel.setText("SURVIVAL RPG");
        heroTitleLabel.setText(LauncherBrand.APP_TITLE.toUpperCase());
        heroSubtitleLabel.setText("SURVIVAL RPG REALM");

        applyLabelGraphic(brandTitleLabel, LOGO_ASSET, 180, 72);
        applyLabelGraphic(heroTitleLabel, LOGO_ASSET, 320, 122);

        applyButtonGraphic(homeNavButton, HOME_ICON_ASSET, 18);
        applyButtonGraphic(playNavButton, PLAY_ICON_ASSET, 18);
        applyButtonGraphic(modsNavButton, MODS_ICON_ASSET, 18);
        applyButtonGraphic(settingsButton, SETTINGS_ICON_ASSET, 18);

        populateCommunityButtons();
        populateLatestNews();
    }

    private void populateCommunityButtons() {
        communityBar.getChildren().clear();
        for (LauncherHomeContent.CommunityLink link : homeContent.getCommunity()) {
            Button button = new Button(compactCommunityLabel(link.getLabel()));
            button.getStyleClass().add("sidebar-icon-button");
            applyButtonGraphic(button, GLOBE_ICON_ASSET, 14);
            button.setOnAction(event -> openCommunityLink(link));
            communityBar.getChildren().add(button);
        }
    }

    private void populateLatestNews() {
        LauncherHomeContent.NewsEntry entry = homeContent.getNews().isEmpty()
            ? null
            : homeContent.getNews().get(0);

        latestNewsTitleLabel.setText(valueOrFallback(entry == null ? "" : entry.getTitle(), "Realm update"));
        latestNewsCopyLabel.setText(
            valueOrFallback(
                entry == null ? "" : entry.getCopy(),
                "Launcher shell is ready. Connect your news feed when external URLs are available."
            )
        );
        latestNewsDateLabel.setText(valueOrFallback(entry == null ? "" : entry.getTag(), "REALM FEED"));
    }

    private void bindActions() {
        homeNavButton.setOnAction(event -> {
            setActiveNav(homeNavButton);
            setLogDrawerVisible(false);
        });
        playNavButton.setOnAction(event -> {
            setActiveNav(playNavButton);
            launchClient();
        });
        modsNavButton.setOnAction(event -> {
            setActiveNav(modsNavButton);
            syncFiles();
        });
        settingsButton.setOnAction(event -> {
            openSettingsDialog();
            setActiveNav(homeNavButton);
        });
        launchButton.setOnAction(event -> {
            setActiveNav(playNavButton);
            launchClient();
        });
        syncButton.setOnAction(event -> {
            setActiveNav(modsNavButton);
            syncFiles();
        });
        toggleLogButton.setOnAction(event -> toggleLogDrawer());
        newsActionButton.setOnAction(event -> openPrimaryCommunityLink());
    }

    private void installResponsiveBehavior(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveLayout(newValue.doubleValue()));
        applyResponsiveLayout(scene.getWidth());
    }

    private void applyResponsiveLayout(double width) {
        toggleStyleClass(appRoot, "compact-mode", width < 1380);
        toggleStyleClass(appRoot, "condensed-mode", width < 1220);
    }

    private LauncherConfig loadConfig() {
        try {
            LauncherConfig loadedConfig = configStore.load();
            LauncherDefaults.applyMissingValues(loadedConfig);
            appendLog("Config loaded from " + configStore.getConfigFile());
            return loadedConfig;
        } catch (IOException exception) {
            appendLog("Failed to load config: " + exception.getMessage());
            return LauncherDefaults.applyMissingValues(LauncherConfig.defaults());
        }
    }

    private void applyConfigToView(LauncherConfig config) {
        refreshSummary(config);
    }

    private void refreshSummary(LauncherConfig config) {
        String username = valueOrFallback(config.getUsername(), LauncherDefaults.defaultUsername());
        String gameDirectory = valueOrFallback(config.getGameDirectory(), LauncherDefaults.defaultGameDirectory());
        String folderName = displayFolderName(gameDirectory);
        String mode = config.isUpdateFilesBeforeLaunch() ? "Auto update before launch" : "Manual update mode";
        String host = valueOrFallback(config.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST);
        String route = host + ":" + config.getServerPort();

        headerProfileLabel.setText(username);
        headerModeLabel.setText(mode);

        serverRouteLabel.setText(route);
        serverVersionLabel.setText(BUILD_LABEL);
        serverProfileLabel.setText(username);
        serverFolderLabel.setText(folderName);

        if (!route.equals(lastPresenceRoute)) {
            lastPresenceRoute = route;
            refreshServerPresenceAsync(host, config.getServerPort(), route);
        }
    }

    private LauncherConfig buildCurrentConfig() {
        return LauncherDefaults.applyMissingValues(currentConfig.copy());
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Укажи URL manifest.json в Settings.");
            persistConfig(config, false);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        appendLog("Sync requested.");
        setLogDrawerVisible(true);
        runTask(LauncherAction.SYNC_ONLY, config);
    }

    private void launchClient() {
        LauncherConfig config;
        try {
            config = buildCurrentConfig();
            persistConfig(config, false);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        if (shouldSyncBeforeLaunch(config)) {
            appendLog("Auto update is enabled. Client files will be synced before launch.");
        }

        setLogDrawerVisible(true);
        runTask(LauncherAction.SYNC_AND_LAUNCH, config);
    }

    private void runTask(LauncherAction action, LauncherConfig requestedConfig) {
        setBusy(true);
        updateProgressState(
            true,
            action == LauncherAction.SYNC_ONLY ? "Checking manifest and files" : "Preparing launcher runtime",
            "...",
            ProgressBar.INDETERMINATE_PROGRESS
        );

        Task<LauncherTaskResult> task = new Task<LauncherTaskResult>() {
            @Override
            protected LauncherTaskResult call() throws Exception {
                LauncherConfig effectiveConfig = requestedConfig.copy();
                ModpackSyncResult syncResult = null;

                if (action == LauncherAction.SYNC_ONLY || shouldSyncBeforeLaunch(effectiveConfig)) {
                    syncResult = modpackSyncService.sync(effectiveConfig, LauncherShellController.this::appendLogAsync);
                    effectiveConfig = syncResult.getResolvedConfig();
                }

                Integer exitCode = null;
                if (action == LauncherAction.SYNC_AND_LAUNCH) {
                    List<String> command = commandBuilder.build(effectiveConfig);
                    Path workingDirectory = resolveWorkingDirectory(effectiveConfig);

                    appendLogAsync("Launch command: " + commandBuilder.preview(command));
                    if (workingDirectory != null) {
                        appendLogAsync("Working directory: " + workingDirectory.toAbsolutePath());
                    }

                    exitCode = Integer.valueOf(runProcess(command, workingDirectory));
                }

                return new LauncherTaskResult(effectiveConfig, syncResult, exitCode);
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            LauncherTaskResult result = task.getValue();
            try {
                persistConfig(result.getResolvedConfig(), false);
                applyConfigToView(result.getResolvedConfig());

                if (result.getSyncResult() != null) {
                    applySyncResult(result.getSyncResult());
                } else if (result.getExitCode() != null) {
                    updateProgressState(false, "Game session finished", "READY", 0.0d);
                    syncBytesLabel.setText("Exit code " + result.getExitCode());
                } else {
                    updateProgressState(false, "Ready", "READY", 0.0d);
                }

                if (result.getExitCode() != null) {
                    appendLog("Client process exited with code " + result.getExitCode() + ".");
                }
            } catch (IOException exception) {
                showError("Не удалось сохранить обновленный конфиг: " + exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Operation failed", "ERR", 0.0d);
            syncBytesLabel.setText("Review launcher log for details.");
            showError(exception == null ? "Неизвестная ошибка." : exception.getMessage());
        });

        task.setOnCancelled(event -> {
            setBusy(false);
            updateProgressState(false, "Operation cancelled", "STOP", 0.0d);
            syncBytesLabel.setText("No files changed.");
        });

        Thread thread = new Thread(task, "launcher-shell-" + action.name().toLowerCase());
        thread.setDaemon(true);
        thread.start();
    }

    private void applySyncResult(ModpackSyncResult syncResult) {
        updateProgressState(false, "Sync complete", "100%", 1.0d);
        syncBytesLabel.setText(formatSyncSummary(syncResult));
        syncFileLabel.setText(
            "Downloaded "
                + syncResult.getDownloadedFiles()
                + " files, reused "
                + syncResult.getReusedFiles()
                + "."
        );
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
                appendLogAsync(line);
                for (String hint : ServerAuthHints.detect(line)) {
                    if (emittedHints.add(hint)) {
                        appendLogAsync(hint);
                    }
                }
            }
        }
        return process.waitFor();
    }

    private void openSettingsDialog() {
        LauncherConfig snapshot = buildCurrentConfig();

        Stage dialog = new Stage();
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Настройки лаунчера");

        TextField usernameField = new TextField(snapshot.getUsername());
        TextField gameDirectoryField = new TextField(snapshot.getGameDirectory());
        CheckBox autoUpdateCheckBox = new CheckBox("Автоматически обновлять сборку перед запуском");
        autoUpdateCheckBox.setSelected(snapshot.isUpdateFilesBeforeLaunch());
        TextField javaCommandField = new TextField(snapshot.getJavaCommand());
        TextField manifestUrlField = new TextField(snapshot.getManifestUrl());
        TextField workingDirectoryField = new TextField(snapshot.getWorkingDirectory());
        TextField serverHostField = new TextField(snapshot.getServerHost());
        TextField serverPortField = new TextField(Integer.toString(snapshot.getServerPort()));
        TextArea launchTemplateArea = new TextArea(snapshot.getLaunchTemplate());

        usernameField.getStyleClass().add("launcher-input");
        gameDirectoryField.getStyleClass().add("launcher-input");
        autoUpdateCheckBox.getStyleClass().add("launcher-check");
        javaCommandField.getStyleClass().add("launcher-input");
        manifestUrlField.getStyleClass().add("launcher-input");
        workingDirectoryField.getStyleClass().add("launcher-input");
        serverHostField.getStyleClass().add("launcher-input");
        serverPortField.getStyleClass().add("launcher-input");
        launchTemplateArea.getStyleClass().add("launcher-textarea");
        launchTemplateArea.setWrapText(true);
        launchTemplateArea.setPrefRowCount(8);

        Button browseGameDirectoryButton = new Button("Папка");
        browseGameDirectoryButton.getStyleClass().addAll("action-button", "ghost-action");
        browseGameDirectoryButton.setOnAction(event -> chooseDirectory(gameDirectoryField, "Выбери каталог клиента"));

        Button browseWorkingDirectoryButton = new Button("Обзор");
        browseWorkingDirectoryButton.getStyleClass().addAll("action-button", "ghost-action");
        browseWorkingDirectoryButton.setOnAction(event -> chooseDirectory(workingDirectoryField, "Выбери рабочую папку"));

        Button previewButton = new Button("Проверить команду");
        Button cancelButton = new Button("Отмена");
        Button applyButton = new Button("Применить");
        previewButton.getStyleClass().addAll("action-button", "ghost-action");
        cancelButton.getStyleClass().addAll("action-button", "ghost-action");
        applyButton.getStyleClass().addAll("action-button", "primary-action");

        previewButton.setOnAction(event -> {
            try {
                LauncherConfig config = buildSettingsConfig(
                    snapshot,
                    usernameField,
                    gameDirectoryField,
                    autoUpdateCheckBox,
                    javaCommandField,
                    manifestUrlField,
                    workingDirectoryField,
                    serverHostField,
                    serverPortField,
                    launchTemplateArea
                );
                appendLog("Command preview: " + commandBuilder.preview(commandBuilder.build(config)));
            } catch (Exception exception) {
                showError(exception.getMessage());
            }
        });

        cancelButton.setOnAction(event -> dialog.close());
        applyButton.setOnAction(event -> {
            try {
                LauncherConfig updatedConfig = buildSettingsConfig(
                    snapshot,
                    usernameField,
                    gameDirectoryField,
                    autoUpdateCheckBox,
                    javaCommandField,
                    manifestUrlField,
                    workingDirectoryField,
                    serverHostField,
                    serverPortField,
                    launchTemplateArea
                );
                persistConfig(updatedConfig, true);
                applyConfigToView(updatedConfig);
                dialog.close();
            } catch (Exception exception) {
                showError(exception.getMessage());
            }
        });

        VBox root = new VBox(16);
        root.getStyleClass().add("dialog-root");
        root.setPadding(new Insets(18));

        VBox header = new VBox(6);
        header.getStyleClass().addAll("dialog-header", "settings-card");
        Label title = new Label("Настройки лаунчера");
        title.getStyleClass().add("card-title");
        Label copy = new Label(
            "Главный экран теперь ведет себя как launcher home. Все редактируемые поля профиля и клиента вынесены сюда."
        );
        copy.getStyleClass().add("field-hint");
        copy.setWrapText(true);
        header.getChildren().addAll(title, copy);

        VBox content = new VBox(14);
        content.getChildren().addAll(
            createSettingsCard(
                "PROFILE",
                "Игрок и клиент",
                createFieldGroup("Ник игрока", "Имя профиля, с которым клиент пойдет на сервер.", usernameField),
                createFieldGroup("Каталог клиента", "Папка со сборкой, runtime и локальными файлами.", buildInlineField(gameDirectoryField, browseGameDirectoryButton)),
                autoUpdateCheckBox
            ),
            createSettingsCard(
                "ENVIRONMENT",
                "Runtime и manifest",
                createFieldGroup("Java", "Команда запуска Java или путь к java.exe.", javaCommandField),
                createFieldGroup("Manifest URL", "HTTP(S)-адрес manifest.json.", manifestUrlField),
                createFieldGroup("Рабочая папка", "Каталог, из которого запускается клиент.", buildInlineField(workingDirectoryField, browseWorkingDirectoryButton))
            ),
            createSettingsCard(
                "SERVER",
                "Сетевой маршрут",
                createFieldGroup("IP сервера", "Хост Minecraft-сервера.", serverHostField),
                createFieldGroup("Порт", "Порт игрового сервера.", serverPortField)
            ),
            createSettingsCard(
                "LAUNCH",
                "Launch template",
                createInfoNote(
                    "Плейсхолдеры",
                    "{java}, {username}, {gameDir}, {workingDir}, {serverHost}, {serverPort}, {uuid}, {accessToken}, {userType}"
                ),
                createFieldGroup("Команда запуска", "Итоговая строка будет собрана из launch template и текущего config.", launchTemplateArea)
            )
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Label dialogConfigPath = new Label("Профиль: " + configStore.getConfigFile());
        dialogConfigPath.getStyleClass().add("dialog-path");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBar = new HBox(10, dialogConfigPath, spacer, cancelButton, previewButton, applyButton);
        buttonBar.getStyleClass().add("settings-actions");
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(header, scrollPane, buttonBar);

        Scene scene = new Scene(root, 980, 780);
        scene.getStylesheets().add(
            LauncherShellApplication.class.getResource("/ru/mcrpg/launcher/launcher-shell.css").toExternalForm()
        );
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private LauncherConfig buildSettingsConfig(
        LauncherConfig baseConfig,
        TextField usernameField,
        TextField gameDirectoryField,
        CheckBox autoUpdateCheckBox,
        TextField javaCommandField,
        TextField manifestUrlField,
        TextField workingDirectoryField,
        TextField serverHostField,
        TextField serverPortField,
        TextArea launchTemplateArea
    ) {
        LauncherConfig config = baseConfig.copy();
        config.setUsername(usernameField.getText().trim());
        config.setGameDirectory(gameDirectoryField.getText().trim());
        config.setUpdateFilesBeforeLaunch(autoUpdateCheckBox.isSelected());
        config.setJavaCommand(javaCommandField.getText().trim());
        config.setManifestUrl(manifestUrlField.getText().trim());
        config.setWorkingDirectory(workingDirectoryField.getText().trim());
        config.setServerHost(serverHostField.getText().trim());
        config.setServerPort(parsePortOrDefault(serverPortField.getText()));
        config.setLaunchTemplate(launchTemplateArea.getText().trim());
        return LauncherDefaults.applyMissingValues(config);
    }

    private Node buildInlineField(TextField field, Button button) {
        HBox row = new HBox(10, field, button);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private VBox createFieldGroup(String title, String hint, Node field) {
        VBox group = new VBox(8);
        group.getStyleClass().add("field-group");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("field-label");

        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("field-hint");
        hintLabel.setWrapText(true);

        group.getChildren().addAll(titleLabel, hintLabel, field);
        return group;
    }

    private VBox createInfoNote(String title, String copy) {
        VBox note = new VBox(6);
        note.getStyleClass().add("note-box");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("note-title");

        Label bodyLabel = new Label(copy);
        bodyLabel.getStyleClass().add("note-copy");
        bodyLabel.setWrapText(true);

        note.getChildren().addAll(titleLabel, bodyLabel);
        return note;
    }

    private VBox createSettingsCard(String eyebrow, String title, Node... content) {
        VBox card = new VBox(14);
        card.getStyleClass().add("settings-card");

        Label eyebrowLabel = new Label(eyebrow);
        eyebrowLabel.getStyleClass().add("sidebar-caption");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        VBox body = new VBox(12);
        body.getChildren().addAll(content);

        card.getChildren().addAll(eyebrowLabel, titleLabel, body);
        return card;
    }

    private void chooseDirectory(TextField targetField, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);

        String currentValue = targetField.getText().trim();
        if (!currentValue.isEmpty()) {
            Path currentPath = Paths.get(currentValue);
            if (Files.exists(currentPath) && Files.isDirectory(currentPath)) {
                chooser.setInitialDirectory(currentPath.toFile());
            }
        }

        if (primaryStage != null) {
            java.io.File selected = chooser.showDialog(primaryStage);
            if (selected != null) {
                targetField.setText(selected.getAbsolutePath());
            }
        }
    }

    @FXML
    private void captureWindowDrag(MouseEvent event) {
        if (primaryStage == null) {
            return;
        }
        dragOffsetX = event.getScreenX() - primaryStage.getX();
        dragOffsetY = event.getScreenY() - primaryStage.getY();
    }

    @FXML
    private void dragWindow(MouseEvent event) {
        if (primaryStage == null) {
            return;
        }
        primaryStage.setX(event.getScreenX() - dragOffsetX);
        primaryStage.setY(event.getScreenY() - dragOffsetY);
    }

    @FXML
    private void minimizeWindow() {
        if (primaryStage != null) {
            primaryStage.setIconified(true);
        }
    }

    @FXML
    private void closeWindow() {
        if (primaryStage != null) {
            primaryStage.close();
        }
    }

    private void setBusy(boolean busy) {
        launchButton.setDisable(busy);
        syncButton.setDisable(busy);
        settingsButton.setDisable(busy);
        playNavButton.setDisable(busy);
        modsNavButton.setDisable(busy);
        newsActionButton.setDisable(busy);
    }

    private void updateProgressState(boolean busy, String statusText, String percentText, double progress) {
        syncProgressBar.setProgress(progress);
        syncStatusLabel.setText(statusText);
        syncPercentLabel.setText(percentText);
        if (busy) {
            syncBytesLabel.setText("Launcher is working...");
        }
    }

    private void updateServerPresence(String title, String hint, String tone) {
        realmPresenceLabel.setText(title);
        realmPresenceHintLabel.setText(hint);
        toggleStyleClass(realmPresenceIndicator, "presence-checking", "checking".equals(tone));
        toggleStyleClass(realmPresenceIndicator, "presence-online", "online".equals(tone));
        toggleStyleClass(realmPresenceIndicator, "presence-offline", "offline".equals(tone));
    }

    private void refreshServerPresenceAsync(String host, int port, String route) {
        updateServerPresence("CHECKING", "Probing " + route + ".", "checking");
        long requestId = serverPresenceSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            boolean online = false;
            String hint;

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1500);
                online = true;
                hint = "Route " + route + " is responding.";
            } catch (IOException exception) {
                hint = "No response from " + route + ": " + compact(exception.getMessage(), 64);
            }

            boolean resolvedOnline = online;
            String resolvedHint = hint;
            Platform.runLater(() -> {
                if (requestId != serverPresenceSequence.get()) {
                    return;
                }
                updateServerPresence(
                    resolvedOnline ? "ONLINE" : "OFFLINE",
                    resolvedHint,
                    resolvedOnline ? "online" : "offline"
                );
            });
        }, "launcher-shell-presence");

        thread.setDaemon(true);
        thread.start();
    }

    private void persistConfig(LauncherConfig config, boolean logPath) throws IOException {
        currentConfig = LauncherDefaults.applyMissingValues(config.copy());
        configStore.save(currentConfig);
        refreshSummary(currentConfig);
        if (logPath) {
            appendLog("Config saved: " + configStore.getConfigFile());
        }
    }

    private void persistCurrentConfigQuietly() {
        try {
            persistConfig(buildCurrentConfig(), false);
        } catch (Exception ignored) {
        }
    }

    private void appendLog(String message) {
        String resolvedMessage = message == null ? "" : message;
        logArea.appendText("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + resolvedMessage + System.lineSeparator());
        logArea.positionCaret(logArea.getLength());
        syncFileLabel.setText(compact(resolvedMessage, 96));
    }

    private void appendLogAsync(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void showError(String message) {
        String resolvedMessage = hasText(message) ? message : "Неизвестная ошибка.";
        appendLog("Error: " + resolvedMessage);
        setLogDrawerVisible(true);

        Alert alert = new Alert(Alert.AlertType.ERROR, resolvedMessage, ButtonType.OK);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }

    private void toggleLogDrawer() {
        setLogDrawerVisible(logDrawerBox == null || !logDrawerBox.isVisible());
    }

    private void setLogDrawerVisible(boolean visible) {
        setNodeVisible(logDrawerBox, visible);
        toggleLogButton.setText(visible ? "Hide log" : "Show log");
    }

    private void setActiveNav(Button activeButton) {
        updateNavState(homeNavButton, activeButton == homeNavButton);
        updateNavState(playNavButton, activeButton == playNavButton);
        updateNavState(modsNavButton, activeButton == modsNavButton);
        updateNavState(settingsButton, activeButton == settingsButton);
    }

    private void updateNavState(Button button, boolean active) {
        toggleStyleClass(button, "nav-active", active);
    }

    private void openPrimaryCommunityLink() {
        for (LauncherHomeContent.CommunityLink link : homeContent.getCommunity()) {
            if (hasText(link.getUrl())) {
                openCommunityLink(link);
                return;
            }
        }
        appendLog("News archive is not linked yet. Connect a community URL in launcher-home.json.");
    }

    private void openCommunityLink(LauncherHomeContent.CommunityLink link) {
        if (link == null || !hasText(link.getUrl())) {
            appendLog(
                "Community link is not configured: " + (link == null ? "community" : valueOrFallback(link.getLabel(), "community"))
            );
            return;
        }
        if (hostServices != null) {
            hostServices.showDocument(link.getUrl().trim());
        }
    }

    private void applyLabelGraphic(Label label, String resourcePath, double fitWidth, double fitHeight) {
        ImageView imageView = createImageView(resourcePath, fitWidth, fitHeight);
        if (imageView == null) {
            return;
        }
        label.setGraphic(imageView);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void applyButtonGraphic(Button button, String resourcePath, double size) {
        ImageView imageView = createImageView(resourcePath, size, size);
        if (imageView == null) {
            return;
        }
        button.setGraphic(imageView);
        button.setGraphicTextGap(12);
        button.setContentDisplay(ContentDisplay.LEFT);
    }

    private ImageView createImageView(String resourcePath, double fitWidth, double fitHeight) {
        java.net.URL resource = LauncherShellController.class.getResource(resourcePath);
        if (resource == null) {
            return null;
        }

        ImageView imageView = new ImageView(new Image(resource.toExternalForm(), false));
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);
        return imageView;
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

    private static String formatSyncSummary(ModpackSyncResult syncResult) {
        return syncResult.getDownloadedFiles()
            + " downloaded / "
            + syncResult.getReusedFiles()
            + " reused / "
            + formatMegabytes(syncResult.getDownloadedBytes());
    }

    private static String formatMegabytes(long bytes) {
        double megabytes = bytes / 1024.0d / 1024.0d;
        return String.format(java.util.Locale.US, "%.1f MB", Double.valueOf(megabytes));
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

    private static int parsePortOrDefault(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return LauncherConfig.DEFAULT_SERVER_PORT;
        }

        try {
            int port = Integer.parseInt(normalized);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Порт должен быть в диапазоне 1-65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Порт должен быть числом.");
        }
    }

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String displayFolderName(String value) {
        try {
            Path path = Paths.get(value).normalize();
            Path fileName = path.getFileName();
            return fileName == null ? compact(value, 28) : compact(fileName.toString(), 28);
        } catch (Exception exception) {
            return compact(value, 28);
        }
    }

    private static String compactCommunityLabel(String label) {
        String resolved = valueOrFallback(label, "LINK").toUpperCase();
        if (resolved.length() <= 2) {
            return resolved;
        }
        if ("DISCORD".equals(resolved)) {
            return "DS";
        }
        if ("TELEGRAM".equals(resolved)) {
            return "TG";
        }
        return resolved.substring(0, Math.min(2, resolved.length()));
    }

    private static String compact(String value, int maxLength) {
        String resolved = value == null ? "" : value;
        if (resolved.length() <= maxLength) {
            return resolved;
        }
        return resolved.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void setNodeVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setManaged(visible);
        node.setVisible(visible);
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

        private LauncherTaskResult(LauncherConfig resolvedConfig, ModpackSyncResult syncResult, Integer exitCode) {
            this.resolvedConfig = resolvedConfig;
            this.syncResult = syncResult;
            this.exitCode = exitCode;
        }

        private LauncherConfig getResolvedConfig() {
            return resolvedConfig;
        }

        private ModpackSyncResult getSyncResult() {
            return syncResult;
        }

        private Integer getExitCode() {
            return exitCode;
        }
    }
}
