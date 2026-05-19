package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeleportGuardServiceTest {

    @Test
    void cooldownExpiresAfterDuration() {
        TeleportGuardService service = new TeleportGuardService(1000L);

        service.startCooldown("Player", TeleportGuardService.CHANNEL_HOME, 1000L);

        assertTrue(service.cooldownRemainingSeconds("player", TeleportGuardService.CHANNEL_HOME) > 0);
        assertEquals(0, service.cooldownRemainingSeconds("player", TeleportGuardService.CHANNEL_RTP));
    }

    @Test
    void combatUsesPlayerIdentity() {
        TeleportGuardService service = new TeleportGuardService(15000L);
        FakePlayer player = new FakePlayer(UUID.randomUUID());

        service.markCombat(player, System.currentTimeMillis());

        assertTrue(service.isInCombat(player));
        assertTrue(service.combatRemainingSeconds(player) > 0);
    }

    static final class FakePlayer {
        private final UUID id;

        FakePlayer(UUID id) {
            this.id = id;
        }

        public UUID getUniqueID() {
            return id;
        }
    }
}
