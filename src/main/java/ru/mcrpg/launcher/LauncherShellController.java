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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
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
import javafx.stage.Stage;

public final class LauncherShellController {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String BUILD_LABEL = "1.20.4";
    private static final String SERVER_TITLE = "Survival RPG";
    private static final String SERVER_ROUTE = "play.redstone.net";
    private static final String SERVER_PLAYERS = "1,258 / 2,000";
    private static final String MODPACK_VERSION = "2.5.0";
    private static final String TOTAL_MODS_LABEL = "87";
    private static final String ENABLED_MODS_LABEL = "55 enabled";
    private static final String DISABLED_MODS_LABEL = "32 disabled";
    private static final String ACCOUNT_RANK = "Legendary";
    private static final String ACCOUNT_LEVEL = "56";
    private static final String ACCOUNT_XP = "24,850 / 28,000 XP";
    private static final String ACCOUNT_PLAYTIME = "184h";
    private static final String ACCOUNT_BALANCE = "12,450";
    private static final String ACCOUNT_VOTE_POINTS = "3,215";
    private static final String DEFAULT_NEWS_TITLE = "v2.5.0 - Realm Awakening";
    private static final String DEFAULT_NEWS_COPY =
        "New level cap, custom dungeons, skills overhaul, and the exciting new Gauntlet system!";
    private static final String DEFAULT_NEWS_DATE = "May 18, 2025";

    private static final String ASSET_ROOT = "/ru/mcrpg/launcher/redstone/";
    private static final String LOGO_ASSET = ASSET_ROOT + "logo/redstone_logo_full_transparent.png";
    private static final String HOME_ICON_ASSET = ASSET_ROOT + "ui_icons/home_red.png";
    private static final String PLAY_ICON_ACTIVE_ASSET = ASSET_ROOT + "ui_icons/play_red.png";
    private static final String PLAY_ICON_INACTIVE_ASSET = ASSET_ROOT + "ui_icons/play_gray.png";
    private static final String MODS_ICON_ACTIVE_ASSET = ASSET_ROOT + "ui_icons/mods_red.png";
    private static final String MODS_ICON_INACTIVE_ASSET = ASSET_ROOT + "ui_icons/mods_gray.png";
    private static final String SETTINGS_ICON_ACTIVE_ASSET = ASSET_ROOT + "ui_icons/settings_red.png";
    private static final String SETTINGS_ICON_INACTIVE_ASSET = ASSET_ROOT + "ui_icons/settings_gray.png";
    private static final String LOGIN_ICON_ASSET = ASSET_ROOT + "ui_icons/login_red.png";
    private static final String REGISTER_ICON_ASSET = ASSET_ROOT + "ui_icons/register_red.png";
    private static final String ACCOUNT_ICON_ASSET = ASSET_ROOT + "ui_icons/account_red.png";
    private static final String DISCORD_ICON_ASSET = ASSET_ROOT + "ui_icons/social_discord_gray.png";
    private static final String GLOBE_ICON_ASSET = ASSET_ROOT + "ui_icons/utility_globe_gray.png";
    private static final String LIST_ICON_ASSET = ASSET_ROOT + "ui_icons/utility_list_gray.png";
    private static final String BUTTON_PLAY_ICON_ASSET = ASSET_ROOT + "ui_icons/play_gray.png";
    private static final String MOD_ICON_JOURNEYMAP = ASSET_ROOT + "mod_icons/journeymap.png";
    private static final String MOD_ICON_JEI = ASSET_ROOT + "mod_icons/jei.png";
    private static final String MOD_ICON_SODIUM = ASSET_ROOT + "mod_icons/sodium.png";
    private static final String MOD_ICON_XAERO = ASSET_ROOT + "mod_icons/xaero_minimap.png";
    private static final String MOD_ICON_VOICE = ASSET_ROOT + "mod_icons/voice_chat.png";

    private final LauncherConfigStore configStore = LauncherConfigStore.defaultStore();
    private final LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder();
    private final ModpackSyncService modpackSyncService = new ModpackSyncService(new ModpackManifestClient());
    private final LauncherHomeContent homeContent = new LauncherHomeContentLoader().loadDefault();
    private final AtomicLong serverPresenceSequence = new AtomicLong();
    private final List<ModEntry> modEntries = List.of(
        new ModEntry("JourneyMap", "Real-time mapping in-game or your browser as you explore.", "5.10.3", MOD_ICON_JOURNEYMAP, true, ModFilter.INSTALLED, false),
        new ModEntry("JEI", "View recipes and item usages.", "15.20.0.105", MOD_ICON_JEI, true, ModFilter.INSTALLED, false),
        new ModEntry("Sodium", "A modern rendering engine improvement mod for Minecraft.", "0.5.8", MOD_ICON_SODIUM, true, ModFilter.UPDATES, true),
        new ModEntry("Xaero Minimap", "Displays a map of the nearby area.", "24.3.3", MOD_ICON_XAERO, false, ModFilter.OPTIONAL, false),
        new ModEntry("Voice Chat", "Simple and universal voice chat.", "2.5.22", MOD_ICON_VOICE, false, ModFilter.UPDATES, true)
    );

