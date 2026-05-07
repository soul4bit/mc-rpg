package ru.mcrpg.launcher;

import java.io.IOException;
import java.util.Optional;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class LauncherShellApplication extends Application {

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(LauncherBrand.APP_NAME);
        stage.setMinWidth(1280);
        stage.setMinHeight(760);

        LauncherConfigStore configStore = LauncherConfigStore.defaultStore();
        LauncherConfig config = loadConfig(configStore);

        AuthSessionStore sessionStore = AuthSessionStore.defaultStore();
        AuthSession session = loadSession(sessionStore).orElse(null);

        LauncherState state = new LauncherState(config, session);
        AuthService authService = new AuthService(new AuthApiClient(), sessionStore);
        LauncherContext context = new LauncherContext(stage, configStore, sessionStore, authService, new SessionFileWriter(), state);
        ScreenRouter router = new ScreenRouter(stage, context);
        context.setScreenRouter(router);

        stage.setOnCloseRequest(event -> context.persistStateQuietly());
        router.open(state.isAuthenticated() ? ScreenRouter.Screen.HOME : ScreenRouter.Screen.AUTH);
    }

    private static LauncherConfig loadConfig(LauncherConfigStore configStore) {
        try {
            LauncherConfig loaded = configStore.load();
            return LauncherDefaults.applyMissingValues(loaded);
        } catch (IOException exception) {
            return LauncherDefaults.applyMissingValues(LauncherConfig.defaults());
        }
    }

    private static Optional<AuthSession> loadSession(AuthSessionStore sessionStore) {
        try {
            return sessionStore.load();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
