package ru.mcrpg.launcher;

import java.net.URL;
import java.util.List;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class ModsController extends AbstractScreenController {

    private final ModpackManifestClient manifestClient = new ModpackManifestClient();

    @FXML
    private Label manifestVersionValueLabel;

    @FXML
    private Label filesCountValueLabel;

    @FXML
    private Label modsCountValueLabel;

    @FXML
    private Label runtimeCountValueLabel;

    @FXML
    private Label totalSizeValueLabel;

    @FXML
    private Label manifestUrlValueLabel;

    @FXML
    private Label downloadBaseValueLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private VBox runtimePackagesBox;

    @FXML
    private VBox manifestFilesBox;

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
        refreshManifestAsync();
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
    private void onOpenServer() {
        router().open(ScreenRouter.Screen.SERVER);
    }

    @FXML
    private void onOpenSettings() {
        router().open(ScreenRouter.Screen.SETTINGS);
    }

    @FXML
    private void onRefreshManifest() {
        refreshManifestAsync();
    }

    private void refreshManifestAsync() {
        applyLoadingState();
        Task<ModsSnapshot> task = new Task<ModsSnapshot>() {
            @Override
            protected ModsSnapshot call() throws Exception {
                return buildSnapshot();
            }
        };

        task.setOnSucceeded(event -> renderSnapshot(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            statusLabel.setText(error == null ? "Не удалось загрузить manifest." : error.getMessage());
            runtimePackagesBox.getChildren().setAll(createEmptyState("Runtime packages пока недоступны."));
            manifestFilesBox.getChildren().setAll(createEmptyState("Список files[] пока недоступен."));
        });

        Thread thread = new Thread(task, "mods-screen-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private ModsSnapshot buildSnapshot() throws Exception {
        LauncherConfig config = LauncherDefaults.applyMissingValues(state().getConfig().copy());
        LoadedManifest loadedManifest = manifestClient.load(config.getManifestUrl());
        ModpackManifest manifest = loadedManifest.getManifest();

        List<ModpackFile> files = manifest.getFiles();
        List<RuntimePackage> runtimePackages = manifest.getRuntime() == null
            ? java.util.Collections.<RuntimePackage>emptyList()
            : manifest.getRuntime().getPackages();

        int modsCount = 0;
        long totalSize = 0L;
        for (ModpackFile file : files) {
            if (file == null) {
                continue;
            }
            if (startsWithDirectory(file.getPath(), "mods/")) {
                modsCount++;
            }
            if (file.getSize() != null) {
                totalSize += file.getSize().longValue();
            }
        }

        return new ModsSnapshot(
            loadedManifest,
            manifest,
            files,
            runtimePackages,
            modsCount,
            totalSize
        );
    }

    private void renderSnapshot(ModsSnapshot snapshot) {
        ModpackManifest manifest = snapshot.manifest;
        manifestVersionValueLabel.setText(normalizeText(manifest.getVersion()));
        filesCountValueLabel.setText(Integer.toString(snapshot.files.size()));
        modsCountValueLabel.setText(Integer.toString(snapshot.modsCount));
        runtimeCountValueLabel.setText(Integer.toString(snapshot.runtimePackages.size()));
        totalSizeValueLabel.setText(formatBytes(snapshot.totalFileBytes));
        manifestUrlValueLabel.setText(snapshot.loadedManifest.getSourceUrl().toString());
        downloadBaseValueLabel.setText(resolveDisplayDownloadBase(snapshot.loadedManifest));
        statusLabel.setText(
            "Manifest " + normalizeText(manifest.getVersion()) + ": " + snapshot.files.size()
                + " files, " + snapshot.runtimePackages.size() + " runtime packages."
        );

        renderRuntimePackages(snapshot.runtimePackages);
        renderManifestFiles(snapshot.files);
    }

    private void renderRuntimePackages(List<RuntimePackage> runtimePackages) {
        runtimePackagesBox.getChildren().clear();
        if (runtimePackages == null || runtimePackages.isEmpty()) {
            runtimePackagesBox.getChildren().add(createEmptyState("Runtime packages в manifest не объявлены."));
            return;
        }

        for (RuntimePackage runtimePackage : runtimePackages) {
            runtimePackagesBox.getChildren().add(createRuntimeCard(runtimePackage));
        }
    }

    private void renderManifestFiles(List<ModpackFile> files) {
        manifestFilesBox.getChildren().clear();
        if (files == null || files.isEmpty()) {
            manifestFilesBox.getChildren().add(createEmptyState("В manifest нет файлов для синхронизации."));
            return;
        }

        for (ModpackFile file : files) {
            manifestFilesBox.getChildren().add(createManifestFileCard(file));
        }
    }

    private Node createRuntimeCard(RuntimePackage runtimePackage) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("manifest-item-card");

        HBox titleRow = new HBox(10.0);
        Label title = new Label(buildRuntimeTitle(runtimePackage));
        title.getStyleClass().add("manifest-item-title");
        Label badge = createBadge("runtime");
        titleRow.getChildren().addAll(title, badge);

        VBox details = new VBox(6.0);
        details.getChildren().add(createDetailRow("URL", normalizeText(runtimePackage.getUrl())));
        details.getChildren().add(createDetailRow("Extract Dir", normalizeText(runtimePackage.getExtractDir())));
        details.getChildren().add(createDetailRow("Java Path", normalizeText(runtimePackage.getJavaPath())));
        details.getChildren().add(createDetailRow("SHA256", normalizeHash(runtimePackage.getSha256())));
        details.getChildren().add(createDetailRow("Size", formatBytes(runtimePackage.getSize() == null ? 0L : runtimePackage.getSize().longValue())));

        card.getChildren().addAll(titleRow, details);
        return card;
    }

    private Node createManifestFileCard(ModpackFile file) {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("manifest-item-card");

        HBox titleRow = new HBox(10.0);
        Label title = new Label(normalizeText(file.getPath()));
        title.getStyleClass().add("manifest-item-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, createBadge(resolveFileCategory(file)));

        HBox metricsRow = new HBox(14.0);
        metricsRow.getChildren().add(createMetricChip("size", formatBytes(file.getSize() == null ? 0L : file.getSize().longValue())));
        metricsRow.getChildren().add(createMetricChip("sha", normalizeHash(file.getSha256())));
        metricsRow.getChildren().add(createMetricChip("exec", file.isExecutable() ? "yes" : "no"));

        VBox details = new VBox(6.0);
        if (hasText(file.getUrl())) {
            details.getChildren().add(createDetailRow("File URL", file.getUrl().trim()));
        }
        details.getChildren().add(createDetailRow("Artifact", extractLeafName(file.getPath())));

        card.getChildren().addAll(titleRow, metricsRow, details);
        return card;
    }

    private Node createDetailRow(String labelText, String valueText) {
        VBox row = new VBox(2.0);

        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("manifest-item-detail");
        value.setWrapText(true);

        row.getChildren().addAll(label, value);
        return row;
    }

    private Node createMetricChip(String labelText, String valueText) {
        VBox chip = new VBox(2.0);
        chip.getStyleClass().add("manifest-metric-chip");

        Label label = new Label(labelText.toUpperCase(java.util.Locale.ROOT));
        label.getStyleClass().add("manifest-metric-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("manifest-metric-value");

        chip.getChildren().addAll(label, value);
        return chip;
    }

    private Label createBadge(String text) {
        Label badge = new Label(text.toUpperCase(java.util.Locale.ROOT));
        badge.getStyleClass().add("manifest-item-badge");
        return badge;
    }

    private Node createEmptyState(String text) {
        VBox box = new VBox();
        box.getStyleClass().add("manifest-empty-card");
        box.setPadding(new Insets(18.0));

        Label label = new Label(text);
        label.getStyleClass().add("label-muted");
        label.setWrapText(true);
        box.getChildren().add(label);
        return box;
    }

    private void applyLoadingState() {
        manifestVersionValueLabel.setText("...");
        filesCountValueLabel.setText("...");
        modsCountValueLabel.setText("...");
        runtimeCountValueLabel.setText("...");
        totalSizeValueLabel.setText("...");
        manifestUrlValueLabel.setText("...");
        downloadBaseValueLabel.setText("...");
        statusLabel.setText("Загружаем manifest и строим список modpack файлов...");
        runtimePackagesBox.getChildren().setAll(createEmptyState("Проверяем runtime packages..."));
        manifestFilesBox.getChildren().setAll(createEmptyState("Проверяем manifest.files[]..."));
    }

    private void applyWindowControls() {
        minimizeWindowButton.setText("");
        minimizeWindowButton.setGraphic(SvgIcons.icon("minimize", 18, "#D9D9D9"));
        closeWindowButton.setText("");
        closeWindowButton.setGraphic(SvgIcons.icon("close", 18, "#D9D9D9"));
    }

    private static String buildRuntimeTitle(RuntimePackage runtimePackage) {
        return normalizeText(runtimePackage.getOs()) + " / " + normalizeText(runtimePackage.getArch());
    }

    private static String resolveFileCategory(ModpackFile file) {
        String path = file == null ? "" : normalizeText(file.getPath()).toLowerCase(java.util.Locale.ROOT);
        if (path.startsWith("mods/")) {
            return "mod";
        }
        if (path.startsWith("config/")) {
            return "config";
        }
        if (path.startsWith("runtime/")) {
            return "runtime";
        }
        return "file";
    }

    private static boolean startsWithDirectory(String path, String prefix) {
        if (!hasText(path)) {
            return false;
        }
        return path.trim().replace('\\', '/').toLowerCase(java.util.Locale.ROOT)
            .startsWith(prefix.toLowerCase(java.util.Locale.ROOT));
    }

    private static String extractLeafName(String path) {
        if (!hasText(path)) {
            return "—";
        }
        String normalized = path.trim().replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
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

    private static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0 B";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kilobytes = bytes / 1024.0d;
        if (kilobytes < 1024.0d) {
            return String.format(java.util.Locale.US, "%.1f KB", Double.valueOf(kilobytes));
        }
        double megabytes = kilobytes / 1024.0d;
        return String.format(java.util.Locale.US, "%.1f MB", Double.valueOf(megabytes));
    }

    private static String normalizeHash(String sha256) {
        if (!hasText(sha256)) {
            return "—";
        }
        String trimmed = sha256.trim();
        if (trimmed.length() <= 16) {
            return trimmed;
        }
        return trimmed.substring(0, 12) + "..." + trimmed.substring(trimmed.length() - 8);
    }

    private static String normalizeText(String value) {
        return hasText(value) ? value.trim() : "—";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class ModsSnapshot {
        private final LoadedManifest loadedManifest;
        private final ModpackManifest manifest;
        private final List<ModpackFile> files;
        private final List<RuntimePackage> runtimePackages;
        private final int modsCount;
        private final long totalFileBytes;

        private ModsSnapshot(
            LoadedManifest loadedManifest,
            ModpackManifest manifest,
            List<ModpackFile> files,
            List<RuntimePackage> runtimePackages,
            int modsCount,
            long totalFileBytes
        ) {
            this.loadedManifest = loadedManifest;
            this.manifest = manifest;
            this.files = files;
            this.runtimePackages = runtimePackages;
            this.modsCount = modsCount;
            this.totalFileBytes = totalFileBytes;
        }
    }
}