    private Stage primaryStage;
    private HostServices hostServices;
    private LauncherConfig currentConfig = LauncherConfig.defaults();
    private ViewState activeView = ViewState.HOME;
    private ModFilter activeModFilter = ModFilter.INSTALLED;
    private boolean loggedIn = true;
    private String lastPresenceRoute = "";
    private double dragOffsetX;
    private double dragOffsetY;

    @FXML
    private StackPane appRoot;

    @FXML
    private HBox shell;

    @FXML
    private Label brandLogoLabel;

    @FXML
    private Label brandSubtitleLabel;

    @FXML
    private Button homeNavButton;

    @FXML
    private Button playNavButton;

    @FXML
    private Button modsNavButton;

    @FXML
    private Button settingsNavButton;

    @FXML
    private Button sessionNavButton;

    @FXML
    private Button discordUtilityButton;

    @FXML
    private Button globeUtilityButton;

    @FXML
    private Button logUtilityButton;

    @FXML
    private VBox homeView;

    @FXML
    private VBox playView;

    @FXML
    private javafx.scene.control.ScrollPane modsView;

    @FXML
    private javafx.scene.control.ScrollPane settingsView;

    @FXML
    private StackPane loginView;

    @FXML
    private StackPane registerView;

    @FXML
    private javafx.scene.control.ScrollPane accountView;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private Label homeHeroLogoLabel;

    @FXML
    private Button homeLaunchButton;

    @FXML
    private Region homePresenceIndicator;

    @FXML
    private Label homePresenceLabel;

    @FXML
    private Label homeRouteLabel;

    @FXML
    private Label homeVersionLabel;

    @FXML
    private Label homePlayersLabel;

    @FXML
    private Label homePresenceHintLabel;

    @FXML
    private Label homeNewsTitleLabel;

    @FXML
    private Label homeNewsCopyLabel;

    @FXML
    private Label homeNewsDateLabel;

    @FXML
    private Button homeNewsActionButton;

    @FXML
    private Button homeUpdateButton;

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
    private Label playHeroLogoLabel;

    @FXML
    private Button playLaunchButton;

    @FXML
    private Label playAccountNameLabel;

    @FXML
    private Label playAccountStatusLabel;

    @FXML
    private Label playServerTitleLabel;

    @FXML
    private Label playServerRouteLabel;

    @FXML
    private Label playServerVersionLabel;

    @FXML
    private Label playServerPlayersLabel;

    @FXML
    private Label playServerPingLabel;

    @FXML
    private ComboBox<String> playVersionComboBox;

    @FXML
    private TextField playGameDirectoryField;

    @FXML
    private Button openGameDirectoryButton;

    @FXML
    private Button repairClientButton;

    @FXML
    private Label playUpdateDateLabel;

    @FXML
    private Label playUpdateTitleLabel;

    @FXML
    private Label playUpdateCopyLabel;

    @FXML
    private Button playChangelogButton;

    @FXML
    private TextField modsSearchField;

    @FXML
    private Button installedFilterButton;

    @FXML
    private Button optionalFilterButton;

    @FXML
    private Button updatesFilterButton;

    @FXML
    private VBox modsListBox;

    @FXML
    private Label modsVersionLabel;

    @FXML
    private Label modsTotalLabel;

    @FXML
    private Label modsEnabledLabel;

    @FXML
    private Label modsDisabledLabel;

    @FXML
    private Button updatePackButton;

    @FXML
    private Button openModsFolderButton;

    @FXML
    private ComboBox<String> settingsVersionComboBox;

    @FXML
    private Slider settingsRamSlider;

    @FXML
    private Label settingsRamLabel;

    @FXML
    private TextField settingsJavaPathField;

    @FXML
    private Button browseJavaPathButton;

    @FXML
    private ComboBox<String> settingsResolutionComboBox;

    @FXML
    private CheckBox settingsFullscreenToggle;

    @FXML
    private CheckBox settingsAutoUpdateToggle;

    @FXML
    private TextField settingsDownloadPathField;

    @FXML
    private Button browseDownloadPathButton;

    @FXML
    private ComboBox<String> settingsLanguageComboBox;

    @FXML
    private CheckBox settingsStartOnBootToggle;

    @FXML
    private Label settingsAccountNameLabel;

    @FXML
    private Label settingsAccountStatusLabel;

    @FXML
    private Button settingsLogoutButton;

    @FXML
    private Button settingsManageAccountButton;

