package com.music.echo.offline.files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OfflineToolUiTest {
    @Test
    public void exposesTheEightRequiredToolThemeNamesInOrder() {
        assertArrayEquals(new String[]{
                "Cyborg Titanium", "Neural", "Overdrive", "Phantom",
                "Matrix Hacker", "Cyber Hacker", "Dark Web", "Ghost Protocol"
        }, OfflineToolUi.themeNames());
    }

    @Test
    public void toolThemesHaveEightDistinctExistingAccentColors() {
        assertEquals(8, ThemeCatalog.COLORS.length);
        assertTrue(ThemeCatalog.hasEightDistinctColors());
    }

    @Test
    public void returnedThemeNamesCannotMutateTheCatalog() {
        String[] names = OfflineToolUi.themeNames();
        names[0] = "Modified";
        assertEquals("Cyborg Titanium", OfflineToolUi.themeNames()[0]);
    }
}
