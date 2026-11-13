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


    @Test public void eachThemeProfileDrivesDistinctGeometryMaterialAndMotion() {
        java.util.HashSet<ToxiThemeEngine.Geometry> geometries = new java.util.HashSet<>();
        java.util.HashSet<ToxiThemeEngine.Material> materials = new java.util.HashSet<>();
        java.util.HashSet<String> typefaces = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) {
            ToxiThemeEngine.activate(i);
            ToxiThemeEngine.Profile profile = ToxiThemeEngine.profile();
            assertEquals(i, profile.index);
            geometries.add(profile.geometry);
            materials.add(profile.material);
            typefaces.add(profile.typeface);
            assertTrue(ToxiThemeEngine.radius(16) >= 4);
            assertTrue(ToxiThemeEngine.iconStroke() > 0f);
            assertTrue(ToxiThemeEngine.edgeAlpha(180) >= 0);
            assertTrue(ToxiThemeEngine.edgeAlpha(180) <= 255);
            assertTrue(ToxiThemeEngine.highlightAlpha(120) >= 0);
            assertTrue(ToxiThemeEngine.highlightAlpha(120) <= 255);
            assertTrue(ToxiThemeEngine.motionDuration(200) >= 90);
            assertTrue(ToxiThemeEngine.motionDistance(10) > 0f);
            assertTrue(ToxiThemeEngine.touchTarget(32) >= 48f);
            assertTrue(ToxiThemeEngine.touchTarget(56) >= 56f);
        }
        assertEquals(5, geometries.size());
        assertEquals(5, materials.size());
        assertTrue(typefaces.size() >= 3);
        ToxiThemeEngine.activate(0);
    }

    @Test public void themeEngineNormalizesInvalidSavedIndexes() {
        ToxiThemeEngine.activate(-10);
        assertEquals(0, ToxiThemeEngine.profile().index);
        ToxiThemeEngine.activate(100);
        assertEquals(4, ToxiThemeEngine.profile().index);
    }

    @Test public void themeIndexIsSafelyBoundedForSavedPreferenceMigration() {
        assertEquals(0, ToxiThemeSystem.normalize(-1));
        assertEquals(0, ToxiThemeSystem.normalize(0));
        assertEquals(4, ToxiThemeSystem.normalize(4));
        assertEquals(4, ToxiThemeSystem.normalize(5));
        assertEquals(4, ToxiThemeSystem.normalize(99));
    }
}