    @FXML
    private TextField loginUsernameField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private CheckBox loginRememberCheckBox;

    @FXML
    private Button loginSignInButton;

    @FXML
    private Button loginCreateAccountButton;

    @FXML
    private Button loginGuestButton;

    @FXML
    private TextField registerUsernameField;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField registerConfirmPasswordField;

    @FXML
    private CheckBox registerAgreementCheckBox;

    @FXML
    private Button registerCreateButton;

    @FXML
    private Button registerBackButton;

    @FXML
    private Label accountNameLabel;

    @FXML
    private Label accountStatusLabel;

    @FXML
    private Label accountEmailLabel;

    @FXML
    private Label accountRankLabel;

    @FXML
    private Label accountProfileNicknameLabel;

    @FXML
    private Label accountProfileRankLabel;

    @FXML
    private Label accountProfileLevelLabel;

    @FXML
    private Label accountProfileExperienceLabel;

    @FXML
    private Label accountProfileCountryLabel;

    @FXML
    private Label accountProfileLanguageLabel;

    @FXML
    private Label accountSecurityPasswordLabel;

    @FXML
    private Label accountSecurityEmailLabel;

    @FXML
    private Label accountSecurityTwoFactorLabel;

    @FXML
    private Label accountSecurityBackupLabel;

    @FXML
    private Label accountSecurityRecoveryLabel;

    @FXML
    private Label accountStatsLevelLabel;

    @FXML
    private Label accountStatsPlaytimeLabel;

    @FXML
    private Label accountStatsBalanceLabel;

    @FXML
    private Label accountStatsVotePointsLabel;

    @FXML
    private Button accountEditProfileButton;

    @FXML
    private Button accountChangeSkinButton;

    @FXML
    private Button accountSecurityButton;

    @FXML
    private Button accountMoreStatsButton;

    @FXML
    private Button accountLogoutButton;

    @FXML
    private VBox logDrawerBox;

    @FXML
    private TextArea logArea;

