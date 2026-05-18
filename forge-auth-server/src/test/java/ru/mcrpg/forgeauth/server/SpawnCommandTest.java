package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class SpawnCommandTest {

    @Test
    void teleportStopsAfterVoidConnectionMethod() throws Exception {
        FakePlayer player = new FakePlayer();

        invokeTeleport(player, 12.5D, 70.0D, -4.5D, 90.0F, 15.0F);

        assertEquals(12.5D, player.connection.x);
        assertEquals(70.0D, player.connection.y);
        assertEquals(-4.5D, player.connection.z);
        assertEquals(90.0F, player.connection.yaw);
        assertEquals(15.0F, player.connection.pitch);
        assertFalse(player.positionAndUpdateCalled);
        assertFalse(player.locationAndAnglesCalled);
    }

    private static void invokeTeleport(Object player, double x, double y, double z, float yaw, float pitch) throws Exception {
        Method teleport = SpawnCommand.class.getDeclaredMethod(
            "teleport",
            Object.class,
            Double.TYPE,
            Double.TYPE,
            Double.TYPE,
            Float.TYPE,
            Float.TYPE
        );
        teleport.setAccessible(true);
        teleport.invoke(null, player, x, y, z, yaw, pitch);
    }

    static final class FakePlayer {
        public final FakeConnection connection = new FakeConnection();
        private boolean positionAndUpdateCalled;
        private boolean locationAndAnglesCalled;

        public void setPositionAndUpdate(double x, double y, double z) {
            this.positionAndUpdateCalled = true;
        }

        public void setLocationAndAngles(double x, double y, double z, float yaw, float pitch) {
            this.locationAndAnglesCalled = true;
        }
    }

    static final class FakeConnection {
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        public void setPlayerLocation(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
