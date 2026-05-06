package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class LauncherShellApplication extends Application {

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            LauncherShellApplication.class.getResource("/ru/mcrpg/launcher/launcher-shell.fxml")
        );
        Parent root = loader.load();
        LauncherShellController controller = loader.getController();

        Scene scene = new Scene(root, 1400, 920);
        scene.getStylesheets().add(
            LauncherShellApplication.class.getResource("/ru/mcrpg/launcher/launcher-shell.css").toExternalForm()
        );

        stage.setTitle(LauncherBrand.APP_NAME);
        stage.setMinWidth(960);
        stage.setMinHeight(720);
        stage.setScene(scene);
        controller.attach(stage, getHostServices());
        stage.setOnCloseRequest(event -> controller.onCloseRequest());
        stage.show();
    }
}
