package ru.mcrpg.gameauth;

public final class GameTicketProof {

    private final String ticket;
    private final String serverId;

    public GameTicketProof(String ticket, String serverId) {
        this.ticket = normalize(ticket);
        this.serverId = normalize(serverId);
    }

    public String getTicket() {
        return ticket;
    }

    public String getServerId() {
        return serverId;
    }

    public boolean isComplete() {
        return !ticket.isEmpty() && !serverId.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
