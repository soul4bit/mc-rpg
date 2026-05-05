package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaunchCommandBuilderTest {

    private final LaunchCommandBuilder builder = new LaunchCommandBuilder();

    @Test
    void buildKeepsPlaceholderValueWithSpacesAsSingleArgument() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("Player One");
        config.setJavaCommand("java");
        config.setGameDirectory("C:/Games/MC RPG");
        config.setWorkingDirectory("C:/Games/MC RPG");
        config.setServerHost("192.168.1.103");
        config.setServerPort(25565);
        config.setLaunchTemplate("{java} -jar forge.jar --gameDir {gameDir} --username {username}");

        List<String> command = builder.build(config);

        assertEquals(
            Arrays.asList("java", "-jar", "forge.jar", "--gameDir", "C:/Games/MC RPG", "--username", "Player One"),
            command
        );
    }

    @Test
    void buildSupportsQuotedSegmentsInTemplate() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("RPG");
        config.setJavaCommand("java");
        config.setGameDirectory("/srv/mc-rpg");
        config.setWorkingDirectory("/srv/mc-rpg");
        config.setServerHost("192.168.1.103");
        config.setServerPort(25565);
        config.setLaunchTemplate("{java} --label \"Player {username}\" --server {serverHost}");

        List<String> command = builder.build(config);

        assertEquals(Arrays.asList("java", "--label", "Player RPG", "--server", "192.168.1.103"), command);
    }

    @Test
    void buildRejectsUnknownPlaceholders() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory("/srv/mc-rpg");
        config.setWorkingDirectory("/srv/mc-rpg");
        config.setLaunchTemplate("{java} --server {server}");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> builder.build(config)
        );

        assertEquals(
            "Неизвестный плейсхолдер {server}. Доступны: [java, username, gameDir, workingDir, serverHost, serverPort]",
            exception.getMessage()
        );
    }

    @Test
    void buildAllowsBlankOptionalFieldsWhenTemplateDoesNotUseThem() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("");
        config.setJavaCommand("");
        config.setGameDirectory("");
        config.setWorkingDirectory("");
        config.setServerHost("");
        config.setServerPort(25565);
        config.setLaunchTemplate("start-client.bat");

        List<String> command = builder.build(config);

        assertEquals(Arrays.asList("start-client.bat"), command);
    }
}
