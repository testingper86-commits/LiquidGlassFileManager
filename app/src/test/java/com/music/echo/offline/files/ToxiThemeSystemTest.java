package com.music.echo.offline.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class ToxiThemeSystemTest {
    @Test public void exposesTenDistinctThemeIdentities() {
        assertEquals(10, ToxiThemeSystem.NAMES.length);
        assertEquals(10, ToxiThemeSystem.DESCRIPTIONS.length);
        assertEquals(10, ToxiThemeSystem.ACCENTS.length);
        assertEquals(10, new java.util.HashSet<>(java.util.Arrays.asList(ToxiThemeSystem.NAMES)).size());
        assertTrue(ToxiThemeSystem.NAMES[0].startsWith("Solo Leveling"));
        assertTrue(ToxiThemeSystem.NAMES[9].startsWith("Sailor Moon"));
    }
    @Test public void everyThemeHasIndependentDarkAndLightTokens() {
        assertTrue(ToxiThemeSystem.hasIndependentTokens());
        for (int i = 0; i < 10; i++) {
            assertFalse(ToxiThemeSystem.CANVAS[i] == ToxiThemeSystem.SURFACES[i]);
            assertFalse(ToxiThemeSystem.SURFACES[i] == ToxiThemeSystem.RAISED[i]);
            assertFalse(ToxiThemeSystem.ACCENTS[i] == ToxiThemeSystem.ACCENT_ENDS[i]);
            assertFalse(ToxiThemeSystem.LIGHT_CANVAS[i] == ToxiThemeSystem.LIGHT_SURFACES[i]);
        }
    }
    @Test public void themeIndexIsSafelyBounded() {
        assertEquals(0, ToxiThemeSystem.normalize(-1));
        assertEquals(9, ToxiThemeSystem.normalize(99));
        assertEquals(4, ToxiThemeSystem.normalize(4));
    }
}