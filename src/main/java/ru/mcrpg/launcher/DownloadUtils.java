package ru.mcrpg.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
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

        try (InputStream inputStream = connection.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                 target,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING,
                 StandardOpenOption.WRITE
             )) {
            return copy(inputStream, outputStream);
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
}
