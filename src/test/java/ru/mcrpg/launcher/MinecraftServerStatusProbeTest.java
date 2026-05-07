package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MinecraftServerStatusProbeTest {

    @Test
    void probeReadsVersionPlayersAndPing() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
            Thread serverThread = new Thread(() -> handleProbe(serverSocket, serverFailure), "status-probe-test");
            serverThread.start();

            MinecraftServerStatusProbe.ServerStatus status = MinecraftServerStatusProbe.probe(
                "127.0.0.1",
                serverSocket.getLocalPort(),
                2000
            );

            serverThread.join(2000L);
            assertFalse(serverThread.isAlive(), "Test server thread did not finish.");
            assertNotNull(status);
            assertEquals("1.12.2", status.getVersionName());
            assertEquals(27, status.getOnlinePlayers());
            assertEquals(120, status.getMaxPlayers());
            assertTrue(status.getPingMs() >= 1L);
            if (serverFailure.get() != null) {
                throw new AssertionError(serverFailure.get());
            }
        }
    }

    private static void handleProbe(ServerSocket serverSocket, AtomicReference<Throwable> serverFailure) {
        try (Socket socket = serverSocket.accept()) {
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            byte[] handshakePayload = readPayload(inputStream);
            DataInputStream handshakeStream = new DataInputStream(new java.io.ByteArrayInputStream(handshakePayload));
            assertEquals(0, readVarInt(handshakeStream));
            readVarInt(handshakeStream);
            assertEquals("127.0.0.1", readString(handshakeStream));
            assertTrue(handshakeStream.readUnsignedShort() > 0);
            assertEquals(1, readVarInt(handshakeStream));

            byte[] requestPayload = readPayload(inputStream);
            DataInputStream requestStream = new DataInputStream(new java.io.ByteArrayInputStream(requestPayload));
            assertEquals(0, readVarInt(requestStream));

            String responseJson =
                "{\"version\":{\"name\":\"1.12.2\",\"protocol\":340},"
                    + "\"players\":{\"max\":120,\"online\":27},"
                    + "\"description\":{\"text\":\"mc-rpg\"}}";
            writePacket(outputStream, packet -> {
                writeVarInt(packet, 0);
                writeString(packet, responseJson);
            });

            byte[] pingPayload = readPayload(inputStream);
            DataInputStream pingStream = new DataInputStream(new java.io.ByteArrayInputStream(pingPayload));
            assertEquals(1, readVarInt(pingStream));
            long pingValue = pingStream.readLong();

            writePacket(outputStream, packet -> {
                writeVarInt(packet, 1);
                packet.writeLong(pingValue);
            });
        } catch (Throwable throwable) {
            serverFailure.set(throwable);
        }
    }

    private static byte[] readPayload(DataInputStream inputStream) throws IOException {
        int length = readVarInt(inputStream);
        byte[] payload = inputStream.readNBytes(length);
        assertEquals(length, payload.length);
        return payload;
    }

    private static void writePacket(DataOutputStream outputStream, PacketWriter writer) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream packet = new DataOutputStream(buffer);
        writer.write(packet);
        packet.flush();

        byte[] payload = buffer.toByteArray();
        writeVarInt(outputStream, payload.length);
        outputStream.write(payload);
        outputStream.flush();
    }

    private static void writeString(DataOutputStream outputStream, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(outputStream, bytes.length);
        outputStream.write(bytes);
    }

    private static String readString(DataInputStream inputStream) throws IOException {
        int length = readVarInt(inputStream);
        byte[] bytes = inputStream.readNBytes(length);
        assertEquals(length, bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream outputStream, int value) throws IOException {
        int current = value;
        do {
            int part = current & 0x7F;
            current >>>= 7;
            if (current != 0) {
                part |= 0x80;
            }
            outputStream.writeByte(part);
        } while (current != 0);
    }

    private static int readVarInt(DataInputStream inputStream) throws IOException {
        int result = 0;
        int position = 0;
        int currentByte;
        do {
            currentByte = inputStream.readUnsignedByte();
            result |= (currentByte & 0x7F) << position;
            position += 7;
            if (position > 35) {
                throw new IOException("VarInt is too large.");
            }
        } while ((currentByte & 0x80) != 0);
        return result;
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream outputStream) throws IOException;
    }
}
