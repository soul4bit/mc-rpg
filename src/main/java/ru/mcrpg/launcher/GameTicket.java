package ru.mcrpg.launcher;

import java.time.Instant;

public final class GameTicket {

    private final String ticket;
    private final String username;
    private final String uuid;
    private final String serverId;
    private final Instant expiresAt;

    public GameTicket(String ticket, String username, String uuid, String serverId, Instant expiresAt) {
        this.ticket = normalize(ticket);
        this.username = normalize(username);
        this.uuid = normalize(uuid);
        this.serverId = normalize(serverId);
        this.expiresAt = expiresAt == null ? Instant.EPOCH : expiresAt;
    }

    public String getTicket() {
        return ticket;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getServerId() {
        return serverId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
