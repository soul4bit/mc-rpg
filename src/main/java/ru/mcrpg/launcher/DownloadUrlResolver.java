package ru.mcrpg.launcher;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class DownloadUrlResolver {

    private DownloadUrlResolver() {
    }

    static URL resolve(URL manifestSourceUrl, String manifestBaseUrl, String rawUrl) throws IOException {
        URL baseUrl = manifestSourceUrl;
        if (hasText(manifestBaseUrl)) {
            baseUrl = new URL(manifestSourceUrl, manifestBaseUrl.trim());
        }
        return resolve(baseUrl, rawUrl);
    }

    static URL resolve(URL baseUrl, String rawUrl) throws IOException {
        String value = requireText(rawUrl, "Download URL is missing.");
        if (looksAbsoluteUrl(value)) {
            return new URL(value);
        }
        return new URL(baseUrl, encodeRelativePath(value));
    }

    static String encodeRelativePath(String rawPath) {
        String normalizedPath = requireText(rawPath, "Download path is missing.").replace('\\', '/');
        String[] segments = normalizedPath.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                encoded.append('/');
            }
            encoded.append(encodePathSegment(segments[index]));
        }
        return encoded.toString();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean looksAbsoluteUrl(String value) {
        int schemeSeparator = value.indexOf(':');
        if (schemeSeparator <= 0) {
            return false;
        }

        for (int index = 0; index < schemeSeparator; index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '+' && character != '-' && character != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
