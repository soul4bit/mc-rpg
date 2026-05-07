package ru.mcrpg.launcher;

import java.io.IOException;
import java.util.regex.Pattern;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import ru.mcrpg.launcher.ui.SvgIcons;

public final class RegisterController extends AbstractScreenController {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private CheckBox rulesCheck;

    @FXML
    private Button registerButton;

    @FXML
    private Hyperlink openLoginLink;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button closeWindowButton;

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        applyWindowControls();
        statusLabel.setText("");
    }

    @FXML
    private void onRegister() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmation = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            setStatus("Ник должен совпадать с [A-Za-z0-9_]{3,16}.");
            return;
        }
        if (email.isBlank() || !email.contains("@")) {
            setStatus("Укажите корректный email.");
            return;
        }
        if (password.length() < 8) {
            setStatus("Пароль должен быть не короче 8 символов.");
            return;
        }
        if (!password.equals(confirmation)) {
            setStatus("Пароли не совпадают.");
            return;
        }
        if (!rulesCheck.isSelected()) {
            setStatus("Нужно принять правила сервера.");
            return;
        }

        setBusy(true);
        setStatus("Создаём аккаунт...");

        Task<AuthSession> task = new Task<AuthSession>() {
            @Override
            protected AuthSession call() throws Exception {
                return context().getAuthService().register(state().getConfig(), username, email, password, true);
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            AuthSession session = task.getValue();
            state().setSession(session);
            LauncherConfig config = state().getConfig().copy();
            config.setUsername(session.getAccount().getUsername());
            try {
                context().saveConfig(config);
                setStatus("");
                router().open(ScreenRouter.Screen.HOME);
            } catch (IOException exception) {
                setStatus(exception.getMessage());
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable error = task.getException();
            setStatus(error == null ? "Не удалось создать аккаунт." : error.getMessage());
        });

        Thread thread = new Thread(task, "auth-register");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void openLogin() {
        router().open(ScreenRouter.Screen.AUTH);
    }

    private void setBusy(boolean value) {
        usernameField.setDisable(value);
        emailField.setDisable(value);
        passwordField.setDisable(value);
        confirmPasswordField.setDisable(value);
        rulesCheck.setDisable(value);
        registerButton.setDisable(value);
        openLoginLink.setDisable(value);
    }

    private void setStatus(String message) {
        statusLabel.setText(message == null ? "" : message.trim());
    }

    private void applyWindowControls() {
        minimizeWindowButton.setText("");
        minimizeWindowButton.setGraphic(SvgIcons.icon("minimize", 18, "#D9D9D9"));
        closeWindowButton.setText("");
        closeWindowButton.setGraphic(SvgIcons.icon("close", 18, "#D9D9D9"));
    }
}
