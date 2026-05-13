package ru.mcrpg.forgeauth.client;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import ru.mcrpg.gameauth.GameTicketProof;
import ru.mcrpg.gameauth.LauncherSession;
import ru.mcrpg.gameauth.LauncherSessionFiles;

final class ForgeAuthClientLifecycle {

    private final SimpleNetworkWrapper channel;
    private final Logger logger;
    private final LauncherSessionFiles sessionFiles;

    private LauncherSession pendingSession;
    private Path pendingSessionPath;
    private boolean pendingSend;
    private boolean alreadySent;
    private int ticksUntilSend;

    ForgeAuthClientLifecycle(SimpleNetworkWrapper channel, Logger logger) {
        this.channel = channel;
        this.logger = logger;
        this.sessionFiles = new LauncherSessionFiles();
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        pendingSession = null;
        pendingSessionPath = null;
        pendingSend = false;
        alreadySent = false;
        ticksUntilSend = 0;

        try {
            pendingSessionPath = sessionFiles.resolveFromSystemProperty();
            LauncherSession session = sessionFiles.read(pendingSessionPath);
            if (session.isExpired(Instant.now())) {
                logger.warning("Launcher session ticket already expired. Ticket will not be sent.");
                return;
            }
            if (!session.hasTickets()) {
                logger.warning("Launcher session has no remaining reconnect tickets. Ticket will not be sent.");
                return;
            }

            pendingSession = session;
            pendingSend = true;
            ticksUntilSend = 20;
            logger.info(String.format(
                "Launcher session loaded for %s. Waiting for network handshake before sending ticket proof.",
                session.getUsername()
            ));
        } catch (IOException exception) {
            logger.warning("No launcher session file available: " + exception.getMessage());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to prepare launcher session proof.", exception);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingSend || alreadySent) {
            return;
        }

        if (pendingSession == null) {
            pendingSend = false;
            ticksUntilSend = 0;
            return;
        }

        if (ticksUntilSend > 0) {
            ticksUntilSend--;
            return;
        }

        GameTicketProof proof = pendingSession.toTicketProof();
        if (!proof.isComplete()) {
            logger.warning("Launcher session is missing ticket proof data. Ticket will not be sent.");
            pendingSend = false;
            return;
        }

        channel.sendToServer(new AuthTicketMessage(proof));
        consumeCurrentTicket();
        alreadySent = true;
        pendingSend = false;
        logger.info(String.format(
            "Sent launcher ticket proof for %s to the server channel.",
            pendingSession.getUsername()
        ));
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        pendingSession = null;
        pendingSessionPath = null;
        pendingSend = false;
        alreadySent = false;
        ticksUntilSend = 0;
    }

    private void consumeCurrentTicket() {
        if (pendingSession == null || pendingSessionPath == null) {
            return;
        }

        try {
            sessionFiles.write(pendingSessionPath, pendingSession.consumeTicket());
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Failed to persist remaining reconnect tickets.", exception);
        }
    }
}
