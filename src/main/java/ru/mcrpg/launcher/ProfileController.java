package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

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
            if (handleExpiredSession(error)) {
                return;
            }
            statusLabel.setText(error == null ? "Не удалось обновить профиль." : error.getMessage());
        });

        Thread thread = new Thread(task, "profile-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderAccount(AuthAccount account) {
        profileNameLabel.setText(account.getUsername());
        profileRoleLabel.setText(account.getRole().isEmpty() ? "Игрок" : account.getRole());
        profileEmailLabel.setText(account.getEmail().isEmpty() ? "Email не указан" : account.getEmail());
        accountIdLabel.setText(account.getId());
        launcherPathLabel.setText(state().getConfig().getGameDirectory());
        launcherServerIdLabel.setText(state().getConfig().getServerId());
    }

    private boolean handleExpiredSession(Throwable error) {
        if (!(error instanceof AuthSessionExpiredException)) {
            return false;
        }

        state().setSession(null);
        state().setAuthNotice(error.getMessage());
        context().persistStateQuietly();
        router().open(ScreenRouter.Screen.AUTH);
        return true;
    }

}
