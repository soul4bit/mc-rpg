package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ServerAuthHints {

    private static final String REGISTER_COMMAND = "/register";
    private static final String LOGIN_COMMAND = "/login";

    private static final String REGISTER_HINT =
        "Подсказка: если это первый вход на сервер, введи /register <пароль> <пароль>.";
    private static final String LOGIN_HINT =
        "Подсказка: если аккаунт уже зарегистрирован, введи /login <пароль>.";
    private static final String RECONNECT_HINT =
        "Подсказка: если сервер выкинул из-за авторизации, зайди снова и выполни нужную команду в чате.";

    private ServerAuthHints() {
    }

    public static List<String> detect(String line) {
        if (line == null || line.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = line.toLowerCase(Locale.ROOT);
        boolean hasRegister = normalized.contains(REGISTER_COMMAND);
        boolean hasLogin = normalized.contains(LOGIN_COMMAND);
        boolean authKick = normalized.contains("not logged")
            || normalized.contains("not authenticated")
            || normalized.contains("please login")
            || normalized.contains("please register")
            || normalized.contains("authentication");

        if (!hasRegister && !hasLogin && !authKick) {
            return Collections.emptyList();
        }

        List<String> hints = new ArrayList<String>();
        if (hasRegister) {
            hints.add(REGISTER_HINT);
        }
        if (hasLogin) {
            hints.add(LOGIN_HINT);
        }
        if (authKick && !hasRegister && !hasLogin) {
            hints.add(RECONNECT_HINT);
        }
        return hints;
    }

    public static String launcherHelpHtml() {
        return "Если сервер попросил авторизацию или выкинул на входе, зайди снова и введи "
            + "<code>/register &lt;пароль&gt; &lt;пароль&gt;</code> для первого входа "
            + "или <code>/login &lt;пароль&gt;</code>, если аккаунт уже зарегистрирован.";
    }
}
