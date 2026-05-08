package ru.mcrpg.gameauth;

import java.time.Instant;

public final class LauncherSession {

    private final String ticket;
    private final String username;
    private final String uuid;
    private final String serverId;
    private final Instant expiresAt;

    public LauncherSession(String ticket, String username, String uuid, String serverId, Instant expiresAt) {
        this.ticket = normalize(ticket);
        this.username = normalize(username);
        this.uuid = normalize(uuid);
        this.serverId = normalize(serverId);
        this.expiresAt = expiresAt;
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

    public GameTicketProof toTicketProof() {
        return new GameTicketProof(ticket, serverId);
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || now == null || !expiresAt.isAfter(now);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
