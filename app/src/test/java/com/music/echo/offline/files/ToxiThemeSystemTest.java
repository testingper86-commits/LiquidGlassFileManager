package com.music.echo.offline.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class ToxiThemeSystemTest {
    @Test public void exposesExactlyFiveRequestedThemeIdentitiesInOrder() {
        assertEquals(5, ToxiThemeSystem.NAMES.length);
        assertEquals("One Piece · Grand Voyage", ToxiThemeSystem.NAMES[0]);
        assertEquals("Naruto · Shinobi Spirit", ToxiThemeSystem.NAMES[1]);
        assertEquals("Demon Slayer · Breathing Legends", ToxiThemeSystem.NAMES[2]);
        assertEquals("Dragon Ball · Ultra Instinct", ToxiThemeSystem.NAMES[3]);
        assertEquals("Attack on Titan · Titan Frontier", ToxiThemeSystem.NAMES[4]);
        assertEquals(5, ToxiThemeSystem.DESCRIPTIONS.length);
        assertEquals(5, new java.util.HashSet<>(java.util.Arrays.asList(ToxiThemeSystem.NAMES)).size());
    }

    @Test public void everyThemeHasDistinctDarkLightAndMetallicGlassTokens() {
        assertTrue(ToxiThemeSystem.hasIndependentTokens());
        for (int i = 0; i < 5; i++) {
            assertFalse(ToxiThemeSystem.CANVAS[i] == ToxiThemeSystem.SURFACES[i]);
            assertFalse(ToxiThemeSystem.SURFACES[i] == ToxiThemeSystem.RAISED[i]);
            assertFalse(ToxiThemeSystem.ACCENTS[i] == ToxiThemeSystem.ACCENT_ENDS[i]);
            assertFalse(ToxiThemeSystem.LIGHT_CANVAS[i] == ToxiThemeSystem.LIGHT_SURFACES[i]);
            assertTrue(ToxiThemeSystem.CHROME[i] != ToxiThemeSystem.ACCENTS[i]);
        }
    }

    @Test public void themeIndexIsSafelyBoundedForSavedPreferenceMigration() {
        assertEquals(0, ToxiThemeSystem.normalize(-1));
        assertEquals(0, ToxiThemeSystem.normalize(0));
        assertEquals(4, ToxiThemeSystem.normalize(4));
        assertEquals(4, ToxiThemeSystem.normalize(5));
        assertEquals(4, ToxiThemeSystem.normalize(99));
    }
}
