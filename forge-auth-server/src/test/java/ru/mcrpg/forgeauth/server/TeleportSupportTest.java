package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class TeleportSupportTest {

    @Test
    void changeDimensionUsesForgeTeleporterWhenAvailable() throws Exception {
        FakeDimensionPlayer player = new FakeDimensionPlayer();

        Object moved = TeleportSupport.changeDimension(player, 0, 12.5D, 70.0D, -4.5D, 90.0F, 15.0F);

        assertSame(player, moved);
        assertEquals(0, player.dimension);
        assertFalse(player.vanillaChangeDimensionCalled);
        assertNotNull(player.forgeTeleporter);
        assertEquals(Boolean.FALSE, invokeZeroArg(player.forgeTeleporter, "isVanilla"));
    }

    @Test
    void forgeTeleporterPlacesEntityAtRequestedLocation() throws Exception {
        FakeDimensionPlayer player = new FakeDimensionPlayer();
        TeleportSupport.changeDimension(player, 0, 12.5D, 70.0D, -4.5D, 90.0F, 15.0F);

        Class<?> worldType = Class.forName("amu");
        Class<?> entityType = Class.forName("vg");
        Object world = worldType.getDeclaredConstructor().newInstance();
        Object entity = entityType.getDeclaredConstructor().newInstance();
        Method placeEntity = player.forgeTeleporter.getClass().getMethod(
            "placeEntity",
            worldType,
            entityType,
            Float.TYPE
        );

        placeEntity.invoke(player.forgeTeleporter, world, entity, Float.valueOf(90.0F));

        assertTrue((Boolean) readField(entity, "locationAndAnglesCalled"));
        assertEquals(12.5D, (Double) readField(entity, "x"));
        assertEquals(70.0D, (Double) readField(entity, "y"));
        assertEquals(-4.5D, (Double) readField(entity, "z"));
        assertEquals(90.0F, (Float) readField(entity, "yaw"));
        assertEquals(15.0F, (Float) readField(entity, "pitch"));
    }

    private static Object invokeZeroArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getField(fieldName);
        return field.get(target);
    }

    static final class FakeDimensionPlayer {
        private int dimension = 1;
        private Object forgeTeleporter;
        private boolean vanillaChangeDimensionCalled;

        public void dismountRidingEntity() {
        }

        public Object changeDimension(int destinationDimension, Object forgeTeleporter) {
            this.dimension = destinationDimension;
            this.forgeTeleporter = forgeTeleporter;
            return this;
        }

        public Object changeDimension(int destinationDimension) {
            this.vanillaChangeDimensionCalled = true;
            this.dimension = destinationDimension;
            return this;
        }
    }
}
