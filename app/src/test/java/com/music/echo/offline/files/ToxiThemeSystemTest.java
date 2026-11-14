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

    @Test public void everyProfileHasItsOwnCornerSpacingStrokeEdgeHighlightAndMotionScale() {
        java.util.Set<Float> corners = new java.util.HashSet<>();
        java.util.Set<Float> spacing = new java.util.HashSet<>();
        java.util.Set<Float> strokes = new java.util.HashSet<>();
        java.util.Set<Float> edges = new java.util.HashSet<>();
        java.util.Set<Float> highlights = new java.util.HashSet<>();
        java.util.Set<Float> motion = new java.util.HashSet<>();
        java.util.Set<Integer> radii = new java.util.HashSet<>();
        java.util.Set<Integer> edgeOutputs = new java.util.HashSet<>();
        java.util.Set<Integer> highlightOutputs = new java.util.HashSet<>();
        java.util.Set<Long> durationOutputs = new java.util.HashSet<>();
        for (int i = 0; i < ToxiThemeSystem.NAMES.length; i++) {
            ToxiThemeEngine.activate(i);
            ToxiThemeEngine.Profile profile = ToxiThemeEngine.profile();
            corners.add(profile.cornerScale);
            spacing.add(profile.spacingScale);
            strokes.add(profile.iconStroke);
            edges.add(profile.edgeStrength);
            highlights.add(profile.highlightStrength);
            motion.add(profile.motionScale);
            radii.add(ToxiThemeEngine.radius(16));
            edgeOutputs.add(ToxiThemeEngine.edgeAlpha(200));
            highlightOutputs.add(ToxiThemeEngine.highlightAlpha(120));
            durationOutputs.add(ToxiThemeEngine.motionDuration(200));
        }
        assertEquals("Every theme must own a distinct corner scale", 5, corners.size());
        assertEquals("Every theme must own a distinct spacing scale", 5, spacing.size());
        assertEquals("Every theme must own a distinct icon stroke", 5, strokes.size());
        assertEquals("Every theme must own a distinct edge strength", 5, edges.size());
        assertEquals("Every theme must own a distinct highlight strength", 5, highlights.size());
        assertEquals("Every theme must own a distinct motion scale", 5, motion.size());
        assertEquals("Geometry tokens must produce distinct component radii", 5, radii.size());
        assertEquals("Edge tokens must produce distinct rendered alpha values", 5, edgeOutputs.size());
        assertEquals("Highlight tokens must produce distinct rendered alpha values", 5, highlightOutputs.size());
        assertEquals("Motion tokens must produce distinct animation durations", 5, durationOutputs.size());
        ToxiThemeEngine.activate(0);
    }

    @Test public void allFiveThemesProduceDistinctClippingSilhouettes() {
        float previousDensity = ToxiVisualSystem.D;
        ToxiVisualSystem.D = 1f;
        java.util.Set<String> silhouettes = new java.util.HashSet<>();
        for (int i = 0; i < ToxiThemeSystem.NAMES.length; i++) {
            ToxiThemeEngine.activate(i);
            float[] radii = ToxiVisualSystem.profileCornerRadii(18f);
            assertEquals(8, radii.length);
            for (float radius : radii) assertTrue(radius > 0f);
            silhouettes.add(java.util.Arrays.toString(radii));
        }
        assertEquals("Each theme must clip views with its own silhouette", 5, silhouettes.size());
        ToxiThemeEngine.activate(0);
        ToxiVisualSystem.D = previousDensity;
    }

    @Test public void themeEngineNormalizesInvalidSavedIndexes() {
        ToxiThemeEngine.activate(-10);
        assertEquals(0, ToxiThemeEngine.profile().index);
        ToxiThemeEngine.activate(100);
        assertEquals(4, ToxiThemeEngine.profile().index);
    }

    @Test public void activeProfileCanBeSwitchedAndRestoredWithoutStaleTokens() {
        for (int i = 0; i < ToxiThemeSystem.NAMES.length; i++) {
            ToxiThemeEngine.activate(i);
            assertEquals(i, ToxiThemeEngine.profile().index);
            assertEquals(ToxiThemeSystem.NAMES.length, ToxiThemeSystem.ACCENTS.length);
            assertEquals(ToxiThemeSystem.NAMES.length, ToxiThemeSystem.CHROME.length);
            assertEquals(ToxiThemeSystem.NAMES.length, ToxiThemeSystem.ACCENT_ENDS.length);
            assertEquals(ToxiThemeSystem.NAMES.length, ToxiThemeSystem.LIGHT_CANVAS.length);
            assertEquals(ToxiThemeSystem.NAMES.length, ToxiThemeSystem.LIGHT_SURFACES.length);
        }
        ToxiThemeEngine.activate(0);
        assertEquals(0, ToxiThemeEngine.profile().index);
    }

    @Test public void persistedThemeSurvivesFreshEngineRestorationAndRebuildsPalette() {
        final java.util.Map<String, Integer> durable = new java.util.HashMap<>();
        ToxiThemePersistence.Store writer = new ToxiThemePersistence.Store() {
            @Override public int readTheme() { return durable.containsKey("theme") ? durable.get("theme") : 0; }
            @Override public void writeTheme(int index) { durable.put("theme", index); }
        };
        for (int i = 0; i < ToxiThemeSystem.NAMES.length; i++) {
            assertEquals(i, ToxiThemePersistence.save(writer, i));
            ToxiThemeEngine.activate(0); // Simulate a newly created runtime after process recreation.
            ToxiThemePersistence.Store reader = new ToxiThemePersistence.Store() {
                @Override public int readTheme() { return durable.containsKey("theme") ? durable.get("theme") : 0; }
                @Override public void writeTheme(int index) { durable.put("theme", index); }
            };
            assertEquals(i, ToxiThemePersistence.restore(reader));
            assertEquals(i, ToxiThemeEngine.profile().index);
            assertEquals(ToxiThemeSystem.ACCENTS[i], ToxiThemeSystem.ACCENTS[ToxiThemeEngine.profile().index]);
            assertEquals(ToxiThemeSystem.CANVAS[i], ToxiThemeSystem.CANVAS[ToxiThemeEngine.profile().index]);
        }
        ToxiThemeEngine.activate(0);
    }

    @Test public void persistedThemeRestorationUsesSafeDefaultAndClampsCorruptValues() {
        final int[] stored = {Integer.MIN_VALUE};
        ToxiThemePersistence.Store store = new ToxiThemePersistence.Store() {
            @Override public int readTheme() { return stored[0]; }
            @Override public void writeTheme(int index) { stored[0] = index; }
        };
        assertEquals(0, ToxiThemePersistence.restore(null));
        assertEquals(0, ToxiThemePersistence.restore(store));
        stored[0] = Integer.MAX_VALUE;
        assertEquals(ToxiThemeSystem.NAMES.length - 1, ToxiThemePersistence.restore(store));
        ToxiThemeEngine.activate(0);
    }

    @Test public void eachThemeHasDistinctAccentAndChromeIdentity() {
        java.util.HashSet<Integer> pairs = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) {
            assertFalse(ToxiThemeSystem.ACCENTS[i] == ToxiThemeSystem.CHROME[i]);
            assertFalse(ToxiThemeSystem.ACCENT_ENDS[i] == ToxiThemeSystem.CHROME[i]);
            assertTrue(ToxiThemeSystem.SOFT_ACCENTS[i] != ToxiThemeSystem.CANVAS[i]);
            assertTrue(ToxiThemeSystem.LIGHT_EDGES[i] != ToxiThemeSystem.LIGHT_CANVAS[i]);
            pairs.add(ToxiThemeSystem.ACCENTS[i] ^ ToxiThemeSystem.CHROME[i]);
        }
        assertEquals(5, pairs.size());
    }

    @Test public void approvedCanvaPaletteAnchorsAreAppliedToMatchingThemes() {
        assertEquals(0xFFF04D5E, ToxiThemeSystem.ACCENTS[2]);
        assertEquals(0xFFB9A9FF, ToxiThemeSystem.ACCENT_ENDS[2]);
        assertEquals(0xFF000810, ToxiThemeSystem.CANVAS[2]);
        assertEquals(0xFF241827, ToxiThemeSystem.SURFACES[2]);
        assertEquals(0xFF59C7FF, ToxiThemeSystem.ACCENTS[3]);
        assertEquals(0xFF8D7CFF, ToxiThemeSystem.ACCENT_ENDS[3]);
        assertEquals(0xFF111C31, ToxiThemeSystem.SURFACES[3]);
        assertEquals(0xFFE43757, ToxiThemeSystem.ACCENTS[4]);
        assertEquals(0xFFF2B64B, ToxiThemeSystem.ACCENT_ENDS[4]);
        assertEquals(0xFF050810, ToxiThemeSystem.CANVAS[4]);
        assertEquals(0xFF202832, ToxiThemeSystem.SURFACES[4]);
    }

    @Test public void typographyRolesMatchTheApprovedThemeDirections() {
        ToxiThemeEngine.activate(0);
        assertEquals("sans-serif", ToxiThemeEngine.headingTypeface());
        ToxiThemeEngine.activate(1);
        assertEquals("sans-serif-medium", ToxiThemeEngine.headingTypeface());
        ToxiThemeEngine.activate(2);
        assertEquals("sans-serif-medium", ToxiThemeEngine.headingTypeface());
        ToxiThemeEngine.activate(3);
        assertEquals("sans-serif-condensed", ToxiThemeEngine.headingTypeface());
        ToxiThemeEngine.activate(4);
        assertEquals("sans-serif-condensed", ToxiThemeEngine.headingTypeface());
        assertEquals("sans-serif", ToxiThemeEngine.bodyTypeface());
        assertEquals("monospace", ToxiThemeEngine.monoTypeface());
        ToxiThemeEngine.activate(0);
    }

    @Test public void themeIndexIsSafelyBoundedForSavedPreferenceMigration() {
        assertEquals(0, ToxiThemeSystem.normalize(-1));
        assertEquals(0, ToxiThemeSystem.normalize(0));
        assertEquals(4, ToxiThemeSystem.normalize(4));
        assertEquals(4, ToxiThemeSystem.normalize(5));
        assertEquals(4, ToxiThemeSystem.normalize(99));
    }
}
