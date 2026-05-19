package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class SpawnProtectionCommand {

    private static final String COMMAND_NAME = "spawnprotect";
    private static final String SUBJECT = "Защита спавна";

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
            throw new IllegalStateException("Не удалось зарегистрировать команду /spawnprotect.", exception);
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
                return "/" + COMMAND_NAME + " <info|on|off|radius|center|worldspawn|region|reload>";
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
                ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "недостаточно прав для команды " + ServerChat.command("/" + COMMAND_NAME) + ".");
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
                ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "включена.");
                sendInfo(sender);
                return;
            }
            if ("off".equals(action)) {
                service.setEnabled(false);
                ServerChat.status(sender, ServerChat.Tone.WARNING, SUBJECT, "выключена.");
                sendInfo(sender);
                return;
            }
            if ("reload".equals(action)) {
                service.load();
                ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "конфиг перезагружен.");
                sendInfo(sender);
                return;
            }
            if ("center".equals(action)) {
                Object player = TeleportSupport.resolvePlayer(sender);
                if (player == null) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + COMMAND_NAME + " center") + " может использовать только игрок.");
                    return;
                }
                service.setFixedCenter(
                    TeleportSupport.playerDimension(player),
                    Math.floor(TeleportSupport.playerX(player)),
                    Math.floor(TeleportSupport.playerZ(player))
                );
                ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "центр установлен в текущей позиции.");
                sendInfo(sender);
                return;
            }
            if ("worldspawn".equals(action)) {
                service.useWorldSpawnCenter();
                ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "центр снова берётся из /setworldspawn.");
                sendInfo(sender);
                return;
            }
            if ("region".equals(action)) {
                setRegion(sender, args);
                return;
            }
            if ("radius".equals(action)) {
                if (args.length < 2) {
                    ServerChat.usage(sender, "/" + COMMAND_NAME + " radius <блоки>");
                    return;
                }
                try {
                    service.setRadius(Integer.parseInt(args[1]));
                    ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "радиус обновлён.");
                    sendInfo(sender);
                } catch (NumberFormatException ignored) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "радиус должен быть числом.");
                }
                return;
            }
            ServerChat.usage(sender, "/" + COMMAND_NAME + " <info|on|off|radius|center|worldspawn|region|reload>");
        }

        private void sendInfo(Object sender) {
            SpawnProtectionService.Config config = service.config();
            if (SpawnProtectionService.REGION_MODE_BOX.equals(config.regionMode)) {
                ServerChat.status(sender, SUBJECT, String.format(
                    "%s, приват dim %s [%s,%s,%s]..[%s,%s,%s], блоки %s, мобы %s, взрывы %s, обход OP %s.",
                    ServerChat.value(enabledText(config.enabled)),
                    ServerChat.value(config.dimension),
                    ServerChat.value(Integer.valueOf((int) Math.floor(config.minX))),
                    ServerChat.value(Integer.valueOf((int) Math.floor(config.minY))),
                    ServerChat.value(Integer.valueOf((int) Math.floor(config.minZ))),
                    ServerChat.value(Integer.valueOf((int) Math.floor(config.maxX))),
                    ServerChat.value(Integer.valueOf((int) Math.floor(config.maxY))),
                    ServerChat.value(Integer.valueOf((int) Math.floor(config.maxZ))),
                    ServerChat.value(enabledText(config.protectBlocks)),
                    ServerChat.value(enabledText(config.denyHostileSpawns)),
                    ServerChat.value(enabledText(config.denyExplosions)),
                    ServerChat.value(enabledText(config.allowOperatorBypass))
                ));
                return;
            }
            ServerChat.status(sender, SUBJECT, String.format(
                "%s, радиус %s, центр %s dim %s (%s, %s), блоки %s, мобы %s, взрывы %s, обход OP %s.",
                ServerChat.value(enabledText(config.enabled)),
                ServerChat.value(config.radius),
                ServerChat.value(config.centerMode),
                ServerChat.value(config.dimension),
                ServerChat.value(Integer.valueOf((int) Math.floor(config.centerX))),
                ServerChat.value(Integer.valueOf((int) Math.floor(config.centerZ))),
                ServerChat.value(enabledText(config.protectBlocks)),
                ServerChat.value(enabledText(config.denyHostileSpawns)),
                ServerChat.value(enabledText(config.denyExplosions)),
                ServerChat.value(enabledText(config.allowOperatorBypass))
            ));
        }

        private void setRegion(Object sender, String[] args) {
            if (args.length >= 3 && "here".equals(args[1].trim().toLowerCase())) {
                Object player = TeleportSupport.resolvePlayer(sender);
                if (player == null) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + COMMAND_NAME + " region here <радиус>") + " может использовать только игрок.");
                    return;
                }
                try {
                    int radius = Integer.parseInt(args[2]);
                    int dimension = TeleportSupport.playerDimension(player);
                    double centerX = Math.floor(TeleportSupport.playerX(player));
                    double centerZ = Math.floor(TeleportSupport.playerZ(player));
                    service.setBoxRegion(
                        dimension,
                        centerX - radius,
                        0.0D,
                        centerZ - radius,
                        centerX + radius,
                        255.0D,
                        centerZ + radius
                    );
                    ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "приват создан вокруг текущей позиции.");
                    sendInfo(sender);
                } catch (NumberFormatException ignored) {
                    ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "радиус должен быть числом.");
                }
                return;
            }

            if (args.length < 7) {
                ServerChat.usage(sender, "/" + COMMAND_NAME + " region here <радиус>");
                ServerChat.usage(sender, "/" + COMMAND_NAME + " region <x1> <y1> <z1> <x2> <y2> <z2> [dim]");
                return;
            }

            try {
                double x1 = Double.parseDouble(args[1]);
                double y1 = Double.parseDouble(args[2]);
                double z1 = Double.parseDouble(args[3]);
                double x2 = Double.parseDouble(args[4]);
                double y2 = Double.parseDouble(args[5]);
                double z2 = Double.parseDouble(args[6]);
                int dimension = args.length >= 8 ? Integer.parseInt(args[7]) : dimensionFromSender(sender);
                service.setBoxRegion(dimension, x1, y1, z1, x2, y2, z2);
                ServerChat.status(sender, ServerChat.Tone.SUCCESS, SUBJECT, "приват региона обновлён.");
                sendInfo(sender);
            } catch (NumberFormatException ignored) {
                ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "координаты региона должны быть числами.");
            }
        }

        private int dimensionFromSender(Object sender) {
            Object player = TeleportSupport.resolvePlayer(sender);
            return player == null ? 0 : TeleportSupport.playerDimension(player);
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
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
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
