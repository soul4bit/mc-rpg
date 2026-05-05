package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class ModpackManifestClientTest {

    @Test
    void loadWrapsConnectionRefusedWithManifestHostingHint() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        IOException exception = org.junit.jupiter.api.Assertions.assertThrows(
            IOException.class,
            () -> new ModpackManifestClient().load("http://127.0.0.1:" + port + "/manifest.json")
        );

        assertTrue(exception.getMessage().contains("manifest.json"));
        assertTrue(exception.getMessage().contains("HTTP(S)"));
        assertTrue(exception.getMessage().contains("25565"));
        assertInstanceOf(ConnectException.class, exception.getCause());
    }
}
