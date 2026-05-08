package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.mcrpg.gameauth.TicketVerificationClient;
import ru.mcrpg.gameauth.TicketVerificationResult;

final class ForgeAuthServerLifecycle {

    private static final String DISCONNECT_MESSAGE = "Authentication via the ObsidianGate launcher is required.";

    private final Logger logger;
    private final AuthServerConfig config;
    private final MinecraftPlayerBridge playerBridge;
    private final TicketVerificationClient verificationClient;
    private final ExecutorService verificationExecutor;
    private final Map<String, PlayerAuthState> authStates;
    private final Queue<Runnable> mainThreadActions;

    private int serverTickCounter;

    ForgeAuthServerLifecycle(Logger logger) {
        this(
            logger,
            AuthServerConfig.fromSystem(),
            new MinecraftPlayerBridge(),
            new TicketVerificationClient(),
            newSingleThreadExecutor(),
            new ConcurrentHashMap<String, PlayerAuthState>(),
            new ConcurrentLinkedQueue<Runnable>()
        );
    }

    ForgeAuthServerLifecycle(
        Logger logger,
        AuthServerConfig config,
        MinecraftPlayerBridge playerBridge,
        TicketVerificationClient verificationClient,
        ExecutorService verificationExecutor,
        Map<String, PlayerAuthState> authStates,
        Queue<Runnable> mainThreadActions
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.playerBridge = Objects.requireNonNull(playerBridge, "playerBridge");
        this.verificationClient = Objects.requireNonNull(verificationClient, "verificationClient");
        this.verificationExecutor = Objects.requireNonNull(verificationExecutor, "verificationExecutor");
        this.authStates = Objects.requireNonNull(authStates, "authStates");
        this.mainThreadActions = Objects.requireNonNull(mainThreadActions, "mainThreadActions");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Object player = playerBridge.extractPlayerFromEvent(event);
        String username = playerBridge.extractUsername(player);
        if (username.isEmpty()) {
            logger.warning("Joined player without a readable username. Launcher auth timeout cannot be tracked.");
            return;
        }

        authStates.put(normalizeKey(username), new PlayerAuthState(player, username, Instant.now()));
        logger.info(String.format(
            "Player %s joined. Waiting up to %d seconds for launcher auth proof.",
            username,
            config.getGraceSeconds()
        ));
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Object player = playerBridge.extractPlayerFromEvent(event);
        String username = playerBridge.extractUsername(player);
        if (!username.isEmpty()) {
            authStates.remove(normalizeKey(username));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Runnable action;
        while ((action = mainThreadActions.poll()) != null) {
            action.run();
        }

        serverTickCounter++;
        if (serverTickCounter % 20 != 0) {
            return;
        }

        Instant now = Instant.now();
        for (PlayerAuthState state : authStates.values()) {
            if (state.verified) {
                continue;
            }
            long elapsedSeconds = Duration.between(state.connectedAt, now).getSeconds();
            if (elapsedSeconds >= config.getGraceSeconds()) {
                failAndDisconnect(state.username, "No launcher ticket was received in time.");
            }
        }
    }

    void onTicketMessage(AuthTicketMessage message, Object ctx) {
        Object player = playerBridge.extractPlayerFromMessageContext(ctx);
        String username = playerBridge.extractUsername(player);
        if (username.isEmpty()) {
            logger.warning("Received launcher auth packet from a player with unreadable username.");
            return;
        }

        String key = normalizeKey(username);
        PlayerAuthState state = authStates.computeIfAbsent(key, unused -> new PlayerAuthState(player, username, Instant.now()));
        state.player = player;

        if (!config.isReady()) {
            failAndDisconnect(username, "Server auth bridge is not configured.");
            return;
        }

        if (!message.isComplete()) {
            failAndDisconnect(username, "Launcher ticket packet is incomplete.");
            return;
        }

        if (!config.acceptsServerId(message.getServerId())) {
            failAndDisconnect(username, "Launcher ticket targeted a different server id.");
            return;
        }

        if (state.verified) {
            logger.info(String.format("Ignoring duplicate launcher auth packet from %s.", username));
            return;
        }

        if (state.verificationInFlight) {
            logger.info(String.format("Ignoring concurrent launcher auth packet from %s.", username));
            return;
        }

        state.verificationInFlight = true;
        verificationExecutor.submit(() -> verifyTicketAsync(key, state.username, message));
    }

    void shutdown() {
        verificationExecutor.shutdownNow();
        authStates.clear();
        mainThreadActions.clear();
    }

    private void verifyTicketAsync(String key, String expectedUsername, AuthTicketMessage message) {
        try {
            TicketVerificationResult result = verificationClient.verify(
                config.getAuthBaseUrl(),
                message.getTicket(),
                config.getServerId()
            );
            mainThreadActions.add(() -> applyVerificationResult(key, expectedUsername, result));
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Launcher ticket verification request failed for " + expectedUsername + ".", exception);
            mainThreadActions.add(() -> failAndDisconnect(expectedUsername, "Auth API verification request failed."));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Unexpected launcher auth verification failure for " + expectedUsername + ".", exception);
            mainThreadActions.add(() -> failAndDisconnect(expectedUsername, "Unexpected launcher auth verification failure."));
        }
    }

    private void applyVerificationResult(String key, String expectedUsername, TicketVerificationResult result) {
        PlayerAuthState state = authStates.get(key);
        if (state == null) {
            return;
        }

        state.verificationInFlight = false;
        if (!result.isValid()) {
            failAndDisconnect(expectedUsername, "Ticket rejected: " + normalizeReason(result.getReason()));
            return;
        }

        if (!expectedUsername.equalsIgnoreCase(result.getUsername())) {
            failAndDisconnect(expectedUsername, "Ticket belongs to another username.");
            return;
        }

        state.verified = true;
        logger.info(String.format(
            "Launcher auth accepted for %s. Role=%s accountId=%s",
            expectedUsername,
            result.getRole(),
            result.getAccountId()
        ));
    }

    private void failAndDisconnect(String username, String reason) {
        String key = normalizeKey(username);
        PlayerAuthState state = authStates.remove(key);
        if (state == null) {
            return;
        }

        state.verificationInFlight = false;
        logger.warning(String.format("Disconnecting %s: %s", state.username, reason));
        try {
            playerBridge.disconnectPlayer(state.player, DISCONNECT_MESSAGE);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to disconnect unauthenticated player " + state.username + ".", exception);
        }
    }

    private static String normalizeKey(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        return normalized.isEmpty() ? "unknown_reason" : normalized;
    }

    private static ExecutorService newSingleThreadExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "obsidiangate-auth-verify-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(factory);
    }

    private static final class PlayerAuthState {
        private volatile Object player;
        private final String username;
        private final Instant connectedAt;
        private volatile boolean verificationInFlight;
        private volatile boolean verified;

        private PlayerAuthState(Object player, String username, Instant connectedAt) {
            this.player = player;
            this.username = username;
            this.connectedAt = connectedAt;
        }
    }
}
