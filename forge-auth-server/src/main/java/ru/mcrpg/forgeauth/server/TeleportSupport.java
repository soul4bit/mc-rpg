package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class TeleportSupport {

    static final double WORLD_BORDER_LIMIT = 29999984.0D;
    static final double MIN_Y = 0.0D;
    static final double MAX_Y = 256.0D;

    private static final String PLAYER_CLASS_NAME = "net.minecraft.entity.player.EntityPlayerMP";

    private TeleportSupport() {
    }

    static Object resolvePlayer(Object sender) {
        if (isPlayer(sender)) {
            return sender;
        }
        Object entity = invokeZeroArgIfPresent(sender, "getCommandSenderEntity", "func_174793_f");
        return isPlayer(entity) ? entity : null;
    }

    static Object findOnlinePlayer(Object server, String username) {
        if (server == null || username == null || username.trim().isEmpty()) {
            return null;
        }

        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        Object player = invokeIfPresent(playerList, new Object[] { username }, "getPlayerByUsername", "func_152612_a");
        if (isPlayer(player)) {
            return player;
        }

        for (Object onlinePlayer : onlinePlayers(playerList)) {
            String onlineName = playerName(onlinePlayer);
            if (onlineName.equalsIgnoreCase(username)) {
                return onlinePlayer;
            }
        }

        for (String onlineName : onlinePlayerNames(server)) {
            if (onlineName.equalsIgnoreCase(username)) {
                player = invokeIfPresent(playerList, new Object[] { onlineName }, "getPlayerByUsername", "func_152612_a");
                return isPlayer(player) ? player : null;
            }
        }
        return null;
    }

    static List<String> onlinePlayerNames(Object server) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        Object rawNames = invokeZeroArgIfPresent(server, "getOnlinePlayerNames", "func_71213_z");
        if (rawNames instanceof String[]) {
            String[] onlineNames = (String[]) rawNames;
            for (String name : onlineNames) {
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }

        Object playerList = invokeZeroArgIfPresent(server, "getPlayerList", "func_184103_al");
        for (Object player : onlinePlayers(playerList)) {
            String name = playerName(player);
            if (!"unknown".equals(name)) {
                names.add(name);
            }
        }

        ArrayList<String> sorted = new ArrayList<String>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    static boolean isPlayer(Object value) {
        if (value == null) {
            return false;
        }

        Class<?> type = value.getClass();
        while (type != null) {
            if (PLAYER_CLASS_NAME.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    static String playerName(Object player) {
        Object name = invokeZeroArgIfPresent(player, "getName", "func_70005_c_");
        return name == null ? "unknown" : name.toString();
    }

    static double playerX(Object player) {
        return readDoubleField(player, 0.0D, "posX", "field_70165_t");
    }

    static double playerY(Object player) {
        return readDoubleField(player, 0.0D, "posY", "field_70163_u");
    }

    static double playerZ(Object player) {
        return readDoubleField(player, 0.0D, "posZ", "field_70161_v");
    }

    static float playerYaw(Object player) {
        return readFloatField(player, 0.0F, "rotationYaw", "field_70177_z");
    }

    static float playerPitch(Object player) {
        return readFloatField(player, 0.0F, "rotationPitch", "field_70125_A");
    }

    static int playerDimension(Object player) {
        return readIntField(player, 0, "dimension", "field_71093_bK");
    }

    static Object teleportToPlayer(Object movingPlayer, Object destinationPlayer) {
        if (movingPlayer == null || destinationPlayer == null) {
            throw new IllegalArgumentException("Игрок для телепортации не найден.");
        }

        Object currentPlayer = movingPlayer;
        invokeZeroArgIfPresent(currentPlayer, "dismountRidingEntity", "func_184210_p");

        int destinationDimension = playerDimension(destinationPlayer);
        if (playerDimension(currentPlayer) != destinationDimension) {
            Object transferredPlayer = invokeIfPresent(
                currentPlayer,
                new Object[] { Integer.valueOf(destinationDimension) },
                "changeDimension",
                "func_184204_a"
            );
            if (transferredPlayer != null) {
                currentPlayer = transferredPlayer;
            }
        }

        teleport(
            currentPlayer,
            playerX(destinationPlayer),
            playerY(destinationPlayer),
            playerZ(destinationPlayer),
            playerYaw(destinationPlayer),
            playerPitch(destinationPlayer)
        );
        return currentPlayer;
    }

    static void teleport(Object player, double x, double y, double z, float yaw, float pitch) {
        invokeZeroArgIfPresent(player, "dismountRidingEntity", "func_184210_p");

        Object connection = readFieldIfPresent(player, "connection", "field_71135_a");
        if (connection != null && invokeMethodIfPresent(connection, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setPlayerLocation", "func_147364_a")) {
            resetMotion(player);
            return;
        }

        if (invokeMethodIfPresent(player, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z),
            Float.valueOf(yaw),
            Float.valueOf(pitch)
        }, "setLocationAndAngles", "func_70080_a")) {
            resetMotion(player);
            return;
        }

        if (invokeMethodIfPresent(player, new Object[] {
            Double.valueOf(x),
            Double.valueOf(y),
            Double.valueOf(z)
        }, "setPositionAndUpdate", "func_70634_a")) {
            setFloatField(player, yaw, "rotationYaw", "field_70177_z");
            setFloatField(player, pitch, "rotationPitch", "field_70125_A");
            resetMotion(player);
            return;
        }

        throw new IllegalStateException("Не найден метод телепортации.");
    }

    static void resetMotion(Object player) {
        setFloatField(player, 0.0F, "fallDistance", "field_70143_R");
        setDoubleField(player, 0.0D, "motionX", "field_70159_w");
        setDoubleField(player, 0.0D, "motionY", "field_70181_x");
        setDoubleField(player, 0.0D, "motionZ", "field_70179_y");
    }

    static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static List<Object> onlinePlayers(Object playerList) {
        if (playerList == null) {
            return Collections.emptyList();
        }

        Object rawPlayers = invokeZeroArgIfPresent(playerList, "getPlayers", "func_181057_v");
        if (!(rawPlayers instanceof Iterable<?>)) {
            return Collections.emptyList();
        }

        ArrayList<Object> players = new ArrayList<Object>();
        for (Object player : (Iterable<?>) rawPlayers) {
            if (isPlayer(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean invokeMethodIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return false;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(target, safeArgs);
                        return true;
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean methodMatches(Method method, Object[] args, String... methodNames) {
        if (method.getParameterTypes().length != args.length) {
            return false;
        }
        boolean nameMatches = false;
        for (String methodName : methodNames) {
            if (methodName.equals(method.getName())) {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches) {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignable(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(value.getClass());
        }
        if (parameterType == Integer.TYPE) {
            return value instanceof Integer;
        }
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
        }
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
        }
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        if (parameterType == Long.TYPE) {
            return value instanceof Long;
        }
        if (parameterType == Short.TYPE) {
            return value instanceof Short;
        }
        if (parameterType == Byte.TYPE) {
            return value instanceof Byte;
        }
        if (parameterType == Character.TYPE) {
            return value instanceof Character;
        }
        return false;
    }

    private static Object readFieldIfPresent(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), fieldNames);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось прочитать поле " + field.getName() + ".", exception);
        }
    }

    private static int readIntField(Object target, int fallback, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static double readDoubleField(Object target, double fallback, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static float readFloatField(Object target, float fallback, String... fieldNames) {
        Object value = readFieldIfPresent(target, fieldNames);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static void setFloatField(Object target, float value, String... fieldNames) {
        setFieldIfPresent(target, Float.valueOf(value), fieldNames);
    }

    private static void setDoubleField(Object target, double value, String... fieldNames) {
        setFieldIfPresent(target, Double.valueOf(value), fieldNames);
    }

    private static void setFieldIfPresent(Object target, Object value, String... fieldNames) {
        if (target == null) {
            return;
        }
        Field field = findField(target.getClass(), fieldNames);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось записать поле " + field.getName() + ".", exception);
        }
    }

    private static Field findField(Class<?> type, String... fieldNames) {
        Class<?> current = type;
        while (current != null) {
            for (String fieldName : fieldNames) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
