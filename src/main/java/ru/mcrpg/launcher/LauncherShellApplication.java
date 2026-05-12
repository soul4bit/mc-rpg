package ru.mcrpg.launcher;

import java.io.IOException;
import java.util.Optional;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class LauncherShellApplication extends Application {

    private static final double DEFAULT_WINDOW_WIDTH = 1500;
    private static final double DEFAULT_WINDOW_HEIGHT = 900;
    private static final double MIN_WINDOW_WIDTH = 1320;
    private static final double MIN_WINDOW_HEIGHT = 800;
    private static final double SCREEN_MARGIN_WIDTH = 32;
    private static final double SCREEN_MARGIN_HEIGHT = 48;

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(LauncherBrand.APP_NAME);

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double availableWidth = Math.max(960.0d, screenBounds.getWidth() - SCREEN_MARGIN_WIDTH);
        double availableHeight = Math.max(640.0d, screenBounds.getHeight() - SCREEN_MARGIN_HEIGHT);
        double targetWidth = Math.min(DEFAULT_WINDOW_WIDTH, availableWidth);
        double targetHeight = Math.min(DEFAULT_WINDOW_HEIGHT, availableHeight);

        stage.setMinWidth(Math.min(MIN_WINDOW_WIDTH, targetWidth));
        stage.setMinHeight(Math.min(MIN_WINDOW_HEIGHT, targetHeight));
        stage.setWidth(targetWidth);
        stage.setHeight(targetHeight);
        stage.setX(screenBounds.getMinX() + Math.max(0.0d, (screenBounds.getWidth() - targetWidth) / 2.0d));
        stage.setY(screenBounds.getMinY() + Math.max(0.0d, (screenBounds.getHeight() - targetHeight) / 2.0d));

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
