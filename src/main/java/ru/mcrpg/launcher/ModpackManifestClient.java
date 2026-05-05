package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public final class ModpackManifestClient {

    private final ObjectMapper objectMapper;

    public ModpackManifestClient() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public LoadedManifest load(String manifestUrl) throws IOException {
        String normalizedUrl = requireText(manifestUrl, "Укажи URL manifest.json.");
        URL url = new URL(normalizedUrl);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        try (InputStream inputStream = connection.getInputStream()) {
            ModpackManifest manifest = objectMapper.readValue(inputStream, ModpackManifest.class);
            validate(manifest);
            return new LoadedManifest(url, manifest);
        }
    }

    private static void validate(ModpackManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest.json is empty.");
        }
        if (manifest.getSchemaVersion() != 1) {
            throw new IllegalArgumentException(
                "Неподдерживаемая schemaVersion: " + manifest.getSchemaVersion() + ". Сейчас поддерживается только 1."
            );
        }
        if (manifest.getLauncher() == null) {
            manifest.setLauncher(new LauncherManifestSettings());
        }
        if (manifest.getRuntime() == null) {
            manifest.setRuntime(new ModpackRuntime());
        }
        if (manifest.getMinecraft() == null) {
            manifest.setMinecraft(new MinecraftBootstrapSettings());
        }
        if (manifest.getFiles() == null) {
            manifest.setFiles(null);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
