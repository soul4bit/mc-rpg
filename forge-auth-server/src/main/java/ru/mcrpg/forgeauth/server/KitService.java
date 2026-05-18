package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

final class KitService {

    private static final Path DEFAULT_CLAIMS_PATH = Paths.get("obsidiangate", "kit-claims.properties");
    private static final String START_KIT_PREFIX = "start.";

    private final Logger logger;
    private final Path claimsPath;
    private final Properties claims = new Properties();
    private boolean loaded;

    KitService(Logger logger) {
        this(logger, DEFAULT_CLAIMS_PATH);
    }

    KitService(Logger logger, Path claimsPath) {
        this.logger = logger;
        this.claimsPath = claimsPath;
    }

    synchronized void load() {
        claims.clear();
        if (Files.exists(claimsPath)) {
            try (InputStream input = Files.newInputStream(claimsPath)) {
                claims.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Не удалось прочитать выдачи китов. Запускаем пустое хранилище.", exception);
            }
        }
        loaded = true;
        logger.info(String.format("Выдачи китов загружены из %s. Записей=%d", claimsPath, claims.size()));
    }

    synchronized boolean hasClaimedStart(String playerId) {
        ensureLoaded();
        return claims.containsKey(startKitKey(playerId));
    }

    synchronized void recordStartClaim(String playerId, String playerName) {
        ensureLoaded();
        claims.setProperty(startKitKey(playerId), playerName == null ? "" : playerName);
        save();
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        try {
            Path parent = claimsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(claimsPath)) {
                claims.store(output, "Одноразовые выдачи китов ObsidianGate.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить выдачи китов.", exception);
        }
    }

    private static String startKitKey(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId не должен быть пустым.");
        }
        return START_KIT_PREFIX + playerId.trim().toLowerCase();
    }
}
