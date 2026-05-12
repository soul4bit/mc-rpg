package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.URL;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class ServerController extends AbstractScreenController {

    private static final int SERVER_STATUS_TIMEOUT_MS = 1500;
    private static final String DASH = "—";

    private final ModpackManifestClient manifestClient = new ModpackManifestClient();

    @FXML
    private Label localRouteValueLabel;

    @FXML
    private Label resolvedRouteValueLabel;

    @FXML
    private Label manifestUrlValueLabel;

    @FXML
    private Label downloadBaseValueLabel;

    @FXML
    private Label authBaseValueLabel;

    @FXML
    private Label serverIdValueLabel;

    @FXML
    private Label workingDirectoryValueLabel;

    @FXML
    private Label serverPresenceValueLabel;

    @FXML
    private Region serverPresenceIndicator;

    @FXML
    private Label playersValueLabel;

    @FXML
    private Label pingValueLabel;

    @FXML
    private Label minecraftVersionValueLabel;

    @FXML
    private Label manifestVersionValueLabel;

    @FXML
    private Label overrideSummaryLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private void initialize() {
        applyWindowControls();
        applyLoadingState();
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        refreshServerDataAsync();
    }

    @FXML
    private void onOpenHome() {
        router().open(ScreenRouter.Screen.HOME);
    }

    @FXML
    private void onOpenProfile() {
        router().open(state().isAuthenticated() ? ScreenRouter.Screen.PROFILE : ScreenRouter.Screen.AUTH);
    }

    @FXML
    private void onOpenSettings() {
        router().open(ScreenRouter.Screen.SETTINGS);
    }

    @FXML
    private void onRefreshStatus() {
        refreshServerDataAsync();
    }

    private void refreshServerDataAsync() {
        applyLoadingState();
        Task<ServerSnapshot> task = new Task<ServerSnapshot>() {
            @Override
            protected ServerSnapshot call() {
                return buildSnapshot();
            }
        };

        task.setOnSucceeded(event -> renderSnapshot(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            serverPresenceValueLabel.setText("Ошибка");
            setPresenceTone("offline");
            statusLabel.setText(error == null ? "Не удалось обновить экран сервера." : error.getMessage());
        });

        Thread thread = new Thread(task, "server-screen-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private ServerSnapshot buildSnapshot() {
        LauncherConfig localConfig = LauncherDefaults.applyMissingValues(state().getConfig().copy());
        LauncherConfig resolvedConfig = localConfig.copy();
        String manifestUrl = localConfig.getManifestUrl();

        String manifestVersion = "нет данных";
        String minecraftVersion = "нет данных";
        String downloadBase = deriveManifestDirectory(manifestUrl);
        String manifestSummary = "Manifest overrides не обнаружены.";
        String statusText = "Проверка manifest и статуса Minecraft сервера.";

        try {
            LoadedManifest loadedManifest = manifestClient.load(manifestUrl);
            ModpackManifest manifest = loadedManifest.getManifest();
            applyManifestSettings(resolvedConfig, manifest);
            downloadBase = resolveDisplayDownloadBase(loadedManifest);
            manifestVersion = resolveManifestVersion(manifest);
            minecraftVersion = resolveMinecraftVersion(manifest);
            manifestSummary = buildOverrideSummary(localConfig, resolvedConfig);
            statusText = "Manifest загружен, маршрут и версии обновлены.";
        } catch (Exception exception) {
            manifestSummary = "Manifest не загрузился: " + exception.getMessage();
            statusText = "Работаем по локальному конфигу, потому что manifest сейчас недоступен.";
        }

        MinecraftServerStatusProbe.ServerStatus serverStatus = null;
        try {
            serverStatus = MinecraftServerStatusProbe.probe(
                resolvedConfig.getServerHost(),
                resolvedConfig.getServerPort(),
                SERVER_STATUS_TIMEOUT_MS
            );
            statusText = "Сервер ответил на status probe.";
        } catch (IOException exception) {
            statusText = "Маршрут " + formatRoute(resolvedConfig) + " не ответил на status probe.";
        }

        return new ServerSnapshot(
            localConfig,
            resolvedConfig,
            manifestUrl,
            downloadBase,
            manifestVersion,
            minecraftVersion,
            manifestSummary,
            statusText,
            serverStatus
        );
    }

    private void renderSnapshot(ServerSnapshot snapshot) {
        localRouteValueLabel.setText(formatRoute(snapshot.localConfig));
        resolvedRouteValueLabel.setText(formatRoute(snapshot.resolvedConfig));
        manifestUrlValueLabel.setText(snapshot.manifestUrl);
        downloadBaseValueLabel.setText(snapshot.downloadBase);
        authBaseValueLabel.setText(snapshot.resolvedConfig.getAuthBaseUrl());
        serverIdValueLabel.setText(snapshot.resolvedConfig.getServerId());
        workingDirectoryValueLabel.setText(resolveWorkingDirectoryLabel(snapshot.resolvedConfig));
        manifestVersionValueLabel.setText(snapshot.manifestVersion);
        overrideSummaryLabel.setText(snapshot.overrideSummary);
        statusLabel.setText(snapshot.statusText);

        if (snapshot.serverStatus != null) {
            serverPresenceValueLabel.setText("Онлайн");
            playersValueLabel.setText(formatPlayers(snapshot.serverStatus));
            pingValueLabel.setText(snapshot.serverStatus.getPingMs() + " мс");
            minecraftVersionValueLabel.setText(resolveServerVersion(snapshot.serverStatus, snapshot.minecraftVersion));
            setPresenceTone("online");
            return;
        }

        serverPresenceValueLabel.setText("Оффлайн");
        playersValueLabel.setText(DASH);
        pingValueLabel.setText(DASH);
        minecraftVersionValueLabel.setText(snapshot.minecraftVersion);
        setPresenceTone("offline");
    }

    private void applyLoadingState() {
        localRouteValueLabel.setText(DASH);
        resolvedRouteValueLabel.setText(DASH);
        manifestUrlValueLabel.setText(DASH);
        downloadBaseValueLabel.setText(DASH);
        authBaseValueLabel.setText(DASH);
        serverIdValueLabel.setText(DASH);
        workingDirectoryValueLabel.setText(DASH);
        serverPresenceValueLabel.setText("Проверка");
        playersValueLabel.setText(DASH);
        pingValueLabel.setText(DASH);
        minecraftVersionValueLabel.setText(DASH);
        manifestVersionValueLabel.setText(DASH);
        overrideSummaryLabel.setText("Проверяем manifest, маршрут и live status.");
        statusLabel.setText("Обновляем информацию о сервере...");
        setPresenceTone("checking");
    }

    private void applyWindowControls() {
        minimizeWindowButton.setText("");
        minimizeWindowButton.setGraphic(SvgIcons.icon("minimize", 18, "#D9D9D9"));
        closeWindowButton.setText("");
        closeWindowButton.setGraphic(SvgIcons.icon("close", 18, "#D9D9D9"));
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

    private void setPresenceTone(String tone) {
        toggleStyleClass(serverPresenceIndicator, "server-presence-checking", "checking".equals(tone));
        toggleStyleClass(serverPresenceIndicator, "server-presence-online", "online".equals(tone));
        toggleStyleClass(serverPresenceIndicator, "server-presence-offline", "offline".equals(tone));
    }

    private static String buildOverrideSummary(LauncherConfig localConfig, LauncherConfig resolvedConfig) {
        StringBuilder summary = new StringBuilder();
        appendOverride(summary, "route", formatRoute(localConfig), formatRoute(resolvedConfig));
        appendOverride(summary, "auth", localConfig.getAuthBaseUrl(), resolvedConfig.getAuthBaseUrl());
        appendOverride(summary, "serverId", localConfig.getServerId(), resolvedConfig.getServerId());
        appendOverride(summary, "workingDir", localConfig.getWorkingDirectory(), resolvedConfig.getWorkingDirectory());

        if (summary.length() == 0) {
            return "Manifest overrides не изменили локальный конфиг.";
        }
        return "Manifest overrides: " + summary;
    }

    private static void appendOverride(StringBuilder summary, String name, String before, String after) {
        String left = normalizeText(before);
        String right = normalizeText(after);
        if (left.equals(right)) {
            return;
        }
        if (summary.length() > 0) {
            summary.append(" | ");
        }
        summary.append(name).append(": ").append(left).append(" -> ").append(right);
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

    private static String resolveServerVersion(MinecraftServerStatusProbe.ServerStatus status, String fallback) {
        if (status != null && hasText(status.getVersionName())) {
            return status.getVersionName().trim();
        }
        return normalizeText(fallback);
    }

    private static String formatPlayers(MinecraftServerStatusProbe.ServerStatus status) {
        if (status == null || status.getOnlinePlayers() < 0) {
            return DASH;
        }
        if (status.getMaxPlayers() < 0) {
            return Integer.toString(status.getOnlinePlayers());
        }
        return status.getOnlinePlayers() + " / " + status.getMaxPlayers();
    }

    private static String resolveWorkingDirectoryLabel(LauncherConfig config) {
        if (config == null) {
            return DASH;
        }
        if (hasText(config.getWorkingDirectory())) {
            return config.getWorkingDirectory().trim();
        }
        return config.getGameDirectory();
    }

    private static String formatRoute(LauncherConfig config) {
        if (config == null) {
            return DASH;
        }
        return normalizeText(config.getServerHost()) + ":" + config.getServerPort();
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

    private static String normalizeText(String value) {
        if (!hasText(value)) {
            return DASH;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void toggleStyleClass(Node node, String styleClass, boolean enabled) {
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
            return;
        }
        node.getStyleClass().remove(styleClass);
    }

    private static final class ServerSnapshot {
        private final LauncherConfig localConfig;
        private final LauncherConfig resolvedConfig;
        private final String manifestUrl;
        private final String downloadBase;
        private final String manifestVersion;
        private final String minecraftVersion;
        private final String overrideSummary;
        private final String statusText;
        private final MinecraftServerStatusProbe.ServerStatus serverStatus;

        private ServerSnapshot(
            LauncherConfig localConfig,
            LauncherConfig resolvedConfig,
            String manifestUrl,
            String downloadBase,
            String manifestVersion,
            String minecraftVersion,
            String overrideSummary,
            String statusText,
            MinecraftServerStatusProbe.ServerStatus serverStatus
        ) {
            this.localConfig = localConfig;
            this.resolvedConfig = resolvedConfig;
            this.manifestUrl = manifestUrl;
            this.downloadBase = downloadBase;
            this.manifestVersion = manifestVersion;
            this.minecraftVersion = minecraftVersion;
            this.overrideSummary = overrideSummary;
            this.statusText = statusText;
            this.serverStatus = serverStatus;
        }
    }
}
