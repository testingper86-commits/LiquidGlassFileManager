package com.music.echo.offline.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class ToxiThemeSystemTest {
    @Test public void exposesEightOriginalThemeIdentities() {
        assertEquals(8, ToxiThemeSystem.NAMES.length);
        assertEquals(8, ToxiThemeSystem.DESCRIPTIONS.length);
        assertEquals(8, ToxiThemeSystem.ACCENTS.length);
        assertEquals(8, new java.util.HashSet<>(java.util.Arrays.asList(ToxiThemeSystem.NAMES)).size());
    }
    @Test public void everyThemeHasAnIndependentCompleteTokenSet() {
        assertTrue(ToxiThemeSystem.hasIndependentTokens());
        for (int i=0;i<8;i++) {
            assertFalse(ToxiThemeSystem.CANVAS[i] == ToxiThemeSystem.SURFACES[i]);
            assertFalse(ToxiThemeSystem.SURFACES[i] == ToxiThemeSystem.RAISED[i]);
            assertFalse(ToxiThemeSystem.ACCENTS[i] == ToxiThemeSystem.ACCENT_ENDS[i]);
        }
    }
    @Test public void themeIndexIsSafelyBounded() {
        assertEquals(0,ToxiThemeSystem.normalize(-1));
        assertEquals(7,ToxiThemeSystem.normalize(99));
        assertEquals(4,ToxiThemeSystem.normalize(4));
    }
}
