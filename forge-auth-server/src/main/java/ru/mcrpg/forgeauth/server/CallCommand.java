package ru.mcrpg.forgeauth.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

final class CallCommand {

    private static final String COMMAND_NAME = "call";
    private static final String SUBJECT = "Телепорт";
    private static final List<String> ALIASES = Collections.unmodifiableList(Arrays.asList("tpa", "tpask"));
    private static final long REQUEST_TTL_MILLIS = TimeUnit.SECONDS.toMillis(120L);
    private static final ConcurrentMap<String, TeleportRequest> REQUESTS =
        new ConcurrentHashMap<String, TeleportRequest>();

    private CallCommand() {
    }

    static void register(FMLServerStartingEvent event) {
        try {
            Class<?> commandType = Class.forName("net.minecraft.command.ICommand");
            Object command = Proxy.newProxyInstance(
                CallCommand.class.getClassLoader(),
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
                execute(args[0], args[1], args[2]);
                return null;
            }
            if ("checkPermission".equals(name) || "func_184882_a".equals(name)) {
                return Boolean.TRUE;
            }
            if ("getTabCompletions".equals(name) || "func_184883_a".equals(name)) {
                return tabCompletions(args);
            }
            if ("isUsernameIndex".equals(name) || "func_82358_a".equals(name)) {
                return Boolean.valueOf(isUsernameIndex(args));
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

    private static void execute(Object server, Object sender, Object arguments) {
        Object player = TeleportSupport.resolvePlayer(sender);
        if (player == null) {
            ServerChat.status(sender, ServerChat.Tone.ERROR, SUBJECT, "команду " + ServerChat.command("/" + COMMAND_NAME) + " может использовать только игрок.");
            return;
        }

        cleanupExpired(System.currentTimeMillis());

        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];
        if (args.length == 0) {
            ServerChat.usage(player, usage());
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("accept".equals(action) || "yes".equals(action)) {
            acceptRequest(server, player, args.length >= 2 ? args[1] : null);
            return;
        }
        if ("deny".equals(action) || "no".equals(action) || "reject".equals(action)) {
            denyRequest(server, player, args.length >= 2 ? args[1] : null);
            return;
        }
        if ("cancel".equals(action)) {
            cancelRequests(server, player, args.length >= 2 ? args[1] : null);
            return;
        }
        if ("here".equals(action)) {
            if (args.length != 2) {
                ServerChat.usage(player, "/" + COMMAND_NAME + " here <игрок>");
                return;
            }
            sendRequest(server, player, args[1], TeleportDirection.TARGET_TO_REQUESTER);
            return;
        }

        if (args.length == 1) {
            sendRequest(server, player, args[0], TeleportDirection.TO_TARGET);
            return;
        }

        ServerChat.usage(player, usage());
    }

    private static void sendRequest(Object server, Object requester, String targetName, TeleportDirection direction) {
        Object target = TeleportSupport.findOnlinePlayer(server, targetName);
        if (target == null) {
            ServerChat.status(requester, ServerChat.Tone.WARNING, SUBJECT, "игрок " + ServerChat.value(targetName) + " не найден онлайн.");
            return;
        }

        String requesterName = TeleportSupport.playerName(requester);
        String resolvedTargetName = TeleportSupport.playerName(target);
        if (normalize(requesterName).equals(normalize(resolvedTargetName))) {
            ServerChat.status(requester, ServerChat.Tone.WARNING, SUBJECT, "нельзя отправить запрос самому себе.");
            return;
        }

        TeleportRequest request = new TeleportRequest(
            requesterName,
            resolvedTargetName,
            direction,
            System.currentTimeMillis() + REQUEST_TTL_MILLIS
        );
        REQUESTS.put(request.key(), request);

        if (direction == TeleportDirection.TO_TARGET) {
            ServerChat.status(
                requester,
                ServerChat.Tone.SUCCESS,
                SUBJECT,
                "запрос к " + ServerChat.value(resolvedTargetName) + " отправлен."
            );
            ServerChat.acceptDeny(
                target,
                ServerChat.value(requesterName) + " просит телепорт к вам.",
                "/call accept " + requesterName,
                "/call deny " + requesterName
            );
            return;
        }

        ServerChat.status(
            requester,
            ServerChat.Tone.SUCCESS,
            SUBJECT,
            "запрос телепортировать " + ServerChat.value(resolvedTargetName) + " к вам отправлен."
        );
        ServerChat.acceptDeny(
            target,
            ServerChat.value(requesterName) + " зовёт вас к себе.",
            "/call accept " + requesterName,
            "/call deny " + requesterName
        );
    }

    private static void acceptRequest(Object server, Object target, String requesterName) {
        PendingSelection selection = findPending(TeleportSupport.playerName(target), requesterName);
        if (!selection.isReady()) {
            ServerChat.warning(target, selection.message);
            return;
        }

        TeleportRequest request = selection.request;
        if (!REQUESTS.remove(request.key(), request)) {
            ServerChat.status(target, ServerChat.Tone.WARNING, SUBJECT, "запрос уже недоступен.");
            return;
        }

        Object requester = TeleportSupport.findOnlinePlayer(server, request.requesterName);
        if (requester == null) {
            ServerChat.status(target, ServerChat.Tone.WARNING, SUBJECT, "игрок " + ServerChat.value(request.requesterName) + " уже не онлайн.");
            return;
        }

        try {
            if (request.direction == TeleportDirection.TO_TARGET) {
                Object moved = TeleportSupport.teleportToPlayer(server, requester, target);
                ServerChat.status(moved, ServerChat.Tone.SUCCESS, SUBJECT, "игрок " + ServerChat.value(TeleportSupport.playerName(target)) + " принял запрос. Телепорт выполнен.");
                ServerChat.status(target, ServerChat.Tone.SUCCESS, SUBJECT, "вы приняли запрос от " + ServerChat.value(TeleportSupport.playerName(moved)) + ".");
                return;
            }

            Object moved = TeleportSupport.teleportToPlayer(server, target, requester);
            ServerChat.status(moved, ServerChat.Tone.SUCCESS, SUBJECT, "вы приняли запрос и телепортированы к " + ServerChat.value(TeleportSupport.playerName(requester)) + ".");
            ServerChat.status(requester, ServerChat.Tone.SUCCESS, SUBJECT, "игрок " + ServerChat.value(TeleportSupport.playerName(moved)) + " принял ваш запрос.");
        } catch (RuntimeException exception) {
            ServerChat.status(target, ServerChat.Tone.ERROR, SUBJECT, "не удалось выполнить телепорт: " + exception.getMessage());
            ServerChat.status(requester, ServerChat.Tone.ERROR, SUBJECT, "не удалось выполнить телепорт: " + exception.getMessage());
        }
    }

    private static void denyRequest(Object server, Object target, String requesterName) {
        PendingSelection selection = findPending(TeleportSupport.playerName(target), requesterName);
        if (!selection.isReady()) {
            ServerChat.warning(target, selection.message);
            return;
        }

        TeleportRequest request = selection.request;
        REQUESTS.remove(request.key(), request);
        ServerChat.status(target, ServerChat.Tone.WARNING, SUBJECT, "запрос от " + ServerChat.value(request.requesterName) + " отклонён.");

        Object requester = TeleportSupport.findOnlinePlayer(server, request.requesterName);
        if (requester != null) {
            ServerChat.status(requester, ServerChat.Tone.WARNING, SUBJECT, "игрок " + ServerChat.value(TeleportSupport.playerName(target)) + " отклонил запрос.");
        }
    }

    private static void cancelRequests(Object server, Object requester, String targetName) {
        String requesterKey = normalize(TeleportSupport.playerName(requester));
        String targetKey = targetName == null ? null : normalize(targetName);
        ArrayList<TeleportRequest> removed = new ArrayList<TeleportRequest>();

        for (TeleportRequest request : REQUESTS.values()) {
            if (!request.requesterKey.equals(requesterKey)) {
                continue;
            }
            if (targetKey != null && !request.targetKey.equals(targetKey)) {
                continue;
            }
            if (REQUESTS.remove(request.key(), request)) {
                removed.add(request);
            }
        }

        if (removed.isEmpty()) {
            ServerChat.status(requester, ServerChat.Tone.WARNING, SUBJECT, "активных исходящих запросов нет.");
            return;
        }

        ServerChat.status(requester, ServerChat.Tone.SUCCESS, SUBJECT, "отменено запросов: " + ServerChat.value(Integer.valueOf(removed.size())) + ".");
        for (TeleportRequest request : removed) {
            Object target = TeleportSupport.findOnlinePlayer(server, request.targetName);
            if (target != null) {
                ServerChat.status(target, ServerChat.Tone.WARNING, SUBJECT, "игрок " + ServerChat.value(TeleportSupport.playerName(requester)) + " отменил запрос.");
            }
        }
    }

    private static PendingSelection findPending(String targetName, String requesterName) {
        cleanupExpired(System.currentTimeMillis());

        String targetKey = normalize(targetName);
        String requesterKey = requesterName == null ? null : normalize(requesterName);
        ArrayList<TeleportRequest> matches = new ArrayList<TeleportRequest>();

        for (TeleportRequest request : REQUESTS.values()) {
            if (!request.targetKey.equals(targetKey)) {
                continue;
            }
            if (requesterKey != null && !request.requesterKey.equals(requesterKey)) {
                continue;
            }
            matches.add(request);
        }

        if (matches.isEmpty()) {
            return PendingSelection.message(ServerChat.statusText(SUBJECT, "активных входящих запросов нет."));
        }
        if (matches.size() > 1) {
            return PendingSelection.message(ServerChat.statusText(SUBJECT, "несколько запросов. Укажите ник: " + requesterList(matches) + "."));
        }
        return PendingSelection.ready(matches.get(0));
    }

    private static void cleanupExpired(long nowMillis) {
        Iterator<TeleportRequest> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next();
            if (request.expiresAtMillis <= nowMillis) {
                REQUESTS.remove(request.key(), request);
            }
        }
    }

    private static String requesterList(List<TeleportRequest> requests) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(requests.get(i).requesterName);
        }
        return builder.toString();
    }

