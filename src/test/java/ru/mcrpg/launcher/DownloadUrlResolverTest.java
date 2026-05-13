package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import org.junit.jupiter.api.Test;

class DownloadUrlResolverTest {

    @Test
    void resolvesRelativePathsWithEncodedSegments() throws Exception {
        URL resolved = DownloadUrlResolver.resolve(
            new URL("http://example.com/client/"),
            "config/Dark Roleplay Core/Client [main].cfg"
        );

        assertEquals(
            "http://example.com/client/config/Dark%20Roleplay%20Core/Client%20%5Bmain%5D.cfg",
            resolved.toString()
        );
    }

    @Test
    void resolvesAgainstManifestBaseUrlBeforeEncodingSegments() throws Exception {
        URL resolved = DownloadUrlResolver.resolve(
            new URL("http://example.com/manifest.json"),
            "client/",
            "resourcepacks/Faithful 1.12.2-rv4.zip"
        );

        assertEquals(
            "http://example.com/client/resourcepacks/Faithful%201.12.2-rv4.zip",
            resolved.toString()
        );
    }
}
