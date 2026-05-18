package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KitServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void startClaimPersistsAcrossServiceReloads() {
        Path claimsPath = tempDirectory.resolve("kit-claims.properties");
        KitService service = new KitService(Logger.getLogger("test"), claimsPath);

        service.load();
        assertFalse(service.hasClaimedStart("Player-UUID"));

        service.recordStartClaim("Player-UUID", "Player");
        assertTrue(service.hasClaimedStart("player-uuid"));

        KitService restored = new KitService(Logger.getLogger("test"), claimsPath);
        restored.load();
        assertTrue(restored.hasClaimedStart("PLAYER-UUID"));
    }
}
