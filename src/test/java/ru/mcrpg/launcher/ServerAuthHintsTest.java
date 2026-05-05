package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ServerAuthHintsTest {

    @Test
    void detectSuggestsRegisterCommand() {
        assertEquals(
            Collections.singletonList("Подсказка: если это первый вход на сервер, введи /register <пароль> <пароль>."),
            ServerAuthHints.detect("[CHAT] Please register with /register <password> <password>")
        );
    }

    @Test
    void detectSuggestsLoginCommand() {
        assertEquals(
            Collections.singletonList("Подсказка: если аккаунт уже зарегистрирован, введи /login <пароль>."),
            ServerAuthHints.detect("[CHAT] Please login with /login <password>")
        );
    }

    @Test
    void detectSuggestsBothCommandsWhenBothAreMentioned() {
        assertEquals(
            Arrays.asList(
                "Подсказка: если это первый вход на сервер, введи /register <пароль> <пароль>.",
                "Подсказка: если аккаунт уже зарегистрирован, введи /login <пароль>."
            ),
            ServerAuthHints.detect("[CHAT] Use /register or /login before playing")
        );
    }

    @Test
    void detectSuggestsReconnectForGenericAuthKick() {
        assertEquals(
            Collections.singletonList(
                "Подсказка: если сервер выкинул из-за авторизации, зайди снова и выполни нужную команду в чате."
            ),
            ServerAuthHints.detect("Disconnected: not authenticated")
        );
    }

    @Test
    void launcherHelpHtmlMentionsBothCommands() {
        String html = ServerAuthHints.launcherHelpHtml();

        assertTrue(html.contains("/register"));
        assertTrue(html.contains("/login"));
    }
}
