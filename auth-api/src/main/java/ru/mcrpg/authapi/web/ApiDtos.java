package ru.mcrpg.authapi.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

record RegisterRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,16}$", message = "must match [A-Za-z0-9_]{3,16}")
    String username,
    @NotBlank
    @Email
    @Size(max = 255)
    String email,
    @NotBlank
    @Size(min = 8, max = 128)
    String password,
    @Size(max = 128)
    String deviceName
) {
}

record LoginRequest(
    @NotBlank
    @Size(max = 255)
    String login,
    @NotBlank
    @Size(min = 8, max = 128)
    String password,
    @Size(max = 128)
    String deviceName
) {
}

record RefreshRequest(
    @NotBlank
    String refreshToken
) {
}

record LogoutRequest(
    @NotBlank
    String refreshToken
) {
}

record UpdateMeRequest(
    @NotBlank
    @Email
    @Size(max = 255)
    String email
) {
}

record CreateGameTicketRequest(
    @Size(max = 64)
    String serverId
) {
}

record VerifyGameTicketRequest(
    @NotBlank
    String ticket,
    @Size(max = 64)
    String serverId
) {
}

record AccountResponse(
    String id,
    String username,
    String email,
    String role,
    String status
) {
}

record AuthSessionResponse(
    AccountResponse account,
    String accessToken,
    String refreshToken,
    long expiresIn,
    Instant expiresAt
) {
}

record GameTicketResponse(
    String ticket,
    String username,
    @JsonProperty("uuid")
    String playerUuid,
    String serverId,
    Instant expiresAt
) {
}

record VerifyGameTicketResponse(
    boolean valid,
    String accountId,
    String username,
    @JsonProperty("uuid")
    String playerUuid,
    String role,
    String reason
) {
}

record HealthResponse(
    String status,
    String service
) {
}
