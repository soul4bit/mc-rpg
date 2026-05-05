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
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.concurrent.Task;

public final class LauncherFxApplication extends Application {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LauncherConfigStore configStore = LauncherConfigStore.defaultStore();
    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(new ModpackManifestClient());

    private Stage primaryStage;
    private LauncherConfig currentConfig = LauncherConfig.defaults();

    private final TextField usernameField = new TextField();
    private final TextField gameDirectoryField = new TextField();
    private final CheckBox updateFilesBeforeLaunchCheckBox =
        new CheckBox("Автоматически обновлять сборку перед запуском");
    private final TextArea logArea = new TextArea();

    private final Button launchButton = new Button("Играть");
    private final Button syncButton = new Button("Синхронизация");
    private final Button settingsButton = new Button("Настройки");
    private final Button browseGameDirectoryButton = new Button("Обзор");

    private final Label headerProfileLabel = new Label();
    private final Label headerModeLabel = new Label();
    private final Label heroPlayerLabel = new Label();
    private final Label heroInstallLabel = new Label();
    private final Label heroRouteLabel = new Label();
    private final Label dockPlayerLabel = new Label();
    private final Label dockFolderLabel = new Label();
    private final Label dockModeLabel = new Label();

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        currentConfig = loadConfig();

        Scene scene = new Scene(buildShell(), 1340, 900);
        scene.getStylesheets().add(
            LauncherFxApplication.class.getResource("/ru/mcrpg/launcher/launcher.css").toExternalForm()
        );

