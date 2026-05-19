package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(service.isProtectedBlockPosition(overworld, new FakeBlockPos(0, 64, 0)));
        assertFalse(service.isProtectedBlockPosition(overworld, new FakeBlockPos(11, 64, 0)));
        assertFalse(service.isProtectedBlockPosition(overworld, new FakeBlockPos(0, 90, 0)));
        assertFalse(service.isProtectedBlockPosition(nether, new FakeBlockPos(0, 64, 0)));
    }

    @Test
    void obfuscatedWorldAndBlockPositionNamesAreSupported() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-spawn-protection.properties");
        SpawnProtectionService service = new SpawnProtectionService(Logger.getLogger("test"), configPath);
        service.setBoxRegion(0, -10.0D, 40.0D, -20.0D, 10.0D, 80.0D, 20.0D);

        assertTrue(service.isProtectedBlockPosition(new FakeObfuscatedWorld(0), new FakeObfuscatedBlockPos(0, 64, 0)));
        assertFalse(service.isProtectedBlockPosition(new FakeObfuscatedWorld(0), new FakeObfuscatedBlockPos(0, 90, 0)));
    }

    @Test
    void playerPositionProtectionDoesNotRequireReadableWorldField() throws Exception {
        Path configPath = tempDirectory.resolve("obsidiangate-spawn-protection.properties");
        SpawnProtectionService service = new SpawnProtectionService(Logger.getLogger("test"), configPath);
        service.setBoxRegion(0, -1436.0D, 0.0D, 1009.0D, -1116.0D, 255.0D, 1329.0D);

        FakeObfuscatedPlayer player = new FakeObfuscatedPlayer(0, -1276.0D, 66.0D, 1169.0D);

        assertTrue(service.isProtectedPlayerPosition(player));
        assertTrue(service.describePlayerPosition(player).contains("dim=0"));
        assertTrue(service.describePlayerPosition(player).contains("protected=true"));
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

    static final class FakeObfuscatedWorld {
        public final FakeObfuscatedProvider s;

        FakeObfuscatedWorld(int dimension) {
            this.s = new FakeObfuscatedProvider(dimension);
        }
    }

    static final class FakeObfuscatedProvider {
        private final int dimension;

        FakeObfuscatedProvider(int dimension) {
            this.dimension = dimension;
        }

        public int i() {
            return dimension;
        }
    }

    static final class FakeObfuscatedBlockPos {
        private final int x;
        private final int y;
        private final int z;

        FakeObfuscatedBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int p() {
            return x;
        }

        public int q() {
            return y;
        }

        public int r() {
            return z;
        }
    }

    static final class FakeObfuscatedPlayer {
        public final int field_71093_bK;
        public final double p;
        public final double q;
        public final double r;

        FakeObfuscatedPlayer(int dimension, double x, double y, double z) {
            this.field_71093_bK = dimension;
            this.p = x;
            this.q = y;
            this.r = z;
        }
    }
}
