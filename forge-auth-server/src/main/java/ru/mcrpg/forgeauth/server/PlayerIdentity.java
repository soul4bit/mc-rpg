package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

final class PlayerIdentity {

    private PlayerIdentity() {
    }

    static String id(Object player) {
        Object uniqueId = invokeZeroArgIfPresent(player, "getUniqueID", "func_110124_au");
        if (uniqueId instanceof UUID) {
            return uniqueId.toString();
        }
        return "name:" + name(player).toLowerCase(Locale.ROOT);
    }

    static String name(Object player) {
        return TeleportSupport.playerName(player);
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                for (String methodName : methodNames) {
                    if (!methodName.equals(method.getName())) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        return method.invoke(target);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
