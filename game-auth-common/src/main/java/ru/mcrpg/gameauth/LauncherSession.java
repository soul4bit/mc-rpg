package ru.mcrpg.gameauth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LauncherSession {

    private final List<String> tickets;
    private final String username;
    private final String uuid;
    private final String serverId;
    private final Instant expiresAt;

    public LauncherSession(String ticket, String username, String uuid, String serverId, Instant expiresAt) {
        this(Collections.singletonList(ticket), username, uuid, serverId, expiresAt);
    }

    public LauncherSession(List<String> tickets, String username, String uuid, String serverId, Instant expiresAt) {
        this.tickets = normalizeTickets(tickets);
        this.username = normalize(username);
        this.uuid = normalize(uuid);
        this.serverId = normalize(serverId);
        this.expiresAt = expiresAt;
    }

    public String getTicket() {
        return tickets.isEmpty() ? "" : tickets.get(0);
    }

    public List<String> getTickets() {
        return tickets;
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
        return new GameTicketProof(getTicket(), serverId);
    }

    public boolean hasTickets() {
        return !tickets.isEmpty();
    }

    public LauncherSession consumeTicket() {
        if (tickets.isEmpty()) {
            return this;
        }
        List<String> remaining = new ArrayList<String>(tickets.subList(1, tickets.size()));
        return new LauncherSession(remaining, username, uuid, serverId, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || now == null || !expiresAt.isAfter(now);
    }

    private static List<String> normalizeTickets(List<String> tickets) {
        List<String> normalized = new ArrayList<String>();
        if (tickets != null) {
            for (String ticket : tickets) {
                String value = normalize(ticket);
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
        }
        return Collections.unmodifiableList(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
