package ru.mcrpg.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

public final class LauncherHomeContentLoader {

    private static final String DEFAULT_RESOURCE = "/ru/mcrpg/launcher/launcher-home.json";

    private final ObjectMapper objectMapper;
    private final String resourcePath;

    public LauncherHomeContentLoader() {
        this(DEFAULT_RESOURCE);
    }

    LauncherHomeContentLoader(String resourcePath) {
        this.resourcePath = resourcePath;
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public LauncherHomeContent loadDefault() {
        try (InputStream inputStream = LauncherHomeContentLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return LauncherHomeContent.defaults();
            }
            return normalize(objectMapper.readValue(inputStream, LauncherHomeContent.class));
        } catch (IOException exception) {
            return LauncherHomeContent.defaults();
        }
    }

    private static LauncherHomeContent normalize(LauncherHomeContent content) {
        LauncherHomeContent defaults = LauncherHomeContent.defaults();
        if (content == null) {
            return defaults;
        }
        if (!hasText(content.getHeroEyebrow())) {
            content.setHeroEyebrow(defaults.getHeroEyebrow());
        }
        if (!hasText(content.getHeroTitle())) {
            content.setHeroTitle(defaults.getHeroTitle());
        }
        if (!hasText(content.getHeroDescription())) {
            content.setHeroDescription(defaults.getHeroDescription());
        }
        if (!hasText(content.getHeroFootnote())) {
            content.setHeroFootnote(defaults.getHeroFootnote());
        }
        if (content.getSpotlight() == null || content.getSpotlight().isEmpty()) {
            content.setSpotlight(defaults.getSpotlight());
        }
        if (content.getNews() == null || content.getNews().isEmpty()) {
            content.setNews(defaults.getNews());
        }
        return content;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
