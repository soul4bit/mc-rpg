package ru.mcrpg.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
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

    private final LauncherConfigStore configStore = LauncherConfigStore.defaultStore();
    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(new ModpackManifestClient());
    private final LauncherHomeContent homeContent = new LauncherHomeContentLoader().loadDefault();

    private Stage primaryStage;
    private HostServices hostServices;
    private LauncherConfig currentConfig = LauncherConfig.defaults();

    @FXML
    private StackPane appRoot;

    @FXML
    private VBox shell;

    @FXML
    private FlowPane bodyFlow;

    @FXML
    private VBox leftColumn;

    @FXML
    private FlowPane spotlightFlow;

    @FXML
    private FlowPane metaDeckFlow;

    @FXML
    private HBox communityBar;

    @FXML
    private VBox newsFeed;

    @FXML
    private VBox logDrawerBox;

    @FXML
    private HBox footerBar;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField gameDirectoryField;

    @FXML
    private CheckBox updateFilesBeforeLaunchCheckBox;

    @FXML
    private TextArea logArea;

    @FXML
    private Button launchButton;

    @FXML
    private Button syncButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button toggleLogButton;

    @FXML
    private Button browseGameDirectoryButton;

    @FXML
    private Label mastheadTitleLabel;

    @FXML
    private Label mastheadSubtitleLabel;

    @FXML
    private Label headerProfileLabel;

    @FXML
    private Label headerModeLabel;

    @FXML
    private Label heroFeatureBadge;

    @FXML
    private Label heroTitleLabel;

    @FXML
    private Label heroCopyLabel;

    @FXML
    private Label heroFootnoteLabel;

    @FXML
    private Label heroPlayerLabel;

    @FXML
    private Label heroInstallLabel;

    @FXML
    private Label heroModeLabel;

    @FXML
    private Label heroStateBadge;

    @FXML
    private Label playRouteLabel;

    @FXML
    private Label playStateLabel;

    @FXML
    private Label sessionModeLabel;

    @FXML
    private Label sessionRouteLabel;

    @FXML
    private Label sessionStateLabel;

    @FXML
    private Label dockPlayerLabel;

    @FXML
    private Label dockFolderLabel;

    @FXML
    private Label dockModeLabel;

    @FXML
    private Label configPathLabel;

    @FXML
    private Label supportNoteLabel;

    @FXML
    private void initialize() {
        bodyFlow.prefWrapLengthProperty().bind(shell.widthProperty().subtract(48));
        spotlightFlow.prefWrapLengthProperty().bind(leftColumn.widthProperty());
        metaDeckFlow.prefWrapLengthProperty().bind(leftColumn.widthProperty());

        styleMainControls();
        populateContent();
        bindActions();
        setLogDrawerVisible(false);
        configPathLabel.setText(compact(configStore.getConfigFile().toString(), 72));
        supportNoteLabel.setText(
            "Если сервер попросил авторизацию, используй /register <пароль> <пароль> при первом входе и /login <пароль> для повторного."
        );
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

    private void populateContent() {
        heroFeatureBadge.setText(valueOrFallback(homeContent.getHeroEyebrow(), "FEATURED"));
        heroTitleLabel.setText(valueOrFallback(homeContent.getHeroTitle(), LauncherBrand.APP_NAME));
        heroCopyLabel.setText(homeContent.getHeroDescription());
        heroFootnoteLabel.setText(homeContent.getHeroFootnote());

        communityBar.getChildren().clear();
        for (LauncherHomeContent.CommunityLink link : homeContent.getCommunity()) {
            communityBar.getChildren().add(createCommunityButton(link));
        }

        spotlightFlow.getChildren().clear();
        for (LauncherHomeContent.SpotlightCard card : homeContent.getSpotlight()) {
            spotlightFlow.getChildren().add(createSpotlightTile(card));
        }

        newsFeed.getChildren().clear();
        for (LauncherHomeContent.NewsEntry entry : homeContent.getNews()) {
            newsFeed.getChildren().add(createNewsEntry(entry));
        }
    }

    private void bindActions() {
        browseGameDirectoryButton.setOnAction(event -> chooseDirectory(gameDirectoryField, "Выбери папку клиента"));
        syncButton.setOnAction(event -> syncFiles());
        launchButton.setOnAction(event -> launchClient());
        settingsButton.setOnAction(event -> openSettingsDialog());
        toggleLogButton.setOnAction(event -> toggleLogDrawer());

        usernameField.textProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
        gameDirectoryField.textProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
        updateFilesBeforeLaunchCheckBox.selectedProperty().addListener(
            (observable, oldValue, newValue) -> refreshSummaryFromVisibleFields()
        );
    }

    private void installResponsiveBehavior(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveLayout(newValue.doubleValue()));
        applyResponsiveLayout(scene.getWidth());
    }

    private void applyResponsiveLayout(double width) {
        boolean compact = width < 1240;
        boolean condensed = width < 1040;

        toggleStyleClass(appRoot, "compact-mode", compact);
        toggleStyleClass(appRoot, "condensed-mode", condensed);
        setNodeVisible(footerBar, !condensed);
    }

    private void styleMainControls() {
        usernameField.getStyleClass().add("launcher-input");
        usernameField.setPromptText("Ник игрока");
        usernameField.setTooltip(new Tooltip("Ник для запуска Minecraft-клиента"));

        gameDirectoryField.getStyleClass().add("launcher-input");
        gameDirectoryField.setPromptText(LauncherDefaults.defaultGameDirectory());
        gameDirectoryField.setTooltip(new Tooltip("Папка для modpack, runtime и bootstrap"));

        updateFilesBeforeLaunchCheckBox.getStyleClass().add("launcher-check");

        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(true);
    }

    private Button createCommunityButton(LauncherHomeContent.CommunityLink link) {
        Button button = new Button(valueOrFallback(link.getLabel(), "LINK"));
        button.getStyleClass().addAll("community-button", "action-button", "ghost-action");
        button.setOnAction(event -> openCommunityLink(link));
        return button;
    }

    private Node createSpotlightTile(LauncherHomeContent.SpotlightCard spotlightCard) {
        VBox tile = new VBox(8);
        tile.getStyleClass().addAll("spotlight-card", "spotlight-" + valueOrFallback(spotlightCard.getAccent(), "fire"));
        tile.setPrefWidth(220);
        tile.setMinWidth(220);

        Label eyebrow = new Label(valueOrFallback(spotlightCard.getEyebrow(), "INFO"));
        eyebrow.getStyleClass().add("spotlight-eyebrow");

        Label title = new Label(spotlightCard.getTitle());
        title.getStyleClass().add("spotlight-title");
        title.setWrapText(true);

        Label copy = new Label(spotlightCard.getCopy());
        copy.getStyleClass().add("spotlight-copy");
        copy.setWrapText(true);

        tile.getChildren().addAll(eyebrow, title, copy);
        return tile;
    }

    private Node createNewsEntry(LauncherHomeContent.NewsEntry entry) {
        VBox box = new VBox(6);
        box.getStyleClass().add("news-entry");

        Label tag = new Label(valueOrFallback(entry.getTag(), "UPDATE"));
        tag.getStyleClass().add("news-tag");

        Label title = new Label(entry.getTitle());
        title.getStyleClass().add("news-title");
        title.setWrapText(true);

        Label copy = new Label(entry.getCopy());
        copy.getStyleClass().add("news-copy");
        copy.setWrapText(true);

        box.getChildren().addAll(tag, title, copy);
        return box;
    }

    private LauncherConfig loadConfig() {
        try {
            LauncherConfig loadedConfig = configStore.load();
            LauncherDefaults.applyMissingValues(loadedConfig);
            appendLog("Конфиг загружен из " + configStore.getConfigFile());
            return loadedConfig;
        } catch (IOException exception) {
            appendLog("Не удалось загрузить конфиг: " + exception.getMessage());
            return LauncherDefaults.applyMissingValues(LauncherConfig.defaults());
        }
    }

    private void applyConfigToView(LauncherConfig config) {
        usernameField.setText(config.getUsername());
        gameDirectoryField.setText(config.getGameDirectory());
        updateFilesBeforeLaunchCheckBox.setSelected(config.isUpdateFilesBeforeLaunch());
        refreshSummary(config);
    }

    private void refreshSummary(LauncherConfig config) {
        String username = valueOrFallback(config.getUsername(), LauncherDefaults.defaultUsername());
        String folderName = displayFolderName(valueOrFallback(config.getGameDirectory(), LauncherDefaults.defaultGameDirectory()));
        String mode = config.isUpdateFilesBeforeLaunch() ? "Автообновление" : "Ручной запуск";
        String route = valueOrFallback(config.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST) + ":" + config.getServerPort();

        mastheadTitleLabel.setText(LauncherBrand.APP_NAME);
        mastheadSubtitleLabel.setText("Основной сервер " + route + " | профиль " + username);

        headerProfileLabel.setText(username);
        headerModeLabel.setText(mode);

        heroPlayerLabel.setText("Forge 1.12.2");
        heroInstallLabel.setText(route);
        heroModeLabel.setText(mode);

        playRouteLabel.setText(route);
        dockPlayerLabel.setText(username);
        dockFolderLabel.setText(folderName);
        dockModeLabel.setText(mode);

        sessionModeLabel.setText(mode);
        sessionRouteLabel.setText(route);
    }

    private void refreshSummaryFromVisibleFields() {
        refreshSummary(buildConfigFromMainFields());
    }

    private LauncherConfig buildConfigFromMainFields() {
        LauncherConfig config = currentConfig.copy();
        config.setUsername(usernameField.getText().trim());
        config.setGameDirectory(gameDirectoryField.getText().trim());
        config.setUpdateFilesBeforeLaunch(updateFilesBeforeLaunchCheckBox.isSelected());
        return LauncherDefaults.applyMissingValues(config);
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            config = buildConfigFromMainFields();
            requireText(config.getManifestUrl(), "Укажи URL manifest.json.");
            persistConfig(config, false);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        appendLog("Запуск синхронизации файлов.");
        setLogDrawerVisible(true);
        runTask(LauncherAction.SYNC_ONLY, config);
    }

    private void launchClient() {
        LauncherConfig config;
        try {
            config = buildConfigFromMainFields();
            persistConfig(config, false);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        if (shouldSyncBeforeLaunch(config)) {
            appendLog("Перед запуском будет выполнена синхронизация файлов.");
        }

        setLogDrawerVisible(true);
        runTask(LauncherAction.SYNC_AND_LAUNCH, config);
    }

    private void runTask(LauncherAction action, LauncherConfig requestedConfig) {
        setBusy(true);
        updateStatusState(action == LauncherAction.SYNC_ONLY ? "Синхронизация" : "Запуск");

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

                    appendLogAsync("Запуск: " + commandBuilder.preview(command));
                    if (workingDirectory != null) {
                        appendLogAsync("Рабочая папка: " + workingDirectory.toAbsolutePath());
                    }

                    exitCode = Integer.valueOf(runProcess(command, workingDirectory));
                }

                return new LauncherTaskResult(effectiveConfig, syncResult, exitCode);
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            updateStatusState("Готово");
            LauncherTaskResult result = task.getValue();
            try {
                persistConfig(result.getResolvedConfig(), false);
                applyConfigToView(result.getResolvedConfig());
                if (result.getExitCode() != null) {
                    appendLog("Процесс завершился с кодом " + result.getExitCode() + ".");
                }
            } catch (IOException exception) {
                showError("Не удалось сохранить обновлённый конфиг: " + exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            updateStatusState("Внимание");
            Throwable exception = task.getException();
            showError(exception == null ? "Неизвестная ошибка." : exception.getMessage());
        });

        task.setOnCancelled(event -> {
            setBusy(false);
            updateStatusState("Ожидание");
        });

        Thread thread = new Thread(task, "launcher-shell-" + action.name().toLowerCase());
        thread.setDaemon(true);
        thread.start();
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
        LauncherConfig snapshot = buildConfigFromMainFields();

        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Технические настройки");

        TextField javaCommandField = new TextField(snapshot.getJavaCommand());
        TextField manifestUrlField = new TextField(snapshot.getManifestUrl());
        TextField workingDirectoryField = new TextField(snapshot.getWorkingDirectory());
        TextField serverHostField = new TextField(snapshot.getServerHost());
        TextField serverPortField = new TextField(Integer.toString(snapshot.getServerPort()));
        TextArea launchTemplateArea = new TextArea(snapshot.getLaunchTemplate());

        javaCommandField.getStyleClass().add("launcher-input");
        manifestUrlField.getStyleClass().add("launcher-input");
        workingDirectoryField.getStyleClass().add("launcher-input");
        serverHostField.getStyleClass().add("launcher-input");
        serverPortField.getStyleClass().add("launcher-input");
        launchTemplateArea.getStyleClass().add("launcher-textarea");
        launchTemplateArea.setWrapText(true);
        launchTemplateArea.setPrefRowCount(8);

        Button workingDirectoryButton = new Button("Обзор");
        workingDirectoryButton.getStyleClass().addAll("action-button", "ghost-action");
        workingDirectoryButton.setOnAction(event -> chooseDirectory(workingDirectoryField, "Выбери рабочую папку"));

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
                    javaCommandField,
                    manifestUrlField,
                    workingDirectoryField,
                    serverHostField,
                    serverPortField,
                    launchTemplateArea
                );
                appendLog("Команда: " + commandBuilder.preview(commandBuilder.build(config)));
            } catch (Exception exception) {
                showError(exception.getMessage());
            }
        });

        cancelButton.setOnAction(event -> dialog.close());
        applyButton.setOnAction(event -> {
            try {
                LauncherConfig updatedConfig = buildSettingsConfig(
                    snapshot,
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
        header.getStyleClass().addAll("surface-card", "dialog-header");
        Label title = new Label("Технические настройки");
        title.getStyleClass().add("card-title");
        Label copy = new Label(
            "Сюда вынесены поля, которые не нужны на главном экране: manifest URL, Java, маршрут к серверу и launch template."
        );
        copy.getStyleClass().add("card-description");
        copy.setWrapText(true);
        header.getChildren().addAll(title, copy);

        VBox content = new VBox(14);
        content.getChildren().addAll(
            createSettingsCard(
                "ENVIRONMENT",
                "Runtime и manifest",
                createFieldGroup("Java", "Команда запуска Java или путь к java.exe.", javaCommandField),
                createFieldGroup("Manifest URL", "HTTP(S)-адрес manifest.json.", manifestUrlField),
                createFieldGroup(
                    "Рабочая папка",
                    "Каталог, из которого запускается клиент. Может быть переопределён manifest.",
                    buildInlineField(workingDirectoryField, workingDirectoryButton)
                )
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
                createFieldGroup(
                    "Команда запуска",
                    "Итоговая строка будет собрана через launch template и текущий config.",
                    launchTemplateArea
                )
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
        TextField javaCommandField,
        TextField manifestUrlField,
        TextField workingDirectoryField,
        TextField serverHostField,
        TextField serverPortField,
        TextArea launchTemplateArea
    ) {
        LauncherConfig config = baseConfig.copy();
        config.setJavaCommand(javaCommandField.getText().trim());
        config.setManifestUrl(manifestUrlField.getText().trim());
        config.setWorkingDirectory(workingDirectoryField.getText().trim());
        config.setServerHost(serverHostField.getText().trim());
        config.setServerPort(parsePortOrDefault(serverPortField.getText()));
        config.setLaunchTemplate(launchTemplateArea.getText().trim());
        return LauncherDefaults.applyMissingValues(config);
    }

    private VBox createCard(String eyebrow, String title, String description) {
        VBox card = new VBox(14);
        card.getStyleClass().add("surface-card");
        card.setPadding(new Insets(20));

        Label eyebrowLabel = new Label(eyebrow);
        eyebrowLabel.getStyleClass().add("card-eyebrow");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("card-description");
        descriptionLabel.setWrapText(true);

        VBox header = new VBox(6, eyebrowLabel, titleLabel, descriptionLabel);
        card.getChildren().add(header);
        return card;
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

    private Node createSettingsCard(String eyebrow, String title, Node... content) {
        VBox card = createCard(eyebrow, title, "");
        card.getStyleClass().add("settings-card");
        if (!card.getChildren().isEmpty() && card.getChildren().get(0) instanceof VBox headerBox) {
            if (headerBox.getChildren().size() > 2) {
                headerBox.getChildren().remove(2);
            }
        }

        VBox body = new VBox(12);
        body.getChildren().addAll(content);
        card.getChildren().add(body);
        return card;
    }

    private Node buildInlineField(TextField field, Button button) {
        HBox row = new HBox(10, field, button);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
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

    private void updateStatusState(String state) {
        heroStateBadge.setText(state);
        playStateLabel.setText(state);
        sessionStateLabel.setText(state);
    }

    private void setBusy(boolean busy) {
        launchButton.setDisable(busy);
        syncButton.setDisable(busy);
        settingsButton.setDisable(busy);
        browseGameDirectoryButton.setDisable(busy);
        usernameField.setDisable(busy);
        gameDirectoryField.setDisable(busy);
        updateFilesBeforeLaunchCheckBox.setDisable(busy);
    }

    private void persistConfig(LauncherConfig config, boolean logPath) throws IOException {
        currentConfig = LauncherDefaults.applyMissingValues(config.copy());
        configStore.save(currentConfig);
        refreshSummary(currentConfig);
        if (logPath) {
            appendLog("Конфиг сохранён: " + configStore.getConfigFile());
        }
    }

    private void persistCurrentConfigQuietly() {
        try {
            persistConfig(buildConfigFromMainFields(), false);
        } catch (Exception ignored) {
        }
    }

    private void appendLog(String message) {
        logArea.appendText("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.positionCaret(logArea.getLength());
    }

    private void appendLogAsync(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void showError(String message) {
        String resolvedMessage = hasText(message) ? message : "Неизвестная ошибка.";
        updateStatusState("Внимание");
        setLogDrawerVisible(true);
        appendLog("Ошибка: " + resolvedMessage);

        Alert alert = new Alert(Alert.AlertType.ERROR, resolvedMessage, ButtonType.OK);
        alert.initOwner(primaryStage);
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }

    private void toggleLogDrawer() {
        setLogDrawerVisible(logDrawerBox == null || !logDrawerBox.isVisible());
    }

    private void setLogDrawerVisible(boolean visible) {
        setNodeVisible(logDrawerBox, visible);
        toggleLogButton.setText(visible ? "Скрыть лог" : "Открыть лог");
    }

    private void openCommunityLink(LauncherHomeContent.CommunityLink link) {
        if (link == null || !hasText(link.getUrl())) {
            appendLog("Ссылка сообщества пока не настроена: " + (link == null ? "community" : valueOrFallback(link.getLabel(), "community")));
            return;
        }
        if (hostServices != null) {
            hostServices.showDocument(link.getUrl().trim());
        }
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

    private static String compact(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
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

        @SuppressWarnings("unused")
        private ModpackSyncResult getSyncResult() {
            return syncResult;
        }

        private Integer getExitCode() {
            return exitCode;
        }
    }
}
