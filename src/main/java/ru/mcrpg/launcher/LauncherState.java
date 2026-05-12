package ru.mcrpg.launcher;

public final class LauncherState {

    private LauncherConfig config;
    private AuthSession session;
    private String authNotice;

    public LauncherState(LauncherConfig config, AuthSession session) {
        this.config = config == null ? LauncherConfig.defaults() : config;
        this.session = session;
    }

    public LauncherConfig getConfig() {
        return config;
    }

    public void setConfig(LauncherConfig config) {
        this.config = config == null ? LauncherConfig.defaults() : config;
    }

    public AuthSession getSession() {
        return session;
    }

    public void setSession(AuthSession session) {
        this.session = session;
        if (session != null) {
            this.authNotice = null;
        }
    }

    public boolean isAuthenticated() {
        return session != null && session.getAccount() != null;
    }

    public void setAuthNotice(String authNotice) {
        this.authNotice = authNotice == null ? null : authNotice.trim();
    }

    public String consumeAuthNotice() {
        String notice = authNotice;
        authNotice = null;
        return notice;
    }
}
