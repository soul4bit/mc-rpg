package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class ProfileController extends AbstractScreenController {

    @FXML
    private Label profileNameLabel;

    @FXML
    private Label profileRoleLabel;

    @FXML
    private Label profileEmailLabel;

    @FXML
    private Label accountIdLabel;

    @FXML
    private Label launcherPathLabel;

    @FXML
    private Label launcherServerIdLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private void initialize() {
        applyWindowControls();
    }

    @Override
    protected void onContextBound(LauncherContext context) {
        if (!state().isAuthenticated()) {
            router().open(ScreenRouter.Screen.AUTH);
            return;
        }
        renderAccount(state().getSession().getAccount());
        refreshProfileAsync();
    }

    @FXML
    private void onOpenHome() {
        router().open(ScreenRouter.Screen.HOME);
    }

    @FXML
    private void onLogout() {
        context().getAuthService().logoutQuietly(state().getConfig(), state().getSession());
        state().setSession(null);
        router().open(ScreenRouter.Screen.AUTH);
    }

    @FXML
    private void onChangeSkin() {
        showInfo("Скин", "Загрузка скина будет подключена после клиентского мода.");
    }

    @FXML
    private void onEditProfile() {
        showInfo("Профиль", "Редактирование профиля пока не подключено.");
    }

    @FXML
    private void onEnableTwoFactor() {
        showInfo("2FA", "Подключение двухфакторной защиты пока не реализовано.");
    }

    @FXML
    private void onChangePassword() {
        showInfo("Пароль", "Смена пароля будет добавлена отдельным окном.");
    }

    @FXML
    private void onOpenStats() {
        showInfo("Статистика", "Подробная статистика персонажа пока не подключена.");
    }

    @FXML
    private void onLauncherSettings() {
        router().open(ScreenRouter.Screen.HOME);
    }

    private void refreshProfileAsync() {
        statusLabel.setText("Обновляем профиль...");
        Task<AuthAccount> task = new Task<AuthAccount>() {
            @Override
            protected AuthAccount call() throws Exception {
                return context().getAuthService().fetchProfile(state().getConfig(), state().getSession());
            }
        };

        task.setOnSucceeded(event -> {
            AuthAccount account = task.getValue();
            state().setSession(state().getSession().withAccount(account));
            renderAccount(account);
            statusLabel.setText("Профиль синхронизирован.");
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            statusLabel.setText(error == null ? "Не удалось обновить профиль." : error.getMessage());
        });

        Thread thread = new Thread(task, "profile-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderAccount(AuthAccount account) {
        profileNameLabel.setText(account.getUsername());
        profileRoleLabel.setText(account.getRole().isEmpty() ? "player" : account.getRole());
        profileEmailLabel.setText(account.getEmail().isEmpty() ? "email not set" : account.getEmail());
        accountIdLabel.setText(account.getId());
        launcherPathLabel.setText(state().getConfig().getGameDirectory());
        launcherServerIdLabel.setText(state().getConfig().getServerId());
    }

    private void applyWindowControls() {
        minimizeWindowButton.setText("");
        minimizeWindowButton.setGraphic(SvgIcons.icon("minimize", 18, "#D9D9D9"));
        closeWindowButton.setText("");
        closeWindowButton.setGraphic(SvgIcons.icon("close", 18, "#D9D9D9"));
    }
}
