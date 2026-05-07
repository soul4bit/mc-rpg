package ru.mcrpg.launcher;

public final class AuthAccount {

    private final String id;
    private final String username;
    private final String email;
    private final String role;
    private final String status;

    public AuthAccount(String id, String username, String email, String role, String status) {
        this.id = normalize(id);
        this.username = normalize(username);
        this.email = normalize(email);
        this.role = normalize(role);
        this.status = normalize(status);
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
