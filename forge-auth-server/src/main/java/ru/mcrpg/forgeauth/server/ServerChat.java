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
    private static final String BUTTON_FRAME = "\u00A78";

    private ServerChat() {
    }

    enum Tone {
        SUCCESS(ServerChat.SUCCESS),
        WARNING(ServerChat.WARNING),
        ERROR(ServerChat.ERROR),
        INFO(ServerChat.INFO);

        private final String color;

        Tone(String color) {
            this.color = color;
        }
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

    static void usage(Object sender, String usage) {
        warning(sender, "Использование: " + command(usage));
    }

    static void status(Object sender, String subject, String detail) {
        status(sender, Tone.INFO, subject, detail);
    }

    static void status(Object sender, Tone tone, String subject, String detail) {
        send(sender, tone.color + statusText(subject, detail));
    }

    static void countdown(Object sender, String subject, int seconds) {
        warning(sender, countdownText(subject, seconds));
    }

    static void acceptDeny(Object sender, String message, String acceptCommand, String denyCommand) {
        actionPair(
            sender,
            message,
            "Принять",
            acceptCommand,
            SUCCESS,
            "Выполнить " + command(acceptCommand),
            "Отклонить",
            denyCommand,
            ERROR,
            "Выполнить " + command(denyCommand)
        );
    }

    static String command(String value) {
        return COMMAND + value + RESET;
    }

    static String value(Object value) {
        return VALUE + String.valueOf(value) + RESET;
    }

    static String statusText(String subject, String detail) {
        return subject + ": " + detail;
    }

    static String countdownText(String subject, int seconds) {
        return subject + " через " + duration(seconds) + ".";
    }

    private static void send(Object sender, String message) {
        if (sender == null) {
            return;
        }
        try {
            sendComponent(sender, component(PREFIX + message));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void actionPair(
        Object sender,
        String message,
        String acceptLabel,
        String acceptCommand,
        String acceptColor,
        String acceptHover,
        String denyLabel,
        String denyCommand,
        String denyColor,
        String denyHover
    ) {
        if (sender == null) {
            return;
        }

        try {
            Object root = component(PREFIX + INFO + message + RESET + " ");
            appendSibling(root, actionButton(acceptLabel, acceptCommand, acceptColor, acceptHover));
            appendSibling(root, component(" "));
            appendSibling(root, actionButton(denyLabel, denyCommand, denyColor, denyHover));
            sendComponent(sender, root);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            info(sender, message + " " + command(acceptCommand) + " или " + command(denyCommand) + ".");
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object actionButton(String label, String command, String color, String hover) throws ReflectiveOperationException {
        Object button = component(BUTTON_FRAME + "[" + color + label + BUTTON_FRAME + "]" + RESET);
        Object style = Class.forName("net.minecraft.util.text.Style").getConstructor().newInstance();

        Class<?> clickEventType = Class.forName("net.minecraft.util.text.event.ClickEvent");
        Class<?> clickActionType = Class.forName("net.minecraft.util.text.event.ClickEvent$Action");
        Object runCommand = Enum.valueOf((Class<Enum>) clickActionType.asSubclass(Enum.class), "RUN_COMMAND");
        Object clickEvent = clickEventType
            .getConstructor(clickActionType, String.class)
            .newInstance(runCommand, command);
        invokeIfPresent(style, new Object[] { clickEvent }, "setClickEvent", "func_150241_a");

        Class<?> hoverEventType = Class.forName("net.minecraft.util.text.event.HoverEvent");
        Class<?> hoverActionType = Class.forName("net.minecraft.util.text.event.HoverEvent$Action");
        Class<?> textComponentType = Class.forName("net.minecraft.util.text.ITextComponent");
        Object showText = Enum.valueOf((Class<Enum>) hoverActionType.asSubclass(Enum.class), "SHOW_TEXT");
        Object hoverEvent = hoverEventType
            .getConstructor(hoverActionType, textComponentType)
            .newInstance(showText, component(hover));
        invokeIfPresent(style, new Object[] { hoverEvent }, "setHoverEvent", "func_150209_a");
        invokeIfPresent(button, new Object[] { style }, "setStyle", "func_150255_a");
        return button;
    }

    private static Object component(String text) throws ReflectiveOperationException {
        return Class.forName("net.minecraft.util.text.TextComponentString")
            .getConstructor(String.class)
            .newInstance(text);
    }

    private static void appendSibling(Object target, Object sibling) {
        invokeIfPresent(target, new Object[] { sibling }, "appendSibling", "func_150257_a");
    }

    private static void sendComponent(Object sender, Object textComponent) {
        invokeIfPresent(sender, new Object[] { textComponent }, "sendMessage", "func_145747_a");
    }

    private static String duration(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        if (safeSeconds >= 60 && safeSeconds % 60 == 0) {
            int minutes = safeSeconds / 60;
            return minutes + " " + plural(minutes, "минуту", "минуты", "минут");
        }
        return safeSeconds + " " + plural(safeSeconds, "секунду", "секунды", "секунд");
    }

    private static String plural(int value, String one, String few, String many) {
        int lastTwoDigits = Math.abs(value) % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return many;
        }
        int lastDigit = Math.abs(value) % 10;
        if (lastDigit == 1) {
            return one;
        }
        if (lastDigit >= 2 && lastDigit <= 4) {
            return few;
        }
        return many;
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
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
        }
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
        }
        return false;
    }
}
