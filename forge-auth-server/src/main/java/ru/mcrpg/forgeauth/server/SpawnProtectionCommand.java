package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class SpawnProtectionCommand {

    private static final String COMMAND_NAME = "spawnprotect";

    private SpawnProtectionCommand() {
    }

    static void register(FMLServerStartingEvent event, SpawnProtectionService service) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                SpawnProtectionCommand.class.getClassLoader(),
                new Class<?>[] { commandType },
                new Handler(service)
            );
            Method registerMethod = event.getClass().getMethod("registerServerCommand", commandType);
            registerMethod.invoke(event, command);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register /spawnprotect command.", exception);
        }
    }

    private static final class Handler implements InvocationHandler {
        private final SpawnProtectionService service;

        private Handler(SpawnProtectionService service) {
            this.service = service;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name) || "func_71517_b".equals(name)) {
                return COMMAND_NAME;
            }
            if ("getUsage".equals(name) || "func_71518_a".equals(name)) {
                return "/" + COMMAND_NAME + " <info|on|off|radius|reload>";
            }
            if ("getAliases".equals(name) || "func_71514_a".equals(name)) {
                return Collections.emptyList();
            }
            if ("execute".equals(name) || "func_184881_a".equals(name)) {
                execute(args[1], args[2]);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.valueOf(canUse(args[1]));
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
                return Boolean.valueOf(proxy == args[0]);
            }
            return defaultValue(method.getReturnType());
        }

        private void execute(Object sender, Object arguments) {
            if (!canUse(sender)) {
                ServerChat.error(sender, "Недостаточно прав для команды " + ServerChat.command("/" + COMMAND_NAME) + ".");
                return;
            }

            String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
            String action = args.length == 0 ? "info" : args[0].trim().toLowerCase();
            if ("info".equals(action)) {
                sendInfo(sender);
                return;
            }
            if ("on".equals(action)) {
                service.setEnabled(true);
                ServerChat.success(sender, "Защита спавна включена.");
                sendInfo(sender);
                return;
            }
            if ("off".equals(action)) {
                service.setEnabled(false);
                ServerChat.warning(sender, "Защита спавна выключена.");
                sendInfo(sender);
                return;
            }
            if ("reload".equals(action)) {
                service.load();
                ServerChat.success(sender, "Конфиг защиты спавна перезагружен.");
                sendInfo(sender);
                return;
            }
            if ("radius".equals(action)) {
                if (args.length < 2) {
                    ServerChat.warning(sender, "Использование: " + ServerChat.command("/" + COMMAND_NAME + " radius <блоки>"));
                    return;
                }
                try {
                    service.setRadius(Integer.parseInt(args[1]));
                    ServerChat.success(sender, "Радиус защиты спавна обновлён.");
                    sendInfo(sender);
                } catch (NumberFormatException ignored) {
                    ServerChat.error(sender, "Радиус должен быть числом.");
                }
                return;
            }
            ServerChat.warning(sender, "Использование: " + ServerChat.command("/" + COMMAND_NAME + " <info|on|off|radius|reload>"));
        }

        private void sendInfo(Object sender) {
            SpawnProtectionService.Config config = service.config();
            ServerChat.info(sender, String.format(
                "Защита спавна: %s, радиус %s, блоки %s, мобы %s, взрывы %s.",
                ServerChat.value(enabledText(config.enabled)),
                ServerChat.value(config.radius),
                ServerChat.value(enabledText(config.protectBlocks)),
                ServerChat.value(enabledText(config.denyHostileSpawns)),
                ServerChat.value(enabledText(config.denyExplosions))
            ));
        }
    }

    private static boolean canUse(Object sender) {
        if (sender == null) {
            return false;
        }
        Object result = invokeIfPresent(sender, new Object[] { Integer.valueOf(2), COMMAND_NAME }, "canUseCommand", "func_70003_b");
        return Boolean.TRUE.equals(result);
    }

    private static int compareTo(Object other) {
        if (other == null) {
            return 1;
        }
        Object otherName = invokeZeroArgIfPresent(other, "getName", "func_71517_b");
        return otherName == null ? 1 : COMMAND_NAME.compareTo(otherName.toString());
    }

    private static String enabledText(boolean value) {
        return value ? "вкл" : "выкл";
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
                        throw new IllegalStateException("Failed to invoke " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
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
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        return false;
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
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        return null;
    }
}