    @FXML
    private void initialize() {
        configureControls();
        populateStaticContent();
        populateChoiceBoxes();
        populateNewsContent();
        populateAccountCards();
        populateModSummary();
        renderModsList();
        bindActions();
        updateProgressState(false, "Ready to verify launcher files.", "0%", 0.0d);
        syncBytesLabel.setText("Client state is idle.");
        syncFileLabel.setText("Press Update to sync the pack or Play to launch.");
        setLogDrawerVisible(false);
        showView(ViewState.HOME);
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

    private void configureControls() {
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        playGameDirectoryField.setEditable(false);
        settingsRamSlider.valueProperty().addListener((observable, oldValue, newValue) -> updateRamLabel());
        modsSearchField.textProperty().addListener((observable, oldValue, newValue) -> renderModsList());
    }

    private void populateStaticContent() {
        brandSubtitleLabel.setText("RPG SERVER");

        applyLabelGraphic(brandLogoLabel, LOGO_ASSET, 236, 168);
        applyLabelGraphic(homeHeroLogoLabel, LOGO_ASSET, 590, 228);
        applyLabelGraphic(playHeroLogoLabel, LOGO_ASSET, 590, 228);

        applyGraphicButton(homeLaunchButton, BUTTON_PLAY_ICON_ASSET, 26);
        applyGraphicButton(playLaunchButton, BUTTON_PLAY_ICON_ASSET, 26);
        applyIconOnlyButton(discordUtilityButton, DISCORD_ICON_ASSET, 26);
        applyIconOnlyButton(globeUtilityButton, GLOBE_ICON_ASSET, 26);
        applyIconOnlyButton(logUtilityButton, LIST_ICON_ASSET, 22);

        playServerTitleLabel.setText(SERVER_TITLE);
        playServerVersionLabel.setText(BUILD_LABEL);
        playServerPlayersLabel.setText(SERVER_PLAYERS);

        modsVersionLabel.setText(MODPACK_VERSION);
        modsTotalLabel.setText(TOTAL_MODS_LABEL);
        modsEnabledLabel.setText(ENABLED_MODS_LABEL);
        modsDisabledLabel.setText(DISABLED_MODS_LABEL);
    }

    private void populateChoiceBoxes() {
        playVersionComboBox.getItems().setAll(BUILD_LABEL);
        playVersionComboBox.setValue(BUILD_LABEL);

        settingsVersionComboBox.getItems().setAll(BUILD_LABEL, "1.12.2");
        settingsVersionComboBox.setValue(BUILD_LABEL);

        settingsResolutionComboBox.getItems().setAll(
            "1920 x 1080 (16:9)",
            "1600 x 900 (16:9)",
            "1280 x 720 (16:9)"
        );
        settingsResolutionComboBox.setValue("1920 x 1080 (16:9)");

        settingsLanguageComboBox.getItems().setAll("English", "Russian");
        settingsLanguageComboBox.setValue("English");

        settingsFullscreenToggle.setSelected(true);
        settingsStartOnBootToggle.setSelected(false);
        settingsRamSlider.setValue(6.0d);
        updateRamLabel();
    }

    private void populateNewsContent() {
        LauncherHomeContent.NewsEntry homeNews = homeContent.getNews().isEmpty() ? null : homeContent.getNews().get(0);
        LauncherHomeContent.NewsEntry updateNews = homeContent.getNews().size() > 1 ? homeContent.getNews().get(1) : homeNews;

        homeNewsTitleLabel.setText(valueOrFallback(homeNews == null ? "" : homeNews.getTitle(), DEFAULT_NEWS_TITLE));
        homeNewsCopyLabel.setText(valueOrFallback(homeNews == null ? "" : homeNews.getCopy(), DEFAULT_NEWS_COPY));
        homeNewsDateLabel.setText(DEFAULT_NEWS_DATE);

        playUpdateTitleLabel.setText(valueOrFallback(updateNews == null ? "" : updateNews.getTitle(), DEFAULT_NEWS_TITLE));
        playUpdateCopyLabel.setText(valueOrFallback(updateNews == null ? "" : updateNews.getCopy(), DEFAULT_NEWS_COPY));
        playUpdateDateLabel.setText(DEFAULT_NEWS_DATE);
    }

    private void populateAccountCards() {
        accountProfileRankLabel.setText(ACCOUNT_RANK);
        accountRankLabel.setText(ACCOUNT_RANK);
        accountProfileLevelLabel.setText(ACCOUNT_LEVEL);
        accountProfileExperienceLabel.setText(ACCOUNT_XP);
        accountProfileCountryLabel.setText("United States");
        accountProfileLanguageLabel.setText("English");
        accountSecurityPasswordLabel.setText("********");
        accountSecurityTwoFactorLabel.setText("Enabled");
        accountSecurityBackupLabel.setText("5 codes remaining");
        accountSecurityRecoveryLabel.setText("dragon.recovery@redstone.net");
        accountStatsLevelLabel.setText(ACCOUNT_LEVEL);
        accountStatsPlaytimeLabel.setText(ACCOUNT_PLAYTIME);
        accountStatsBalanceLabel.setText(ACCOUNT_BALANCE);
        accountStatsVotePointsLabel.setText(ACCOUNT_VOTE_POINTS);
    }

    private void populateModSummary() {
        installedFilterButton.getStyleClass().add("segment-button-active");
    }

    private void bindActions() {
        homeNavButton.setOnAction(event -> navigateTo(ViewState.HOME));
        playNavButton.setOnAction(event -> navigateTo(ViewState.PLAY));
        modsNavButton.setOnAction(event -> navigateTo(ViewState.MODS));
        settingsNavButton.setOnAction(event -> navigateTo(ViewState.SETTINGS));
        sessionNavButton.setOnAction(event -> navigateTo(activeView));

        discordUtilityButton.setOnAction(event -> openCommunityLinkByKeyword("discord"));
        globeUtilityButton.setOnAction(event -> openPrimaryCommunityLink());
        logUtilityButton.setOnAction(event -> toggleLogDrawer());

        homeLaunchButton.setOnAction(event -> launchClient());
        playLaunchButton.setOnAction(event -> launchClient());
        homeUpdateButton.setOnAction(event -> syncFiles());
        repairClientButton.setOnAction(event -> syncFiles());
        updatePackButton.setOnAction(event -> syncFiles());

        homeNewsActionButton.setOnAction(event -> openPrimaryCommunityLink());
        playChangelogButton.setOnAction(event -> openPrimaryCommunityLink());

        openGameDirectoryButton.setOnAction(event -> openDirectory(currentConfig.getGameDirectory()));
        openModsFolderButton.setOnAction(event -> openDirectory(Paths.get(currentConfig.getGameDirectory(), "mods").toString()));

        browseDownloadPathButton.setOnAction(event -> chooseDirectory(settingsDownloadPathField, "Choose game directory"));
        browseJavaPathButton.setOnAction(event -> chooseDirectory(settingsJavaPathField, "Choose Java directory"));

        installedFilterButton.setOnAction(event -> selectModFilter(ModFilter.INSTALLED));
        optionalFilterButton.setOnAction(event -> selectModFilter(ModFilter.OPTIONAL));
        updatesFilterButton.setOnAction(event -> selectModFilter(ModFilter.UPDATES));

        settingsManageAccountButton.setOnAction(event -> {
            loggedIn = true;
            navigateTo(ViewState.ACCOUNT);
        });
        settingsLogoutButton.setOnAction(event -> logoutToLogin());

        loginSignInButton.setOnAction(event -> handleLogin());
        loginCreateAccountButton.setOnAction(event -> showView(ViewState.REGISTER));
        loginGuestButton.setOnAction(event -> handleGuestMode());

        registerCreateButton.setOnAction(event -> handleRegister());
        registerBackButton.setOnAction(event -> showView(ViewState.LOGIN));

        accountEditProfileButton.setOnAction(event -> appendLog("Profile editor is not connected yet."));
        accountChangeSkinButton.setOnAction(event -> appendLog("Skin management is not connected yet."));
        accountSecurityButton.setOnAction(event -> appendLog("Security settings are not connected yet."));
        accountMoreStatsButton.setOnAction(event -> openPrimaryCommunityLink());
        accountLogoutButton.setOnAction(event -> logoutToLogin());
    }

    private void navigateTo(ViewState view) {
        syncDraftConfigToState();
        showView(view);
    }

    private void showView(ViewState view) {
        activeView = view;
        setNodeVisible(homeView, view == ViewState.HOME);
        setNodeVisible(playView, view == ViewState.PLAY);
        setNodeVisible(modsView, view == ViewState.MODS);
        setNodeVisible(settingsView, view == ViewState.SETTINGS);
        setNodeVisible(loginView, view == ViewState.LOGIN);
        setNodeVisible(registerView, view == ViewState.REGISTER);
        setNodeVisible(accountView, view == ViewState.ACCOUNT);
        updateNavVisuals();
    }

    private void updateNavVisuals() {
        updateNavButton(homeNavButton, activeView == ViewState.HOME, HOME_ICON_ASSET, HOME_ICON_ASSET);
        updateNavButton(playNavButton, activeView == ViewState.PLAY, PLAY_ICON_ACTIVE_ASSET, PLAY_ICON_INACTIVE_ASSET);
        updateNavButton(modsNavButton, activeView == ViewState.MODS, MODS_ICON_ACTIVE_ASSET, MODS_ICON_INACTIVE_ASSET);
        updateNavButton(
            settingsNavButton,
            activeView == ViewState.SETTINGS,
            SETTINGS_ICON_ACTIVE_ASSET,
            SETTINGS_ICON_INACTIVE_ASSET
        );

        if (activeView == ViewState.LOGIN) {
            sessionNavButton.setText("Login");
            updateNavButton(sessionNavButton, true, LOGIN_ICON_ASSET, LOGIN_ICON_ASSET);
            setNodeVisible(sessionNavButton, true);
        } else if (activeView == ViewState.REGISTER) {
            sessionNavButton.setText("Register");
            updateNavButton(sessionNavButton, true, REGISTER_ICON_ASSET, REGISTER_ICON_ASSET);
            setNodeVisible(sessionNavButton, true);
        } else if (activeView == ViewState.ACCOUNT) {
            sessionNavButton.setText("Account");
            updateNavButton(sessionNavButton, true, ACCOUNT_ICON_ASSET, ACCOUNT_ICON_ASSET);
            setNodeVisible(sessionNavButton, true);
        } else {
            setNodeVisible(sessionNavButton, false);
        }
    }

    private void updateNavButton(Button button, boolean active, String activeAsset, String inactiveAsset) {
        toggleStyleClass(button, "nav-active", active);
        ImageView imageView = createImageView(active ? activeAsset : inactiveAsset, 19, 19);
        if (imageView != null && !active && activeAsset.equals(inactiveAsset)) {
            imageView.setOpacity(0.48d);
        }
        button.setGraphic(imageView);
        button.setGraphicTextGap(14);
        button.setContentDisplay(ContentDisplay.LEFT);
    }

    private void selectModFilter(ModFilter filter) {
        activeModFilter = filter;
        toggleStyleClass(installedFilterButton, "segment-button-active", filter == ModFilter.INSTALLED);
        toggleStyleClass(optionalFilterButton, "segment-button-active", filter == ModFilter.OPTIONAL);
        toggleStyleClass(updatesFilterButton, "segment-button-active", filter == ModFilter.UPDATES);
        renderModsList();
    }

    private void renderModsList() {
        if (modsListBox == null) {
            return;
        }

        String query = modsSearchField == null ? "" : modsSearchField.getText().trim().toLowerCase(Locale.ROOT);
        modsListBox.getChildren().clear();

        boolean matched = false;
        for (ModEntry entry : modEntries) {
            if (!matchesFilter(entry)) {
                continue;
            }
            if (hasText(query) && !entry.matches(query)) {
                continue;
            }
            modsListBox.getChildren().add(createModRow(entry));
            matched = true;
        }

        if (!matched) {
            Label emptyLabel = new Label("No mods match the current filter.");
            emptyLabel.getStyleClass().add("empty-list-copy");
            VBox box = new VBox(emptyLabel);
            box.getStyleClass().add("mods-empty-state");
            modsListBox.getChildren().add(box);
        }
    }

    private boolean matchesFilter(ModEntry entry) {
        if (activeModFilter == ModFilter.INSTALLED) {
            return entry.filter == ModFilter.INSTALLED || entry.enabled;
        }
        if (activeModFilter == ModFilter.OPTIONAL) {
            return entry.filter == ModFilter.OPTIONAL || !entry.enabled;
        }
        return entry.updatable;
    }

    private HBox createModRow(ModEntry entry) {
        HBox row = new HBox(22);
        row.getStyleClass().add("mod-row");

        ImageView icon = createImageView(entry.iconPath, 82, 82);
        VBox details = new VBox(8);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label nameLabel = new Label(entry.name);
        nameLabel.getStyleClass().add("mod-name-label");

        Label copyLabel = new Label(entry.description);
        copyLabel.getStyleClass().add("mod-copy-label");
        copyLabel.setWrapText(true);

        details.getChildren().addAll(nameLabel, copyLabel);

        Label versionLabel = new Label(entry.version);
        versionLabel.getStyleClass().add("mod-version-label");
        versionLabel.setMinWidth(92);

        CheckBox toggle = new CheckBox();
        toggle.getStyleClass().add("switch-toggle");
        toggle.setSelected(entry.enabled);
        toggle.selectedProperty().addListener((observable, oldValue, newValue) -> entry.enabled = newValue.booleanValue());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(icon, details, spacer, versionLabel, toggle);
        return row;
    }

    private void handleLogin() {
        String username = valueOrFallback(loginUsernameField.getText(), LauncherDefaults.defaultUsername());
        currentConfig.setUsername(username);
        loggedIn = true;
        applyConfigToView(currentConfig);
        appendLog("Signed in as " + username + ".");
        showView(ViewState.HOME);
    }

    private void handleGuestMode() {
        currentConfig.setUsername("Guest");
        loggedIn = false;
        applyConfigToView(currentConfig);
        appendLog("Continuing in guest mode.");
        showView(ViewState.HOME);
    }

    private void handleRegister() {
        String username = valueOrFallback(registerUsernameField.getText(), LauncherDefaults.defaultUsername());
        currentConfig.setUsername(username);
        loggedIn = true;
        applyConfigToView(currentConfig);
        appendLog("Account created for " + username + ".");
        showView(ViewState.ACCOUNT);
    }

    private void logoutToLogin() {
        loggedIn = false;
        appendLog("Signed out from launcher account state.");
        showView(ViewState.LOGIN);
    }

    private void installResponsiveBehavior(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveLayout(newValue.doubleValue()));
        applyResponsiveLayout(scene.getWidth());
    }

