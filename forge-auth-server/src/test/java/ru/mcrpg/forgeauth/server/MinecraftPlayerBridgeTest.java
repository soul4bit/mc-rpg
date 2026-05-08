package ru.mcrpg.forgeauth.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class MinecraftPlayerBridgeTest {

    @Test
    void extractsPlayerFromForgeStyleEventAndReadsUsername() {
        FakePlayer player = new FakePlayer("soul4bit");
        FakeEvent event = new FakeEvent(player);
        MinecraftPlayerBridge bridge = new MinecraftPlayerBridge(message -> message);

        assertSame(player, bridge.extractPlayerFromEvent(event));
        assertEquals("soul4bit", bridge.extractUsername(player));
    }

    @Test
    void disconnectsThroughKnownConnectionFieldAndMethod() {
        FakePlayer player = new FakePlayer("soul4bit");
        MinecraftPlayerBridge bridge = new MinecraftPlayerBridge(message -> message);

        bridge.disconnectPlayer(player, "Denied.");

        assertEquals("Denied.", player.connection.lastMessage);
    }

    static final class FakeEvent {
        public final Object player;

        FakeEvent(Object player) {
            this.player = player;
        }
    }

    static final class FakePlayer {
        private final String username;
        public final FakeConnection connection = new FakeConnection();

        FakePlayer(String username) {
            this.username = username;
        }

        public String func_70005_c_() {
            return username;
        }
    }

    static final class FakeConnection {
        private String lastMessage;

        public void disconnect(Object textComponent) {
            this.lastMessage = String.valueOf(textComponent);
        }
    }
}
