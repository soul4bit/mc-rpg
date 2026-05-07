package ru.mcrpg.launcher;

import java.io.IOException;

public final class AuthClientException extends IOException {

    private final int statusCode;
    private final String errorCode;

    public AuthClientException(int statusCode, String errorCode, String message) {
        super(message == null || message.trim().isEmpty() ? "Authentication request failed." : message.trim());
        this.statusCode = statusCode;
        this.errorCode = errorCode == null ? "" : errorCode.trim();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
