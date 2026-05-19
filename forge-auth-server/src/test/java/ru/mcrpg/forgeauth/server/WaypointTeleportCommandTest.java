package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class WaypointTeleportCommandTest {

    @Test
    void coordinateParserAcceptsRelativeMarker() throws Exception {
        assertEquals(64.5D, parseCoordinate("~", 64.5D));
        assertEquals(66.0D, parseCoordinate("~1.5", 64.5D));
        assertEquals(12.25D, parseCoordinate("12.25", 64.5D));
    }

    private static double parseCoordinate(String rawValue, double fallback) throws Exception {
        Method method = WaypointTeleportCommand.class.getDeclaredMethod(
            "parseCoordinate",
            String.class,
            String.class,
            Double.TYPE
        );
        method.setAccessible(true);
        return ((Double) method.invoke(null, rawValue, "y", Double.valueOf(fallback))).doubleValue();
    }
}
