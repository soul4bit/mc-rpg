package ru.mcrpg.launcher;

import java.io.IOException;
import javafx.stage.Stage;

public final class LauncherContext {

    private final Stage stage;
    private final LauncherConfigStore configStore;
    private final AuthSessionStore sessionStore;
    private final AuthService authService;
    private final SessionFileWriter sessionFileWriter;
    private final LauncherState state;
    private ScreenRouter screenRouter;

    public LauncherContext(
        Stage stage,
        LauncherConfigStore configStore,
        AuthSessionStore sessionStore,
        AuthService authService,
        SessionFileWriter sessionFileWriter,
        LauncherState state
    ) {
        this.stage = stage;
        this.configStore = configStore;
        this.sessionStore = sessionStore;
        this.authService = authService;
        this.sessionFileWriter = sessionFileWriter;
        this.state = state;
    }

    public Stage getStage() {
        return stage;
    }

    public LauncherConfigStore getConfigStore() {
        return configStore;
    }

    public AuthSessionStore getSessionStore() {
        return sessionStore;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public SessionFileWriter getSessionFileWriter() {
        return sessionFileWriter;
    }

    public LauncherState getState() {
        return state;
    }

    public ScreenRouter getScreenRouter() {
        return screenRouter;
    }

    public void setScreenRouter(ScreenRouter screenRouter) {
        this.screenRouter = screenRouter;
    }

    public void saveConfig(LauncherConfig config) throws IOException {
        LauncherConfig resolved = LauncherDefaults.applyMissingValues(config.copy());
        configStore.save(resolved);
        state.setConfig(resolved);
    }

    public void persistStateQuietly() {
        try {
            saveConfig(state.getConfig());
        } catch (IOException ignored) {
        }

        try {
            authService.persist(state.getSession());
        } catch (IOException ignored) {
        }
    }
}
