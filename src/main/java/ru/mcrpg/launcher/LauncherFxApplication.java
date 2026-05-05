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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.scene.layout.FlowPane;
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
    private final LauncherHomeContent homeContent = new LauncherHomeContentLoader().loadDefault();

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
    private final Button toggleLogButton = new Button("Открыть лог");
    private final Button browseGameDirectoryButton = new Button("Обзор");

    private StackPane appRoot;
    private ScrollPane shellScroll;
    private HBox shellRow;
    private VBox sidebarRail;
    private VBox contentShell;
    private FlowPane mainFlow;
    private FlowPane quickDeckFlow;
    private BorderPane heroLayout;
    private VBox heroActionPanel;
    private VBox logDrawerBox;
    private HBox footerBar;
    private VBox sessionCardBox;

    private final Label headerProfileLabel = new Label();
    private final Label headerModeLabel = new Label();
    private final Label heroPlayerLabel = new Label();
    private final Label heroInstallLabel = new Label();
    private final Label heroModeLabel = new Label();
    private final Label heroStateBadge = new Label("Готово");
    private final Label sidebarRouteLabel = new Label();
    private final Label sidebarStateLabel = new Label("Готово");
    private final Label dockPlayerLabel = new Label();
    private final Label dockFolderLabel = new Label();
    private final Label dockModeLabel = new Label();
    private final Label sessionModeLabel = new Label();
    private final Label sessionRouteLabel = new Label();
    private final Label sessionStateLabel = new Label("Готово");
    private final StringProperty mastheadTitle = new SimpleStringProperty(LauncherBrand.APP_NAME);
    private final StringProperty mastheadSubtitle = new SimpleStringProperty("Minecraft modpack launcher для быстрого входа на сервер");

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
        stage.setMinWidth(920);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> persistCurrentConfigQuietly());

        installBehavior();
        applyConfigToView(currentConfig);
        installResponsiveBehavior(scene);
        stage.show();
    }

    private Parent buildShell() {
        appRoot = new StackPane();
        appRoot.getStyleClass().add("app-root");

        shellRow = new HBox(18);
        shellRow.getStyleClass().add("shell");
        shellRow.setPadding(new Insets(18));
        shellRow.setAlignment(Pos.TOP_CENTER);

        contentShell = new VBox(16);
        contentShell.getStyleClass().add("content-shell");
        contentShell.setMaxWidth(1380);

        Node topBar = buildTopBar();
        Node main = buildMainPanel();
        VBox.setVgrow(main, Priority.ALWAYS);
        HBox.setHgrow(contentShell, Priority.ALWAYS);

        contentShell.getChildren().addAll(topBar, main);
        shellRow.getChildren().add(contentShell);

        shellScroll = new ScrollPane(shellRow);
        shellScroll.setFitToWidth(true);
        shellScroll.setFitToHeight(true);
        shellScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        shellScroll.getStyleClass().add("shell-scroll");

        appRoot.getChildren().add(shellScroll);
        return appRoot;
    }

    private Node buildSidebar() {
        sidebarRail = new VBox(20);
        sidebarRail.getStyleClass().add("sidebar-rail");
        sidebarRail.setPrefWidth(250);
        sidebarRail.setMinWidth(250);

        VBox brandBlock = new VBox(8);
        brandBlock.getStyleClass().add("sidebar-brand");
        Label overline = new Label("REDSTONE NETWORK");
        overline.getStyleClass().add("sidebar-overline");
        Label title = new Label(LauncherBrand.APP_TITLE.toUpperCase());
        title.getStyleClass().add("sidebar-title");
        Label copy = new Label("Minecraft mod launcher with one clean path from sync to play.");
        copy.getStyleClass().add("sidebar-copy");
        copy.setWrapText(true);
        brandBlock.getChildren().addAll(overline, title, copy);

        VBox nav = new VBox(8);
        nav.getStyleClass().add("sidebar-panel");
        nav.getChildren().addAll(
            createRailItem("PLAY DECK", "Активный экран запуска", true),
            createRailItem("INSTALL", "Каталог клиента и auto-sync", false),
            createRailItem("CONSOLE", "Live log и runtime output", false),
            createRailItem("SETTINGS", "Техпараметры отдельно", false)
        );

        VBox realmPanel = new VBox(10);
        realmPanel.getStyleClass().add("sidebar-panel");
        Label realmTitle = new Label("Realm status");
        realmTitle.getStyleClass().add("sidebar-panel-title");
        realmPanel.getChildren().addAll(
            realmTitle,
            createSidebarMetric("Route", sidebarRouteLabel),
            createSidebarMetric("State", sidebarStateLabel)
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox footer = new VBox(6);
        footer.getStyleClass().add("sidebar-footer");
        Label footerLabel = new Label("JavaFX Edition");
        footerLabel.getStyleClass().add("sidebar-footer-title");
        Label footerCopy = new Label("Forge 1.12.2 // one-server launcher shell");
        footerCopy.getStyleClass().add("sidebar-footer-copy");
        footer.getChildren().addAll(new Separator(), footerLabel, footerCopy);

        sidebarRail.getChildren().addAll(brandBlock, nav, realmPanel, spacer, footer);
        return sidebarRail;
    }

    private Node buildTopBar() {
        HBox bar = new HBox(18);
        bar.getStyleClass().addAll("surface-card", "masthead-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox branding = new VBox(6);
        Label overline = new Label("REDSTONE NETWORK");
        overline.getStyleClass().add("masthead-overline");
        Label brandTitle = new Label();
        brandTitle.textProperty().bind(mastheadTitle);
        brandTitle.getStyleClass().add("masthead-title");
        Label brandSubtitle = new Label();
        brandSubtitle.textProperty().bind(mastheadSubtitle);
        brandSubtitle.getStyleClass().add("masthead-subtitle");
        brandSubtitle.setWrapText(true);
        branding.getChildren().addAll(overline, brandTitle, brandSubtitle);

        HBox communityBar = new HBox(10);
        communityBar.getStyleClass().add("community-bar");
        for (LauncherHomeContent.CommunityLink link : homeContent.getCommunity()) {
            communityBar.getChildren().add(createCommunityButton(link));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox chips = new HBox(10);
        chips.getChildren().addAll(
            createTopChip("Профиль", headerProfileLabel),
            createTopChip("Режим", headerModeLabel)
        );

        bar.getChildren().addAll(branding, communityBar, spacer, chips);
        return bar;
    }

    private Node buildMainPanel() {
        mainFlow = new FlowPane(18, 18);
        mainFlow.getStyleClass().add("main-flow");
        mainFlow.setAlignment(Pos.TOP_LEFT);
        mainFlow.prefWrapLengthProperty().bind(contentShell.widthProperty().subtract(24));

        VBox leftColumn = new VBox(18);
        leftColumn.getStyleClass().add("showcase-column");
        VBox.setVgrow(leftColumn, Priority.ALWAYS);
        Node heroCard = buildHeroCard();
        Node quickDeck = buildQuickDeck();
        VBox.setVgrow(quickDeck, Priority.ALWAYS);
        leftColumn.getChildren().addAll(heroCard, quickDeck);
        leftColumn.setMinWidth(640);

        VBox rightColumn = new VBox(18);
        rightColumn.getStyleClass().add("activity-column");
        rightColumn.setPrefWidth(380);
        Node profileCard = buildProfileCard();
        Node logCard = buildLogCard();
        VBox.setVgrow(profileCard, Priority.NEVER);
        VBox.setVgrow(logCard, Priority.ALWAYS);
        rightColumn.getChildren().addAll(profileCard, logCard);
        rightColumn.setMinWidth(340);

        mainFlow.getChildren().addAll(leftColumn, rightColumn);
        return mainFlow;
    }

    private Node buildQuickDeck() {
        quickDeckFlow = new FlowPane(18, 18);
        quickDeckFlow.getStyleClass().add("quick-deck");
        quickDeckFlow.setAlignment(Pos.TOP_LEFT);
        quickDeckFlow.prefWrapLengthProperty().bind(mainFlow.widthProperty().subtract(24));

        Node helpCard = buildHelpCard();
        Node newsCard = buildNewsCard();
        if (helpCard instanceof Region) {
            ((Region) helpCard).setPrefWidth(440);
            ((Region) helpCard).setMinWidth(360);
        }
        if (newsCard instanceof Region) {
            ((Region) newsCard).setPrefWidth(440);
            ((Region) newsCard).setMinWidth(320);
        }

        quickDeckFlow.getChildren().addAll(helpCard, newsCard);
        return quickDeckFlow;
    }

    private Node buildHeroCard() {
        StackPane hero = new StackPane();
        hero.getStyleClass().add("hero-card");
        hero.setMinHeight(460);

        Pane accents = new Pane();
        accents.getStyleClass().add("hero-accents");
        accents.setMouseTransparent(true);

        heroLayout = new BorderPane();
        heroLayout.setPadding(new Insets(30));

        VBox content = new VBox(18);
        content.setAlignment(Pos.TOP_LEFT);

        HBox badges = new HBox(8);
        badges.getChildren().addAll(
            createBadge("MAIN SERVER", "gold-badge"),
            createBadge("FORGE 1.12.2", "accent-badge"),
            createBadge(homeContent.getHeroEyebrow(), "green-badge")
        );

        Label title = new Label(homeContent.getHeroTitle());
        title.getStyleClass().add("hero-title");
        Label copy = new Label(homeContent.getHeroDescription());
        copy.getStyleClass().add("hero-copy");
        copy.setWrapText(true);

        HBox stats = new HBox(12);
        stats.getStyleClass().add("hero-stats-row");
        stats.getChildren().addAll(
            createHeroStat("Версия", heroPlayerLabel),
            createHeroStat("Сервер", heroInstallLabel),
            createHeroStat("Режим", heroModeLabel)
        );

        Region stretch = new Region();
        VBox.setVgrow(stretch, Priority.ALWAYS);

        Label flow = new Label(homeContent.getHeroFootnote());
        flow.getStyleClass().add("hero-footnote");
        flow.setWrapText(true);

        content.getChildren().addAll(badges, title, copy, stats, stretch, flow);
        heroLayout.setCenter(content);
        heroLayout.setRight(null);
        heroActionPanel = null;

        hero.getChildren().addAll(accents, heroLayout);
        return hero;
    }

    private Node buildProfileCard() {
        VBox card = createCard(
            "PLAY",
            "Играть на сервере",
            "Компактная правая колонка как в серверных лаунчерах: профиль, запуск, синхронизация и доступ к логу."
        );
        card.getStyleClass().add("play-panel-card");

        HBox pathRow = new HBox(10);
        pathRow.getStyleClass().add("inline-field-row");
        HBox.setHgrow(gameDirectoryField, Priority.ALWAYS);
        browseGameDirectoryButton.getStyleClass().addAll("action-button", "secondary-action", "mini-action");
        pathRow.getChildren().addAll(gameDirectoryField, browseGameDirectoryButton);

        heroStateBadge.getStyleClass().add("status-chip");
        sidebarRouteLabel.getStyleClass().add("play-route-value");

        VBox actions = new VBox(10);
        actions.getStyleClass().add("play-actions");
        launchButton.getStyleClass().addAll("action-button", "primary-action", "hero-button");
        syncButton.getStyleClass().addAll("action-button", "secondary-action", "hero-button");
        settingsButton.getStyleClass().addAll("action-button", "utility-action", "hero-button");
        toggleLogButton.getStyleClass().addAll("action-button", "ghost-action", "hero-button");
        launchButton.setMaxWidth(Double.MAX_VALUE);
        syncButton.setMaxWidth(Double.MAX_VALUE);
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        toggleLogButton.setMaxWidth(Double.MAX_VALUE);
        actions.getChildren().addAll(launchButton, syncButton, toggleLogButton, settingsButton);

        VBox body = new VBox(14);
        body.getChildren().addAll(
            createPlayRouteBox(),
            createFieldGroup(
                "Ник в игре",
                "Имя профиля, под которым клиент зайдёт на сервер.",
                usernameField
            ),
            createFieldGroup(
                "Каталог сборки",
                "Здесь лежат runtime, моды, конфиги и все файлы клиента.",
                pathRow
            ),
            updateFilesBeforeLaunchCheckBox,
            heroStateBadge,
            actions,
            createInfoNote(
                "После входа",
                "Если сервер требует авторизацию, используй /register <пароль> <пароль> при первом входе и /login <пароль> при повторном."
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
            "Каталог клиента и режим обновления. Никаких raw URL и launch template на поверхности."
        );

        HBox pathRow = new HBox(10);
        HBox.setHgrow(gameDirectoryField, Priority.ALWAYS);
        browseGameDirectoryButton.getStyleClass().addAll("action-button", "secondary-action", "mini-action");
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

    private Node buildHelpCard() {
        VBox card = createCard(
            "SERVERS",
            "Карточки сервера",
            "Нижний блок уже ближе к классическим серверным лаунчерам: крупные карточки, а не форма параметров."
        );
        card.getStyleClass().addAll("wide-card", "showcase-card");

        FlowPane spotlightGrid = new FlowPane(12, 12);
        spotlightGrid.getStyleClass().add("spotlight-grid");
        spotlightGrid.setPrefWrapLength(740);
        for (LauncherHomeContent.SpotlightCard spotlightCard : homeContent.getSpotlight()) {
            spotlightGrid.getChildren().add(createSpotlightTile(spotlightCard));
        }

        VBox body = new VBox(14, spotlightGrid);
        VBox.setVgrow(body, Priority.ALWAYS);
        card.getChildren().add(body);
        return card;
    }

    private Node buildNewsCard() {
        VBox card = createCard(
            "NEWS",
            "Новости сервера",
            "Анонсы, заметки по сборке и короткие статусы, которые можно править через launcher-home.json."
        );
        card.getStyleClass().addAll("wide-card", "news-card");

        VBox feed = new VBox(10);
        feed.getStyleClass().add("news-feed");
        for (LauncherHomeContent.NewsEntry entry : homeContent.getNews()) {
            feed.getChildren().add(createNewsEntry(entry));
        }

        card.getChildren().add(feed);
        return card;
    }

    private Node buildSessionCard() {
        VBox card = createCard(
            "SESSION",
            "Краткая сессия",
            "Только текущий режим, маршрут и состояние лаунчера без дубля всех карточек."
        );
        card.getStyleClass().add("session-card");

        VBox stats = new VBox(10);
        stats.getChildren().addAll(
            createSessionRow("Режим", sessionModeLabel),
            createSessionRow("Маршрут", sessionRouteLabel),
            createSessionRow("Состояние", sessionStateLabel)
        );

        card.getChildren().add(stats);
        sessionCardBox = card;
        return card;
    }

    private Node buildLogCard() {
        VBox card = createCard(
            "LIVE",
            "Лог запуска",
            "Manifest, синхронизация файлов, вывод Minecraft и ошибки."
        );
        card.getStyleClass().add("log-card");
        card.setPrefWidth(350);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        card.getChildren().add(logArea);

        logDrawerBox = new VBox(card);
        logDrawerBox.getStyleClass().add("log-drawer");
        setLogDrawerVisible(false);
        return logDrawerBox;
    }

    private Node buildDockBar() {
        footerBar = new HBox(16);
        footerBar.getStyleClass().addAll("surface-card", "footer-bar");
        footerBar.setAlignment(Pos.CENTER_LEFT);

        HBox summary = new HBox(12);
        HBox.setHgrow(summary, Priority.ALWAYS);
        summary.getChildren().addAll(
            createDockItem("Игрок", dockPlayerLabel),
            createDockItem("Папка", dockFolderLabel),
            createDockItem("Режим", dockModeLabel)
        );

        VBox meta = new VBox(4);
        meta.getStyleClass().add("footer-meta");
        Label configTitle = new Label("CONFIG");
        configTitle.getStyleClass().add("dock-title");
        Label configValue = new Label(compact(configStore.getConfigFile().toString(), 48));
        configValue.getStyleClass().add("footer-config-value");
        meta.getChildren().addAll(configTitle, configValue);

        footerBar.getChildren().addAll(summary, meta);
        return footerBar;
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

    private Node createCommunityButton(LauncherHomeContent.CommunityLink link) {
        Button button = new Button(valueOrFallback(link.getLabel(), "LINK"));
        button.getStyleClass().addAll("social-chip", "action-button");
        button.setOnAction(event -> openCommunityLink(link));
        return button;
    }

    private Node createRailItem(String title, String copy, boolean active) {
        VBox item = new VBox(3);
        item.getStyleClass().add(active ? "rail-item-active" : "rail-item");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("rail-item-title");
        Label copyLabel = new Label(copy);
        copyLabel.getStyleClass().add("rail-item-copy");
        copyLabel.setWrapText(true);
        item.getChildren().addAll(titleLabel, copyLabel);
        return item;
    }

    private Node createSidebarMetric(String title, Label valueLabel) {
        VBox box = new VBox(4);
        box.getStyleClass().add("sidebar-metric");
        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("sidebar-metric-title");
        valueLabel.getStyleClass().add("sidebar-metric-value");
        valueLabel.setWrapText(true);
        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
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

    private Node createSessionRow(String title, Label valueLabel) {
        HBox row = new HBox(10);
        row.getStyleClass().add("session-row");
        row.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("session-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        valueLabel.getStyleClass().add("session-value");
        valueLabel.setWrapText(true);
        row.getChildren().addAll(titleLabel, spacer, valueLabel);
        return row;
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

    private Node createChecklistRow(String text) {
        HBox row = new HBox(8);
        row.getStyleClass().add("checklist-row");
        Label marker = new Label("+");
        marker.getStyleClass().add("checklist-marker");
        Label copy = new Label(text);
        copy.getStyleClass().add("checklist-copy");
        copy.setWrapText(true);
        row.getChildren().addAll(marker, copy);
        return row;
    }

    private Node createSpotlightTile(LauncherHomeContent.SpotlightCard spotlightCard) {
        VBox tile = new VBox(10);
        tile.getStyleClass().addAll("spotlight-tile", "spotlight-" + valueOrFallback(spotlightCard.getAccent(), "fire"));
        tile.setPrefWidth(210);
        tile.setMinWidth(190);

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

    private Node createPlayRouteBox() {
        VBox box = new VBox(6);
        box.getStyleClass().add("play-route-box");
        Label title = new Label("Сервер");
        title.getStyleClass().add("play-route-title");
        sidebarRouteLabel.setWrapText(true);
        box.getChildren().addAll(title, sidebarRouteLabel);
        return box;
    }

    private void installBehavior() {
        styleMainControls();
        browseGameDirectoryButton.setOnAction(event -> chooseDirectory(gameDirectoryField, "Выбери папку клиента"));
        syncButton.setOnAction(event -> syncFiles());
        launchButton.setOnAction(event -> launchClient());
        settingsButton.setOnAction(event -> openSettingsDialog());
        toggleLogButton.setOnAction(event -> toggleLogDrawer());

        usernameField.textProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
        gameDirectoryField.textProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
        updateFilesBeforeLaunchCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> refreshSummaryFromVisibleFields());
    }

    private void installResponsiveBehavior(Scene scene) {
        scene.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveLayout(newValue.doubleValue()));
        applyResponsiveLayout(scene.getWidth());
    }

    private void applyResponsiveLayout(double width) {
        boolean compact = width < 1280;
        boolean condensed = width < 1080;

        setNodeVisible(sidebarRail, !compact);
        setNodeVisible(sessionCardBox, !condensed);
        setNodeVisible(footerBar, !condensed);

        if (compact) {
            heroLayout.setRight(null);
            heroLayout.setBottom(heroActionPanel);
            BorderPane.setMargin(heroActionPanel, new Insets(18, 0, 0, 0));
        } else {
            heroLayout.setBottom(null);
            heroLayout.setRight(heroActionPanel);
            BorderPane.setMargin(heroActionPanel, Insets.EMPTY);
        }

        toggleStyleClass(appRoot, "compact-mode", compact);
        toggleStyleClass(appRoot, "condensed-mode", condensed);
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

    private void styleMainControls() {
        usernameField.getStyleClass().add("launcher-input");
        usernameField.setPromptText("Ник игрока");
        usernameField.setTooltip(new Tooltip("Ник для запуска клиента"));
        gameDirectoryField.getStyleClass().add("launcher-input");
        gameDirectoryField.setPromptText(LauncherDefaults.defaultGameDirectory());
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
        String mode = config.isUpdateFilesBeforeLaunch() ? "Автообновление" : "Ручной запуск";
        String route = valueOrFallback(config.getServerHost(), LauncherConfig.DEFAULT_SERVER_HOST) + ":" + config.getServerPort();

        headerProfileLabel.setText(username);
        headerModeLabel.setText(mode);

        heroPlayerLabel.setText("Forge 1.12.2");
        heroInstallLabel.setText(route);
        heroModeLabel.setText(mode);
        sidebarRouteLabel.setText(route);

        dockPlayerLabel.setText(username);
        dockFolderLabel.setText(folderName);
        dockModeLabel.setText(mode);
        sessionModeLabel.setText(mode);
        sessionRouteLabel.setText(route);

        mastheadTitle.set(LauncherBrand.APP_NAME);
        mastheadSubtitle.set("Основной сервер " + route + " | Профиль " + username);
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
            updateStatusState("Готово");
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
            updateStatusState("Внимание");
            Throwable exception = task.getException();
            showError(exception == null ? "Неизвестная ошибка." : exception.getMessage());
        });

        task.setOnCancelled(event -> {
            setBusy(false);
            updateStatusState("Ожидание");
        });

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
        usernameField.setDisable(busy);
        gameDirectoryField.setDisable(busy);
        updateFilesBeforeLaunchCheckBox.setDisable(busy);
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
        updateStatusState("Внимание");
        setLogDrawerVisible(true);
        appendLog("Ошибка: " + resolvedMessage);

        Alert alert = new Alert(Alert.AlertType.ERROR, resolvedMessage, ButtonType.OK);
        alert.initOwner(primaryStage);
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }

    private void updateStatusState(String state) {
        heroStateBadge.setText(state);
        sidebarStateLabel.setText(state);
        sessionStateLabel.setText(state);
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
        getHostServices().showDocument(link.getUrl().trim());
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
