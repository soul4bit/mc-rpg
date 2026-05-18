package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class WaypointTeleportCommand {

    private static final String COMMAND_NAME = "wptp";
    private static final List<String> ALIASES = Collections.unmodifiableList(Arrays.asList("waypointtp", "xatp"));

    private WaypointTeleportCommand() {
    }

    static void register(FMLServerStartingEvent event) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                WaypointTeleportCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler()
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Не удалось зарегистрировать команду /" + COMMAND_NAME + ".", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return COMMAND_NAME;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return usage();
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return ALIASES;
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[1], args[2]);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("isUsernameIndex".equals(name) || "func_82358_a".equals(name)) {
                return Boolean.FALSE;
            }
            if ("compareTo".equals(name)) {
                return Integer.valueOf(compareTo(args == null ? null : args[0]));
            }
            if ("toString".equals(name)) {
                return "/" + COMMAND_NAME;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(COMMAND_NAME.hashCode());
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static void execute(Object sender, Object arguments) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.error(sender, "Команду " + ServerChat.command("/" + COMMAND_NAME) + " может использовать только игрок.");
            return;
        }

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length < 3 || args.length > 5) {
            ServerChat.warning(player, "Использование: " + ServerChat.command(usage()));
            return;
        }

        try {
            double x = parseCoordinate(args[0], "x");
            double y = parseCoordinate(args[1], "y");
            double z = parseCoordinate(args[2], "z");
            validatePosition(x, y, z);

            float yaw = args.length >= 4 ? parseRotation(args[3], "yaw", TeleportSupport.playerYaw(player)) : TeleportSupport.playerYaw(player);
            float pitch = args.length >= 5 ? parseRotation(args[4], "pitch", TeleportSupport.playerPitch(player)) : TeleportSupport.playerPitch(player);
            pitch = clampPitch(pitch);

            TeleportSupport.teleport(player, x, y, z, yaw, pitch);
            ServerChat.success(player, "Телепорт к чекпоинту выполнен.");
        } catch (IllegalArgumentException exception) {
            ServerChat.warning(player, exception.getMessage());
        } catch (RuntimeException exception) {
            ServerChat.error(player, "Не удалось телепортироваться к чекпоинту: " + exception.getMessage());
        }
    }

    private static double parseCoordinate(String rawValue, String name) {
        try {
            double value = Double.parseDouble(rawValue);
            if (!TeleportSupport.isFinite(value)) {
                throw new IllegalArgumentException("Координата " + name + " должна быть обычным числом.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Координата " + name + " должна быть числом.");
        }
    }

    private static float parseRotation(String rawValue, String name, float fallback) {
        if ("~".equals(rawValue)) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(rawValue);
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Поворот " + name + " должен быть обычным числом.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Поворот " + name + " должен быть числом.");
        }
    }

    private static void validatePosition(double x, double y, double z) {
        if (Math.abs(x) > TeleportSupport.WORLD_BORDER_LIMIT || Math.abs(z) > TeleportSupport.WORLD_BORDER_LIMIT) {
            throw new IllegalArgumentException("Координаты x/z слишком далеко от мира.");
        }
        if (y < TeleportSupport.MIN_Y || y > TeleportSupport.MAX_Y) {
            throw new IllegalArgumentException("Координата y должна быть от 0 до 256.");
        }
    }

    private static float clampPitch(float pitch) {
        if (pitch < -90.0F) {
            return -90.0F;
        }
        if (pitch > 90.0F) {
            return 90.0F;
        }
        return pitch;
    }

    private static String usage() {
        return "/" + COMMAND_NAME + " <x> <y> <z> [yaw] [pitch]";
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeZeroArgIfPresent(other, "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0) {
                    for (String methodName : methodNames) {
                        if (methodName.equals(method.getName())) {
                            try {
                                method.setAccessible(true);
                                return method.invoke(target);
                            } catch (ReflectiveOperationException exception) {
                                throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
                            }
                        }
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0.0F);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0.0D);
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Short.TYPE) {
            return Short.valueOf((short) 0);
        }
        if (type == Byte.TYPE) {
            return Byte.valueOf((byte) 0);
        }
        if (type == Character.TYPE) {
            return Character.valueOf('\0');
        }
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        return null;
    }
}