    private void applyResponsiveLayout(double width) {
        toggleStyleClass(appRoot, "compact-mode", width < 1600);
        toggleStyleClass(appRoot, "narrow-mode", width < 1450);
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
        String username = valueOrFallback(config.getUsername(), LauncherDefaults.defaultUsername());
        String gameDirectory = valueOrFallback(config.getGameDirectory(), LauncherDefaults.defaultGameDirectory());
        String host = valueOrFallback(config.getServerHost(), SERVER_ROUTE);
        String route = host + ":" + config.getServerPort();
        String email = username.toLowerCase(Locale.ROOT) + "@redstone.net";
        String accountStatus = loggedIn ? "Online" : "Guest";

        homeRouteLabel.setText(host);
        homeVersionLabel.setText(BUILD_LABEL);
        homePlayersLabel.setText(SERVER_PLAYERS);

        playServerRouteLabel.setText(host);
        playServerVersionLabel.setText(BUILD_LABEL);
        playServerPlayersLabel.setText(SERVER_PLAYERS);

        playAccountNameLabel.setText(username);
        playAccountStatusLabel.setText(accountStatus);

        settingsAccountNameLabel.setText(username);
        settingsAccountStatusLabel.setText(accountStatus);

        accountNameLabel.setText(username);
        accountStatusLabel.setText(accountStatus);
        accountEmailLabel.setText(email);
        accountProfileNicknameLabel.setText(username);
        accountSecurityEmailLabel.setText(email);

        playGameDirectoryField.setText(displayFolderName(gameDirectory));
        settingsDownloadPathField.setText(gameDirectory);
        settingsJavaPathField.setText(valueOrFallback(config.getJavaCommand(), "java"));
        settingsAutoUpdateToggle.setSelected(config.isUpdateFilesBeforeLaunch());
        playVersionComboBox.setValue(BUILD_LABEL);
        settingsVersionComboBox.setValue(BUILD_LABEL);

        if (!hasText(loginUsernameField.getText())) {
            loginUsernameField.setText(username);
        }
        if (!hasText(registerUsernameField.getText())) {
            registerUsernameField.setText(username);
        }
        if (!hasText(registerEmailField.getText())) {
            registerEmailField.setText(email);
        }

        if (!route.equals(lastPresenceRoute)) {
            lastPresenceRoute = route;
            refreshServerPresenceAsync(host, config.getServerPort(), route);
        }
    }

