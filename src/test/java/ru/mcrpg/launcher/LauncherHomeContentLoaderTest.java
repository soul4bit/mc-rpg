package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class LauncherHomeContentLoaderTest {

    @Test
    void loadsBundledHomeContent() {
        LauncherHomeContent content = new LauncherHomeContentLoader().loadDefault();

        assertEquals("REDSTONE", content.getHeroTitle());
        assertFalse(content.getCommunity().isEmpty());
        assertFalse(content.getSpotlight().isEmpty());
        assertFalse(content.getNews().isEmpty());
    }
}
