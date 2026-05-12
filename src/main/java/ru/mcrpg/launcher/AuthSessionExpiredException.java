package ru.mcrpg.launcher;

import java.io.IOException;

public final class AuthSessionExpiredException extends IOException {

    public AuthSessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
