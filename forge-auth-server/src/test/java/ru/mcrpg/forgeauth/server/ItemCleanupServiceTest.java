package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import net.minecraft.entity.item.EntityItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemCleanupServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadCreatesDefaultConfigWhenMissing() {
        Path configPath = tempDirectory.resolve("obsidiangate-item-cleanup.properties");
        ItemCleanupService service = new ItemCleanupService(Logger.getLogger("test"), configPath, () -> null);

        service.load();

        assertTrue(Files.exists(configPath));
        assertTrue(service.config().enabled);
        assertTrue(service.config().warningSeconds.contains(Integer.valueOf(60)));
    }

    @Test
    void cleanupRemovesOnlyLiveDroppedItems() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-item-cleanup.properties");
        Files.write(
            configPath,
            (
                "enabled=true\n"
                    + "intervalSeconds=60\n"
                    + "warningSeconds=5\n"
            ).getBytes(StandardCharsets.UTF_8)
        );

        EntityItem liveItem = new EntityItem();
        EntityItem alreadyDeadItem = new EntityItem();
        alreadyDeadItem.isDead = true;
        FakeEntity otherEntity = new FakeEntity();
        FakeWorld world = new FakeWorld(liveItem, alreadyDeadItem, otherEntity);
        FakeServer server = new FakeServer(world);
        ItemCleanupService service = new ItemCleanupService(Logger.getLogger("test"), configPath, () -> server);

        service.load();
        for (int tick = 0; tick < 60 * 20; tick++) {
            service.runServerEndTick();
        }

        assertTrue(liveItem.isDead);
        assertTrue(alreadyDeadItem.isDead);
        assertFalse(otherEntity.isDead);
    }

    static final class FakeServer {
        public final Object[] worlds;
        private final FakePlayerList playerList = new FakePlayerList();

        FakeServer(Object... worlds) {
            this.worlds = worlds;
        }

        public FakePlayerList getPlayerList() {
            return playerList;
        }
    }

    static final class FakePlayerList {
        public List<Object> getPlayers() {
            return Collections.emptyList();
        }
    }

    static final class FakeWorld {
        public final List<Object> loadedEntityList = new ArrayList<Object>();

        FakeWorld(Object... entities) {
            Collections.addAll(loadedEntityList, entities);
        }
    }

    static final class FakeEntity {
        private boolean isDead;

        public void setDead() {
            isDead = true;
        }
    }
}
