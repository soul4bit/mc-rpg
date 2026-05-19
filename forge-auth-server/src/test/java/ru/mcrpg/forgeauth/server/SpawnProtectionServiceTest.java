package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnProtectionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void loadCreatesConfigWithRegionDefaults() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-spawn-protection.properties");
        SpawnProtectionService service = new SpawnProtectionService(Logger.getLogger("test"), configPath);

        service.load();

        assertTrue(Files.exists(configPath));
        assertEquals(SpawnProtectionService.REGION_MODE_RADIUS, service.config().regionMode);
        assertEquals(-96.0D, service.config().minX);
        assertEquals(255.0D, service.config().maxY);
    }

    @Test
    void boxRegionProtectsOnlyConfiguredCuboid() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-spawn-protection.properties");
        SpawnProtectionService service = new SpawnProtectionService(Logger.getLogger("test"), configPath);
        service.setBoxRegion(0, -10.0D, 40.0D, -20.0D, 10.0D, 80.0D, 20.0D);

        FakeWorld overworld = new FakeWorld(0);
        FakeWorld nether = new FakeWorld(-1);

        assertTrue(isProtected(service, overworld, new FakeBlockPos(0, 64, 0)));
        assertFalse(isProtected(service, overworld, new FakeBlockPos(11, 64, 0)));
        assertFalse(isProtected(service, overworld, new FakeBlockPos(0, 90, 0)));
        assertFalse(isProtected(service, nether, new FakeBlockPos(0, 64, 0)));
    }

    private static boolean isProtected(SpawnProtectionService service, Object world, Object pos) throws Exception {
        Method method = SpawnProtectionService.class.getDeclaredMethod(
            "isProtected",
            Object.class,
            Object.class,
            SpawnProtectionService.Config.class
        );
        method.setAccessible(true);
        return ((Boolean) method.invoke(service, world, pos, service.config())).booleanValue();
    }

    static final class FakeWorld {
        public final FakeProvider provider;

        FakeWorld(int dimension) {
            this.provider = new FakeProvider(dimension);
        }
    }

    static final class FakeProvider {
        private final int dimension;

        FakeProvider(int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }
    }

    static final class FakeBlockPos {
        private final int x;
        private final int y;
        private final int z;

        FakeBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}
