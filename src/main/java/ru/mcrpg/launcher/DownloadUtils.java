package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class DownloadUtils {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int BUFFER_SIZE = 64 * 1024;

    private DownloadUtils() {
    }

    static long download(URL downloadUrl, Path target, int readTimeoutMs) throws IOException {
        URLConnection connection = downloadUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(readTimeoutMs);

        try {
            ensureSuccessfulResponse(connection, downloadUrl);
        } catch (IOException exception) {
            throw enrichDownloadFailure(downloadUrl, exception);
        }

        try (InputStream inputStream = connection.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                 target,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING,
                 StandardOpenOption.WRITE
             )) {
            return copy(inputStream, outputStream);
        } catch (IOException exception) {
            throw enrichDownloadFailure(downloadUrl, exception);
        }
    }

    static long copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        long totalBytes = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
            totalBytes += read;
        }
        return totalBytes;
    }

    private static void ensureSuccessfulResponse(URLConnection connection, URL downloadUrl) throws IOException {
        if (!(connection instanceof HttpURLConnection)) {
            return;
        }

        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        int statusCode = httpConnection.getResponseCode();
        if (statusCode < 400) {
            return;
        }

        String responseMessage = httpConnection.getResponseMessage();
        StringBuilder message = new StringBuilder("HTTP ").append(statusCode);
        if (hasText(responseMessage)) {
            message.append(" ").append(responseMessage.trim());
        }
        message.append(" while downloading ").append(downloadUrl);
        throw new IOException(message.toString());
    }

    private static IOException enrichDownloadFailure(URL downloadUrl, IOException exception) {
        String message = exception.getMessage();
        if (!hasText(message)) {
            return new IOException("Failed to download " + downloadUrl + ".", exception);
        }

        String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
        String normalizedUrl = downloadUrl.toString().toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains(normalizedUrl) || normalizedMessage.startsWith("failed to download ")) {
            return exception;
        }

        return new IOException("Failed to download " + downloadUrl + ": " + message.trim(), exception);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
