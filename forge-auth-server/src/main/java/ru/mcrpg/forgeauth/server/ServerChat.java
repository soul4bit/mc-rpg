package ru.mcrpg.forgeauth.server;

import java.lang.reflect.Method;

final class ServerChat {

    private static final String PREFIX = "\u00A78[\u00A76ObsidianGate\u00A78]\u00A7r ";
    private static final String SUCCESS = "\u00A7a";
    private static final String WARNING = "\u00A7e";
    private static final String ERROR = "\u00A7c";
    private static final String INFO = "\u00A7b";
    private static final String COMMAND = "\u00A76";
    private static final String VALUE = "\u00A7f";
    private static final String RESET = "\u00A7r";

    private ServerChat() {
    }

    static void success(Object sender, String message) {
        send(sender, SUCCESS + message);
    }

    static void warning(Object sender, String message) {
        send(sender, WARNING + message);
    }

    static void error(Object sender, String message) {
        send(sender, ERROR + message);
    }

    static void info(Object sender, String message) {
        send(sender, INFO + message);
    }

    static String command(String value) {
        return COMMAND + value + RESET;
    }

    static String value(Object value) {
        return VALUE + String.valueOf(value) + RESET;
    }

    private static void send(Object sender, String message) {
        if (sender == null) {
            return;
        }
        try {
            Object textComponent = Class.forName("net.minecraft.util.text.TextComponentString")
                .getConstructor(String.class)
                .newInstance(PREFIX + message);
            invokeIfPresent(sender, new Object[] { textComponent }, "sendMessage", "func_145747_a");
        } catch (ReflectiveOperationException ignored) {
        }
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
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
        }
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
        }
        return false;
    }
}
