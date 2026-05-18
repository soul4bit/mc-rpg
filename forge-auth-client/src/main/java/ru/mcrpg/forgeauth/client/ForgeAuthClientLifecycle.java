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
                logger.warning("Ticket сессии лаунчера уже истек. Отправка отменена.");
                return;
            }
            if (!session.hasTickets()) {
                logger.warning("В сессии лаунчера не осталось ticket для переподключения. Отправка отменена.");
                return;
            }

            pendingSession = session;
            pendingSend = true;
            ticksUntilSend = 20;
            logger.info(String.format(
                "Сессия лаунчера загружена для %s. Ждем сетевое рукопожатие перед отправкой ticket proof.",
                session.getUsername()
            ));
        } catch (IOException exception) {
            logger.warning("Файл сессии лаунчера недоступен: " + exception.getMessage());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Не удалось подготовить proof сессии лаунчера.", exception);
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
            logger.warning("В сессии лаунчера не хватает данных ticket proof. Отправка отменена.");
            pendingSend = false;
            return;
        }

        channel.sendToServer(new AuthTicketMessage(proof));
        consumeCurrentTicket();
        alreadySent = true;
        pendingSend = false;
        logger.info(String.format(
            "Ticket proof лаунчера для %s отправлен в серверный канал.",
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
            logger.log(Level.WARNING, "Не удалось сохранить оставшиеся ticket переподключения.", exception);
        }
    }
}
