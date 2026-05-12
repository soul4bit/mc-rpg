package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
        config.setServerHost(LauncherConfig.DEFAULT_SERVER_HOST);
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
        config.setServerHost(LauncherConfig.DEFAULT_SERVER_HOST);
        config.setServerPort(25565);
        config.setLaunchTemplate("{java} --label \"Player {username}\" --server {serverHost}");

        List<String> command = builder.build(config);

        assertEquals(Arrays.asList("java", "--label", "Player RPG", "--server", LauncherConfig.DEFAULT_SERVER_HOST), command);
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
            "Неизвестный плейсхолдер {server}. Доступны: [java, username, gameDir, workingDir, serverHost, serverPort, uuid, accessToken, userType, gameSessionFile]",
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

    @Test
    void buildSupportsOfflineAuthPlaceholders() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername("Soul4");
        config.setLaunchTemplate("{java} --uuid {uuid} --token {accessToken} --userType {userType}");

        List<String> command = builder.build(config);

        assertEquals(
            Arrays.asList(
                "java",
                "--uuid",
                UUID.nameUUIDFromBytes("OfflinePlayer:Soul4".getBytes(StandardCharsets.UTF_8)).toString(),
                "--token",
                "0",
                "--userType",
                "legacy"
            ),
            command
        );
    }

    @Test
    void buildInjectsSessionFileJvmArgWhenAuthenticated() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setJavaCommand("java");
        config.setLaunchTemplate("{java} -jar forge.jar --username {username}");

        List<String> command = builder.build(
            config,
            LaunchIdentity.authenticated("Knight", "uuid-1", "token-1", Path.of("C:/Games/.obsidiangate/session.json"))
        );

        assertEquals(
            Arrays.asList(
                "java",
                "-Dobsidiangate.sessionFile=" + Path.of("C:/Games/.obsidiangate/session.json"),
                "-jar",
                "forge.jar",
                "--username",
                "Knight"
            ),
            command
        );
    }
}
