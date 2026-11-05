package com.music.echo.offline.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThemeCatalogTest {
    @Test
    public void exposesExactlyEightNamedThemes() {
        assertEquals(8, ThemeCatalog.NAMES.length);
        assertEquals(8, ThemeCatalog.COLORS.length);
        assertEquals(8, ThemeCatalog.NOTES.length);
    }

    @Test
    public void everyThemeHasDistinctColorAndDescription() {
        assertTrue(ThemeCatalog.hasEightDistinctColors());
        for (int i = 0; i < ThemeCatalog.NAMES.length; i++) {
            assertFalse(ThemeCatalog.NAMES[i].trim().isEmpty());
            assertNotNull(ThemeCatalog.note(i));
            assertFalse(ThemeCatalog.note(i).trim().isEmpty());
        }
    }

    @Test
    public void savedThemeIndicesAreClampedSafely() {
        assertEquals(0, ThemeCatalog.normalizeIndex(-1));
        assertEquals(0, ThemeCatalog.normalizeIndex(0));
        assertEquals(7, ThemeCatalog.normalizeIndex(7));
        assertEquals(7, ThemeCatalog.normalizeIndex(8));
        assertEquals(ThemeCatalog.NOTES[7], ThemeCatalog.note(Integer.MAX_VALUE));
    }
}
