package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class MinecraftServerStatusProbe {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int STATUS_PROTOCOL_VERSION = 340;

    private MinecraftServerStatusProbe() {
    }

    public static ServerStatus probe(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            writePacket(outputStream, packet -> {
                writeVarInt(packet, 0);
                writeVarInt(packet, STATUS_PROTOCOL_VERSION);
                writeString(packet, host);
                packet.writeShort(port);
                writeVarInt(packet, 1);
            });

            long pingStartedAt = System.nanoTime();
            writePacket(outputStream, packet -> writeVarInt(packet, 0));

            int responseLength = readVarInt(inputStream);
            byte[] responsePayload = inputStream.readNBytes(responseLength);
            if (responsePayload.length != responseLength) {
                throw new IOException("Minecraft status response is truncated.");
            }

            DataInputStream responseStream = new DataInputStream(new java.io.ByteArrayInputStream(responsePayload));
            int responsePacketId = readVarInt(responseStream);
            if (responsePacketId != 0) {
                throw new IOException("Unexpected Minecraft status packet: " + responsePacketId);
            }

            JsonNode statusNode = OBJECT_MAPPER.readTree(readString(responseStream));
            String versionName = trimToEmpty(statusNode.path("version").path("name").asText(""));
            int onlinePlayers = statusNode.path("players").path("online").asInt(-1);
            int maxPlayers = statusNode.path("players").path("max").asInt(-1);

            writePacket(outputStream, packet -> {
                writeVarInt(packet, 1);
                packet.writeLong(pingStartedAt);
            });

            int pongLength = readVarInt(inputStream);
            byte[] pongPayload = inputStream.readNBytes(pongLength);
            if (pongPayload.length != pongLength) {
                throw new IOException("Minecraft pong response is truncated.");
            }

            DataInputStream pongStream = new DataInputStream(new java.io.ByteArrayInputStream(pongPayload));
            int pongPacketId = readVarInt(pongStream);
            if (pongPacketId != 1) {
                throw new IOException("Unexpected Minecraft pong packet: " + pongPacketId);
            }
            pongStream.readLong();

            long pingMs = Math.max(1L, (System.nanoTime() - pingStartedAt) / 1_000_000L);
            return new ServerStatus(versionName, onlinePlayers, maxPlayers, pingMs);
        }
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
        byte[] bytes = trimToEmpty(value).getBytes(StandardCharsets.UTF_8);
        writeVarInt(outputStream, bytes.length);
        outputStream.write(bytes);
    }

    private static String readString(DataInputStream inputStream) throws IOException {
        int length = readVarInt(inputStream);
        byte[] bytes = inputStream.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Minecraft status string is truncated.");
        }
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
                throw new IOException("Minecraft VarInt is too large.");
            }
        } while ((currentByte & 0x80) != 0);
        return result;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream outputStream) throws IOException;
    }

    public static final class ServerStatus {
        private final String versionName;
        private final int onlinePlayers;
        private final int maxPlayers;
        private final long pingMs;

        private ServerStatus(String versionName, int onlinePlayers, int maxPlayers, long pingMs) {
            this.versionName = versionName;
            this.onlinePlayers = onlinePlayers;
            this.maxPlayers = maxPlayers;
            this.pingMs = pingMs;
        }

        public String getVersionName() {
            return versionName;
        }

        public int getOnlinePlayers() {
            return onlinePlayers;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public long getPingMs() {
            return pingMs;
        }
    }
}
