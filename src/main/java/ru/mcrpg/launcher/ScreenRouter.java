package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ScreenRouter {

    public enum Screen {
        AUTH("/ru/mcrpg/launcher/AuthView.fxml", "/ru/mcrpg/launcher/account.css"),
        REGISTER("/ru/mcrpg/launcher/RegisterView.fxml", "/ru/mcrpg/launcher/account.css"),
        HOME("/ru/mcrpg/launcher/launcher-shell.fxml", "/ru/mcrpg/launcher/launcher-shell.css"),
        PROFILE("/ru/mcrpg/launcher/ProfileView.fxml", "/ru/mcrpg/launcher/account.css");

        private final String fxmlPath;
        private final String stylesheetPath;

        Screen(String fxmlPath, String stylesheetPath) {
            this.fxmlPath = fxmlPath;
            this.stylesheetPath = stylesheetPath;
        }
    }

    private final Stage stage;
    private final LauncherContext context;

    public ScreenRouter(Stage stage, LauncherContext context) {
        this.stage = stage;
        this.context = context;
    }

    public void open(Screen screen) {
        try {
            FXMLLoader loader = new FXMLLoader(ScreenRouter.class.getResource(screen.fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof LauncherContextAware aware) {
                aware.bindContext(context);
            }

            Scene scene = new Scene(root, Math.max(stage.getWidth(), 1600), Math.max(stage.getHeight(), 900));
            scene.getStylesheets().add(ScreenRouter.class.getResource(screen.stylesheetPath).toExternalForm());
            stage.setScene(scene);
            if (!stage.isShowing()) {
                stage.show();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open screen " + screen.name() + ".", exception);
        }
    }
}
