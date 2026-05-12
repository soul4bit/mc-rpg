package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class SettingsController extends AbstractScreenController {

    @FXML
    private TextField manifestUrlField;

    @FXML
    private TextField authBaseUrlField;

    @FXML
    private TextField serverHostField;

    @FXML
    private TextField serverPortField;

    @FXML
    private TextField serverIdField;

    @FXML
    private TextField gameDirectoryField;

    @FXML
    private TextField workingDirectoryField;

    @FXML
    private TextField javaCommandField;

    @FXML
    private TextArea launchTemplateArea;

    @FXML
    private CheckBox updateFilesBeforeLaunchCheckBox;

    @FXML
    private Label routeSummaryValueLabel;

    @FXML
    private Label manifestSummaryValueLabel;

    @FXML
    private Label authSummaryValueLabel;

    @FXML
    private Label configPathValueLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private void initialize() {
        launchTemplateArea.setWrapText(true);
        applyWindowControls();
        setStatus("Изменения применяются к следующим операциям sync и launch.", false);
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        renderConfig(LauncherDefaults.applyMissingValues(state().getConfig().copy()));
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
    private void onOpenMods() {
        router().open(ScreenRouter.Screen.MODS);
    }

    @FXML
    private void onSaveSettings() {
        try {
            LauncherConfig config = buildConfigFromInputs();
            context().saveConfig(config);
            renderConfig(state().getConfig().copy());
            setStatus("Настройки сохранены в launcher.properties.", false);
        } catch (IllegalArgumentException exception) {
            setStatus(exception.getMessage(), true);
        } catch (IOException exception) {
            setStatus("Не удалось сохранить настройки: " + exception.getMessage(), true);
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onResetDefaults() {
        renderConfig(LauncherConfig.defaults());
        setStatus("В форму загружены значения по умолчанию. Сохраните их, чтобы применить.", false);
    }

    @FXML
    private void onReloadSaved() {
        renderConfig(LauncherDefaults.applyMissingValues(state().getConfig().copy()));
        setStatus("Возвращены сохраненные настройки.", false);
    }

    private LauncherConfig buildConfigFromInputs() {
        LauncherConfig config = state().getConfig().copy();
        config.setManifestUrl(trimmed(manifestUrlField.getText()));
        config.setAuthBaseUrl(trimmed(authBaseUrlField.getText()));
        config.setServerHost(trimmed(serverHostField.getText()));
        config.setServerPort(parseServerPort(serverPortField.getText()));
        config.setServerId(trimmed(serverIdField.getText()));
        config.setGameDirectory(trimmed(gameDirectoryField.getText()));
        config.setWorkingDirectory(trimmed(workingDirectoryField.getText()));
        config.setJavaCommand(trimmed(javaCommandField.getText()));
        config.setLaunchTemplate(trimmed(launchTemplateArea.getText()));
        config.setUpdateFilesBeforeLaunch(updateFilesBeforeLaunchCheckBox.isSelected());
        return LauncherDefaults.applyMissingValues(config);
    }

    private void renderConfig(LauncherConfig config) {
        LauncherConfig resolved = LauncherDefaults.applyMissingValues(config.copy());
        manifestUrlField.setText(resolved.getManifestUrl());
        authBaseUrlField.setText(resolved.getAuthBaseUrl());
        serverHostField.setText(resolved.getServerHost());
        serverPortField.setText(Integer.toString(resolved.getServerPort()));
        serverIdField.setText(resolved.getServerId());
        gameDirectoryField.setText(resolved.getGameDirectory());
        workingDirectoryField.setText(resolved.getWorkingDirectory());
        javaCommandField.setText(resolved.getJavaCommand());
        launchTemplateArea.setText(resolved.getLaunchTemplate());
        updateFilesBeforeLaunchCheckBox.setSelected(resolved.isUpdateFilesBeforeLaunch());

        routeSummaryValueLabel.setText(resolved.getServerHost() + ":" + resolved.getServerPort());
        manifestSummaryValueLabel.setText(resolved.getManifestUrl());
        authSummaryValueLabel.setText(resolved.getAuthBaseUrl());
        configPathValueLabel.setText(context().getConfigStore().getConfigFile().toString());
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message == null ? "" : message.trim());
        toggleStyleClass(statusLabel, "status-error", error);
        toggleStyleClass(statusLabel, "settings-status-success", !error);
    }

    private void applyWindowControls() {
        minimizeWindowButton.setText("");
        minimizeWindowButton.setGraphic(SvgIcons.icon("minimize", 18, "#D9D9D9"));
        closeWindowButton.setText("");
        closeWindowButton.setGraphic(SvgIcons.icon("close", 18, "#D9D9D9"));
    }

    private static int parseServerPort(String value) {
        String trimmed = trimmed(value);
        if (trimmed.isEmpty()) {
            return 0;
        }

        try {
            int port = Integer.parseInt(trimmed);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Порт сервера должен быть в диапазоне 1-65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Порт сервера должен быть числом.");
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
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
}