        stage.setTitle(LauncherBrand.APP_NAME);
        stage.setMinWidth(1180);
        stage.setMinHeight(820);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> persistCurrentConfigQuietly());

        installBehavior();
        applyConfigToView(currentConfig);
        stage.show();
    }

    private Parent buildShell() {
        StackPane root = new StackPane();
        root.getStyleClass().add("app-root");

        VBox shell = new VBox(16);
        shell.getStyleClass().add("shell");
        shell.setPadding(new Insets(18));

        Node topBar = buildTopBar();
        Node main = buildMainPanel();
        Node dock = buildDockBar();
        VBox.setVgrow(main, Priority.ALWAYS);

        shell.getChildren().addAll(topBar, main, dock);
        root.getChildren().add(shell);
        return root;
    }

    private Node buildTopBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().addAll("surface-card", "top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox branding = new VBox(4);
        Label brandTitle = new Label(LauncherBrand.APP_TITLE.toUpperCase());
        brandTitle.getStyleClass().add("brand-title");
        Label brandSubtitle = new Label(LauncherBrand.APP_SUBTITLE);
        brandSubtitle.getStyleClass().add("brand-subtitle");
        branding.getChildren().addAll(brandTitle, brandSubtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox chips = new HBox(10);
        chips.getChildren().addAll(
            createTopChip("Profile", headerProfileLabel),
            createTopChip("Mode", headerModeLabel)
        );

        bar.getChildren().addAll(branding, spacer, chips);
        return bar;
    }

    private Node buildMainPanel() {
        HBox main = new HBox(16);
        main.setFillHeight(true);

        VBox showcase = new VBox(14);
        HBox.setHgrow(showcase, Priority.ALWAYS);
        VBox.setVgrow(showcase, Priority.ALWAYS);

        Label sectionTitle = new Label("PLAY REDSTONE");
        sectionTitle.getStyleClass().add("section-title");
        Label sectionSubtitle = new Label(
            "JavaFX позволяет перейти от формы настроек к нормальной витрине лаунчера: герой, профиль, установка и live console."
        );
        sectionSubtitle.getStyleClass().add("section-subtitle");
        sectionSubtitle.setWrapText(true);

        HBox showcaseGrid = new HBox(16);
        VBox.setVgrow(showcaseGrid, Priority.ALWAYS);

        VBox sideStack = new VBox(14);
        sideStack.setPrefWidth(360);
        sideStack.getChildren().addAll(buildProfileCard(), buildInstallCard());
        VBox.setVgrow(sideStack.getChildren().get(0), Priority.ALWAYS);
        VBox.setVgrow(sideStack.getChildren().get(1), Priority.ALWAYS);

        Node heroCard = buildHeroCard();
        HBox.setHgrow(heroCard, Priority.ALWAYS);

        showcaseGrid.getChildren().addAll(heroCard, sideStack);
        showcase.getChildren().addAll(sectionTitle, sectionSubtitle, showcaseGrid);

        Node logCard = buildLogCard();
        HBox.setHgrow(logCard, Priority.NEVER);

        main.getChildren().addAll(showcase, logCard);
        return main;
    }

    private Node buildHeroCard() {
        StackPane hero = new StackPane();
        hero.getStyleClass().add("hero-card");
        hero.setMinHeight(520);

        Pane accents = new Pane();
        accents.getStyleClass().add("hero-accents");
        accents.setMouseTransparent(true);

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(28));

        VBox content = new VBox(18);
        content.setAlignment(Pos.TOP_LEFT);

        HBox badges = new HBox(8);
        badges.getChildren().addAll(
            createBadge("FORGE 1.12.2", "gold-badge"),
            createBadge("MAIN SERVER", "accent-badge"),
            createBadge("READY", "green-badge")
        );

        Label title = new Label("Redstone Realm");
        title.getStyleClass().add("hero-title");
        Label copy = new Label(
            "Один главный мир, единый modpack-профиль и быстрый вход без ручной возни с runtime, forge и библиотеками."
        );
        copy.getStyleClass().add("hero-copy");
        copy.setWrapText(true);

        HBox stats = new HBox(12);
        stats.getChildren().addAll(
            createHeroStat("Игрок", heroPlayerLabel),
            createHeroStat("Установка", heroInstallLabel),
            createHeroStat("Маршрут", heroRouteLabel)
        );

        Region stretch = new Region();
        VBox.setVgrow(stretch, Priority.ALWAYS);

        Label flow = new Label(
            "Поток запуска: синхронизация файлов, затем старт Minecraft. Низкоуровневые параметры убраны в отдельные настройки."
        );
        flow.getStyleClass().add("hero-footnote");
        flow.setWrapText(true);

        content.getChildren().addAll(badges, title, copy, stats, stretch, flow);
        layout.setCenter(content);

        hero.getChildren().addAll(accents, layout);
        return hero;
    }

    private Node buildProfileCard() {
        VBox card = createCard(
            "PROFILE",
            "Игровой профиль",
            "На главном экране остаются только ник и подсказки для первого входа."
        );

        VBox body = new VBox(14);
        body.getChildren().addAll(
            createFieldGroup(
                "Ник Minecraft",
                "Имя профиля, которое уйдёт прямо в запуск клиента.",
                usernameField
            ),
            createInfoNote(
                "Первый вход",
                "Если сервер требует авторизацию, используй /register <пароль> <пароль> при первом входе и /login <пароль> для повторного."
            ),
            createInfoNote(
                "Журнал справа",
                "Console показывает этапы синхронизации, manifest, stdout клиента и подсказки по авторизации."
            )
        );

        VBox.setVgrow(body, Priority.ALWAYS);
        card.getChildren().add(body);
        return card;
    }

    private Node buildInstallCard() {
        VBox card = createCard(
            "INSTALL",
            "Modpack install",
            "Папка клиента и режим автообновления, без лишнего технического шума."
        );

        HBox pathRow = new HBox(10);
        HBox.setHgrow(gameDirectoryField, Priority.ALWAYS);
        browseGameDirectoryButton.getStyleClass().addAll("action-button", "secondary-action");
        pathRow.getChildren().addAll(gameDirectoryField, browseGameDirectoryButton);

        VBox body = new VBox(14);
        body.getChildren().addAll(
            createFieldGroup(
                "Каталог сборки",
                "Сюда Redstone складывает runtime, моды, конфиги и bootstrap Minecraft.",
                pathRow
            ),
            updateFilesBeforeLaunchCheckBox,
            createInfoNote(
                "Как работает sync",
                "Лаунчер сверит manifest, скачает недостающие файлы и только потом откроет Minecraft."
            )
        );

        VBox.setVgrow(body, Priority.ALWAYS);
        card.getChildren().add(body);
        return card;
    }

    private Node buildLogCard() {
        VBox card = createCard(
            "CONSOLE",
            "Живой журнал",
            "События синхронизации, stdout клиента и любые ошибки запуска."
        );
        card.getStyleClass().add("log-card");
        card.setPrefWidth(390);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        card.getChildren().add(logArea);
        return card;
    }

    private Node buildDockBar() {
        HBox dock = new HBox(14);
        dock.getStyleClass().addAll("surface-card", "dock-bar");
        dock.setAlignment(Pos.CENTER_LEFT);

        HBox summary = new HBox(12);
        HBox.setHgrow(summary, Priority.ALWAYS);
        summary.getChildren().addAll(
            createDockItem("Игрок", dockPlayerLabel),
            createDockItem("Папка", dockFolderLabel),
            createDockItem("Режим", dockModeLabel)
        );

        launchButton.getStyleClass().addAll("action-button", "primary-action");
        syncButton.getStyleClass().addAll("action-button", "secondary-action");
        settingsButton.getStyleClass().addAll("action-button", "utility-action");

        HBox actions = new HBox(10, syncButton, settingsButton, launchButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        dock.getChildren().addAll(summary, actions);
        return dock;
    }

    private VBox createCard(String eyebrow, String title, String description) {
        VBox card = new VBox(16);
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

    private Node createTopChip(String title, Label valueLabel) {
        VBox chip = new VBox(4);
        chip.getStyleClass().add("top-chip");

        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("chip-title");
        valueLabel.getStyleClass().add("chip-value");

        chip.getChildren().addAll(titleLabel, valueLabel);
        return chip;
    }

    private Node createBadge(String text, String extraClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("hero-badge", extraClass);
        return label;
    }

    private Node createHeroStat(String title, Label valueLabel) {
        VBox stat = new VBox(6);
        stat.getStyleClass().add("hero-stat");

        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("stat-title");
        valueLabel.getStyleClass().add("stat-value");
        valueLabel.setWrapText(true);

        stat.getChildren().addAll(titleLabel, valueLabel);
        return stat;
    }

    private Node createDockItem(String title, Label valueLabel) {
        VBox item = new VBox(5);
        item.getStyleClass().add("dock-item");

        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("dock-title");
        valueLabel.getStyleClass().add("dock-value");

        item.getChildren().addAll(titleLabel, valueLabel);
        return item;
    }

    private VBox createFieldGroup(String title, String hint, Node field) {
        VBox group = new VBox(8);
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

    private void installBehavior() {
        styleMainControls();
        browseGameDirectoryButton.setOnAction(event -> chooseDirectory(gameDirectoryField, "Выбери папку клиента"));
        syncButton.setOnAction(event -> syncFiles());
        launchButton.setOnAction(event -> launchClient());
        settingsButton.setOnAction(event -> openSettingsDialog());

        usernameField.textProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
        gameDirectoryField.textProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
        updateFilesBeforeLaunchCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
    }

    private void styleMainControls() {
        usernameField.getStyleClass().add("launcher-input");
        usernameField.setTooltip(new Tooltip("Ник для запуска клиента"));
        gameDirectoryField.getStyleClass().add("launcher-input");
        gameDirectoryField.setTooltip(new Tooltip("Куда будет установлена сборка"));
        updateFilesBeforeLaunchCheckBox.getStyleClass().add("launcher-check");
    }

    private LauncherConfig loadConfig() {
        try {
            LauncherConfig loadedConfig = configStore.load();
            LauncherDefaults.applyMissingValues(loadedConfig);
            appendLog("Конфиг загружен из " + configStore.getConfigFile());
            return loadedConfig;
        } catch (IOException exception) {
            appendLog("Не удалось загрузить конфиг: " + exception.getMessage());
            return LauncherConfig.defaults();
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
        String mode = config.isUpdateFilesBeforeLaunch() ? "Auto-sync" : "Manual";
        String route = valueOrFallback(config.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST) + ":" + config.getServerPort();

        headerProfileLabel.setText(username);
        headerModeLabel.setText(mode);

        heroPlayerLabel.setText(username);
        heroInstallLabel.setText(folderName);
        heroRouteLabel.setText(route);

        dockPlayerLabel.setText(username);
        dockFolderLabel.setText(folderName);
        dockModeLabel.setText(mode);
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

        runTask(LauncherAction.SYNC_AND_LAUNCH, config);
    }

    private void runTask(LauncherAction action, LauncherConfig requestedConfig) {
        setBusy(true);

        Task<LauncherTaskResult> task = new Task<LauncherTaskResult>() {
            @Override
            protected LauncherTaskResult call() throws Exception {
                LauncherConfig effectiveConfig = requestedConfig.copy();
                ModpackSyncResult syncResult = null;

                if (action == LauncherAction.SYNC_ONLY || shouldSyncBeforeLaunch(effectiveConfig)) {
                    syncResult = modpackSyncService.sync(effectiveConfig, LauncherFxApplication.this::appendLogAsync);
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
            LauncherTaskResult result = task.getValue();
            try {
                persistConfig(result.getResolvedConfig(), false);
                applyConfigToView(result.getResolvedConfig());
                if (result.getExitCode() != null) {
                    appendLog("Процесс завершился с кодом " + result.getExitCode() + ".");
                }
            } catch (IOException exception) {
                showError("Не удалось сохранить обновленный конфиг: " + exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            showError(exception == null ? "Неизвестная ошибка." : exception.getMessage());
        });

        task.setOnCancelled(event -> setBusy(false));

        Thread thread = new Thread(task, "launcher-" + action.name().toLowerCase());
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
        workingDirectoryButton.getStyleClass().addAll("action-button", "secondary-action");
        workingDirectoryButton.setOnAction(event -> chooseDirectory(workingDirectoryField, "Выбери рабочую папку"));

        Button previewButton = new Button("Проверить команду");
        Button cancelButton = new Button("Отмена");
        Button applyButton = new Button("Применить");
        previewButton.getStyleClass().addAll("action-button", "secondary-action");
        cancelButton.getStyleClass().addAll("action-button", "utility-action");
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
            "Сюда вынесены raw-поля, которые не нужны обычному игроку: manifest URL, Java, маршрут к серверу и launch template."
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

        Label configPath = new Label("Профиль: " + configStore.getConfigFile());
        configPath.getStyleClass().add("dialog-path");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBar = new HBox(10, configPath, spacer, cancelButton, previewButton, applyButton);
        buttonBar.getStyleClass().add("settings-actions");
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(header, scrollPane, buttonBar);

        Scene scene = new Scene(root, 980, 780);
        scene.getStylesheets().add(
            LauncherFxApplication.class.getResource("/ru/mcrpg/launcher/launcher.css").toExternalForm()
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

    private Node createSettingsCard(String eyebrow, String title, Node... content) {
        VBox card = createCard(eyebrow, title, "");
        card.getStyleClass().add("settings-card");
        if (!card.getChildren().isEmpty()) {
            Node header = card.getChildren().get(0);
            if (header instanceof VBox) {
                VBox headerBox = (VBox) header;
                if (headerBox.getChildren().size() > 2) {
                    headerBox.getChildren().remove(2);
                }
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

    private void setBusy(boolean busy) {
        launchButton.setDisable(busy);
        syncButton.setDisable(busy);
        settingsButton.setDisable(busy);
        browseGameDirectoryButton.setDisable(busy);
    }

    private void persistConfig(LauncherConfig config, boolean logPath) throws IOException {
        currentConfig = LauncherDefaults.applyMissingValues(config.copy());
        configStore.save(currentConfig);
        refreshSummary(currentConfig);
        if (logPath) {
            appendLog("Конфиг сохранен: " + configStore.getConfigFile());
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
        appendLog("Ошибка: " + resolvedMessage);

        Alert alert = new Alert(Alert.AlertType.ERROR, resolvedMessage, ButtonType.OK);
        alert.initOwner(primaryStage);
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
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