    private void syncDraftConfigToState() {
        currentConfig = buildCurrentConfig();
        applyConfigToView(currentConfig);
    }

    private LauncherConfig buildCurrentConfig() {
        LauncherConfig draft = LauncherDefaults.applyMissingValues(currentConfig.copy());
        draft.setGameDirectory(valueOrFallback(settingsDownloadPathField.getText(), draft.getGameDirectory()));
        draft.setJavaCommand(valueOrFallback(settingsJavaPathField.getText(), draft.getJavaCommand()));
        draft.setUpdateFilesBeforeLaunch(settingsAutoUpdateToggle.isSelected());
        return LauncherDefaults.applyMissingValues(draft);
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            syncDraftConfigToState();
            config = buildCurrentConfig();
            requireText(config.getManifestUrl(), "Set manifest URL in the launcher config before syncing.");
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
            syncDraftConfigToState();
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
            action == LauncherAction.SYNC_ONLY ? "Checking manifest and pack files" : "Preparing launcher runtime",
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
                persistConfig(result.resolvedConfig, false);
                applyConfigToView(result.resolvedConfig);

                if (result.syncResult != null) {
                    applySyncResult(result.syncResult);
                } else if (result.exitCode != null) {
                    updateProgressState(false, "Game session finished", "READY", 0.0d);
                    syncBytesLabel.setText("Exit code " + result.exitCode);
                } else {
                    updateProgressState(false, "Ready", "READY", 0.0d);
                }

                if (result.exitCode != null) {
                    appendLog("Client process exited with code " + result.exitCode + ".");
                }
            } catch (IOException exception) {
                showError("Failed to save refreshed launcher config: " + exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable exception = task.getException();
            updateProgressState(false, "Operation failed", "ERR", 0.0d);
            syncBytesLabel.setText("Review launcher log for details.");
            showError(exception == null ? "Unknown launcher error." : exception.getMessage());
        });