    private static List<String> tabCompletions(Object[] methodArgs) {
        Object server = methodArgs != null && methodArgs.length > 0 ? methodArgs[0] : null;
        Object arguments = methodArgs != null && methodArgs.length > 2 ? methodArgs[2] : null;
        String[] args = arguments instanceof String[] ? (String[]) arguments : new String[0];

        if (args.length <= 1) {
            ArrayList<String> values = new ArrayList<String>();
            values.add("accept");
            values.add("deny");
            values.add("cancel");
            values.add("here");
            values.addAll(TeleportSupport.onlinePlayerNames(server));
            return matching(values, args.length == 0 ? "" : args[0]);
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && ("here".equals(action) || "accept".equals(action) || "deny".equals(action) || "cancel".equals(action))) {
            return matching(TeleportSupport.onlinePlayerNames(server), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        ArrayList<String> matches = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private static boolean isUsernameIndex(Object[] methodArgs) {
        if (methodArgs == null || methodArgs.length < 2 || !(methodArgs[0] instanceof String[]) || !(methodArgs[1] instanceof Number)) {
            return false;
        }

        String[] args = (String[]) methodArgs[0];
        int index = ((Number) methodArgs[1]).intValue();
        if (args.length == 0 || index < 0 || index >= args.length) {
            return false;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("here".equals(action) || "accept".equals(action) || "deny".equals(action) || "cancel".equals(action)) {
            return index == 1;
        }
        return index == 0;
    }

    private static String usage() {
        return "/" + COMMAND_NAME + " <игрок> | /" + COMMAND_NAME + " here <игрок> | /" +
            COMMAND_NAME + " accept [игрок] | /" + COMMAND_NAME + " deny [игрок] | /" +
            COMMAND_NAME + " cancel [игрок]";
    }

    private static String requestKey(String targetKey, String requesterKey) {
        return targetKey + "\n" + requesterKey;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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

    private enum TeleportDirection {
        TO_TARGET,
        TARGET_TO_REQUESTER
    }

    private static final class TeleportRequest {
        private final String requesterName;
        private final String targetName;
        private final String requesterKey;
        private final String targetKey;
        private final TeleportDirection direction;
        private final long expiresAtMillis;

        private TeleportRequest(String requesterName, String targetName, TeleportDirection direction, long expiresAtMillis) {
            this.requesterName = requesterName;
            this.targetName = targetName;
            this.requesterKey = normalize(requesterName);
            this.targetKey = normalize(targetName);
            this.direction = direction;
            this.expiresAtMillis = expiresAtMillis;
        }

        private String key() {
            return requestKey(targetKey, requesterKey);
        }
    }

    private static final class PendingSelection {
        private final TeleportRequest request;
        private final String message;

        private PendingSelection(TeleportRequest request, String message) {
            this.request = request;
            this.message = message;
        }

        private static PendingSelection ready(TeleportRequest request) {
            return new PendingSelection(request, null);
        }

        private static PendingSelection message(String message) {
            return new PendingSelection(null, message);
        }

        private boolean isReady() {
            return request != null;
        }
    }
}
