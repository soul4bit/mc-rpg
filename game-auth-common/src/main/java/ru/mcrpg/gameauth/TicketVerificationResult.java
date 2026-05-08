package ru.mcrpg.gameauth;

public final class TicketVerificationResult {

    private final boolean valid;
    private final String accountId;
    private final String username;
    private final String playerUuid;
    private final String role;
    private final String reason;

    public TicketVerificationResult(
        boolean valid,
        String accountId,
        String username,
        String playerUuid,
        String role,
        String reason
    ) {
        this.valid = valid;
        this.accountId = normalize(accountId);
        this.username = normalize(username);
        this.playerUuid = normalize(playerUuid);
        this.role = normalize(role);
        this.reason = normalize(reason);
    }

    public boolean isValid() {
        return valid;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getRole() {
        return role;
    }

    public String getReason() {
        return reason;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