        task.setOnCancelled(event -> {
            setBusy(false);
            updateProgressState(false, "Operation cancelled", "STOP", 0.0d);
            syncBytesLabel.setText("No files changed.");
        });

        Thread thread = new Thread(task, "launcher-shell-" + action.name().toLowerCase(Locale.ROOT));
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

    private void setBusy(boolean busy) {
        homeLaunchButton.setDisable(busy);
        playLaunchButton.setDisable(busy);
        homeUpdateButton.setDisable(busy);
        repairClientButton.setDisable(busy);
        updatePackButton.setDisable(busy);
        homeNavButton.setDisable(busy);
        playNavButton.setDisable(busy);
        modsNavButton.setDisable(busy);
        settingsNavButton.setDisable(busy);
        sessionNavButton.setDisable(busy);
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
        homePresenceLabel.setText(title);
        homePresenceHintLabel.setText(hint);
        toggleStyleClass(homePresenceIndicator, "presence-checking", "checking".equals(tone));
        toggleStyleClass(homePresenceIndicator, "presence-online", "online".equals(tone));
        toggleStyleClass(homePresenceIndicator, "presence-offline", "offline".equals(tone));
    }

    private void refreshServerPresenceAsync(String host, int port, String route) {
        updateServerPresence("CHECKING", "Probing " + route + ".", "checking");
        long requestId = serverPresenceSequence.incrementAndGet();

        Thread thread = new Thread(() -> {
            boolean online = false;
            long pingMs = -1L;
            String hint;

            try (Socket socket = new Socket()) {
                long started = System.nanoTime();
                socket.connect(new InetSocketAddress(host, port), 1500);
                pingMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
                online = true;
                hint = "Route " + route + " is responding.";
            } catch (IOException exception) {
                hint = "No response from " + route + ": " + compact(exception.getMessage(), 64);
            }

            boolean resolvedOnline = online;
            long resolvedPing = pingMs;
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
                playServerPingLabel.setText(resolvedOnline ? resolvedPing + " ms" : "--");
            });
        }, "launcher-shell-presence");

        thread.setDaemon(true);
        thread.start();
    }

    private void persistConfig(LauncherConfig config, boolean logPath) throws IOException {
        currentConfig = LauncherDefaults.applyMissingValues(config.copy());
        configStore.save(currentConfig);
        applyConfigToView(currentConfig);
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
        if (syncFileLabel != null) {
            syncFileLabel.setText(compact(resolvedMessage, 96));
        }
    }

    private void appendLogAsync(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void showError(String message) {
        String resolvedMessage = hasText(message) ? message : "Unknown launcher error.";
        appendLog("Error: " + resolvedMessage);
        setLogDrawerVisible(true);

        Alert alert = new Alert(Alert.AlertType.ERROR, resolvedMessage, ButtonType.OK);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Launcher Error");
        alert.showAndWait();
    }

    private void toggleLogDrawer() {
        setLogDrawerVisible(logDrawerBox == null || !logDrawerBox.isVisible());
    }

    private void setLogDrawerVisible(boolean visible) {
        setNodeVisible(logDrawerBox, visible);
    }

    private void openPrimaryCommunityLink() {
        for (LauncherHomeContent.CommunityLink link : homeContent.getCommunity()) {
            if (hasText(link.getUrl())) {
                openCommunityLink(link);
                return;
            }
        }
        appendLog("No community URL is configured in launcher-home.json.");
    }

    private void openCommunityLinkByKeyword(String keyword) {
        for (LauncherHomeContent.CommunityLink link : homeContent.getCommunity()) {
            if (hasText(link.getLabel()) && link.getLabel().toLowerCase(Locale.ROOT).contains(keyword)) {
                openCommunityLink(link);
                return;
            }
        }
        openPrimaryCommunityLink();
    }

    private void openCommunityLink(LauncherHomeContent.CommunityLink link) {
        if (link == null || !hasText(link.getUrl())) {
            appendLog("Community link is not configured yet.");
            return;
        }
        if (hostServices != null) {
            hostServices.showDocument(link.getUrl().trim());
        }
    }

    private void openDirectory(String rawPath) {
        if (!hasText(rawPath)) {
            appendLog("Directory is not configured.");
            return;
        }

        try {
            Path path = Paths.get(rawPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                appendLog("Directory does not exist yet: " + path);
                return;
            }
            new ProcessBuilder("explorer.exe", path.toString()).start();
        } catch (Exception exception) {
            showError("Failed to open directory: " + exception.getMessage());
        }
    }

    private void updateRamLabel() {
        long gigabytes = Math.round(settingsRamSlider.getValue());
        settingsRamLabel.setText(gigabytes + " GB / 8 GB");
    }

    private void chooseDirectory(TextField targetField, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);

        String currentValue = targetField.getText().trim();
        if (!currentValue.isEmpty()) {
            try {
                Path currentPath = Paths.get(currentValue);
                Path initial = Files.isDirectory(currentPath) ? currentPath : currentPath.getParent();
                if (initial != null && Files.exists(initial) && Files.isDirectory(initial)) {
                    chooser.setInitialDirectory(initial.toFile());
                }
            } catch (Exception ignored) {
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

    private void applyLabelGraphic(Label label, String resourcePath, double fitWidth, double fitHeight) {
        ImageView imageView = createImageView(resourcePath, fitWidth, fitHeight);
        if (imageView == null) {
            return;
        }
        label.setGraphic(imageView);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void applyGraphicButton(Button button, String resourcePath, double size) {
        ImageView imageView = createImageView(resourcePath, size, size);
        if (imageView == null) {
            return;
        }
        button.setGraphic(imageView);
        button.setGraphicTextGap(18);
        button.setContentDisplay(ContentDisplay.LEFT);
    }

    private void applyIconOnlyButton(Button button, String resourcePath, double size) {
        ImageView imageView = createImageView(resourcePath, size, size);
        if (imageView == null) {
            return;
        }
        button.setGraphic(imageView);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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

    private static String formatSyncSummary(ModpackSyncResult syncResult) {
        return syncResult.getDownloadedFiles()
            + " downloaded / "
            + syncResult.getReusedFiles()
            + " reused / "
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

    private static String displayFolderName(String value) {
        try {
            Path path = Paths.get(value).normalize();
            Path fileName = path.getFileName();
            return fileName == null ? compact(value, 32) : compact(fileName.toString(), 32);
        } catch (Exception exception) {
            return compact(value, 32);
        }
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

    private enum ViewState {
        HOME,
        PLAY,
        MODS,
        SETTINGS,
        LOGIN,
        REGISTER,
        ACCOUNT
    }

    private enum ModFilter {
        INSTALLED,
        OPTIONAL,
        UPDATES
    }

    private enum LauncherAction {
        SYNC_ONLY,
        SYNC_AND_LAUNCH
    }

    private static final class ModEntry {
        private final String name;
        private final String description;
        private final String version;
        private final String iconPath;
        private final ModFilter filter;
        private final boolean updatable;
        private boolean enabled;

        private ModEntry(
            String name,
            String description,
            String version,
            String iconPath,
            boolean enabled,
            ModFilter filter,
            boolean updatable
        ) {
            this.name = name;
            this.description = description;
            this.version = version;
            this.iconPath = iconPath;
            this.enabled = enabled;
            this.filter = filter;
            this.updatable = updatable;
        }

        private boolean matches(String query) {
            String haystack = (name + " " + description + " " + version).toLowerCase(Locale.ROOT);
            return haystack.contains(query);
        }
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
    }
}
