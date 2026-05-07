package ru.mcrpg.launcher;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class AccountScreensPreviewApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);

        LauncherContext context = new LauncherContext(
            stage,
            LauncherConfigStore.defaultStore(),
            AuthSessionStore.defaultStore(),
            new AuthService(new AuthApiClient(), AuthSessionStore.defaultStore()),
            new SessionFileWriter(),
            new LauncherState(LauncherConfig.defaults(), null)
        );

        ScreenRouter router = new ScreenRouter(stage, context);
        context.setScreenRouter(router);
        router.open(ScreenRouter.Screen.AUTH);
    }
}
