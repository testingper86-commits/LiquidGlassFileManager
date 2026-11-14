package com.music.echo.offline.files;

/** Runtime design-language profiles: each identity owns geometry, material, type and motion tokens. */
final class ToxiThemeEngine {
    enum Geometry { VOYAGE, SHINOBI, BREATHING, ULTRA, FORTRESS }
    enum Material { OCEAN_TITANIUM, CHAKRA_FORGED, BREATHING_STEEL, AURA_ALLOY, MILITARY_TITANIUM }

    static final class Profile {
        final int index;
        final String identity;
        final Geometry geometry;
        final Material material;
        final String typeface;
        final float cornerScale, spacingScale, iconStroke, edgeStrength, highlightStrength, motionScale;

        Profile(int index, String identity, Geometry geometry, Material material, String typeface,
                float cornerScale, float spacingScale, float iconStroke, float edgeStrength,
                float highlightStrength, float motionScale) {
            this.index = index;
            this.identity = identity;
            this.geometry = geometry;
            this.material = material;
            this.typeface = typeface;
            this.cornerScale = cornerScale;
            this.spacingScale = spacingScale;
            this.iconStroke = iconStroke;
            this.edgeStrength = edgeStrength;
            this.highlightStrength = highlightStrength;
            this.motionScale = motionScale;
        }
    }

    private static final Profile[] PROFILES = {
        new Profile(0, "Grand Voyage", Geometry.VOYAGE, Material.OCEAN_TITANIUM,
                "sans-serif", 1.12f, 1.08f, 1.75f, .58f, .24f, 1.00f),
        new Profile(1, "Shinobi Spirit", Geometry.SHINOBI, Material.CHAKRA_FORGED,
                "sans-serif-condensed", .82f, .88f, 2.0f, .72f, .20f, .92f),
        new Profile(2, "Breathing Legends", Geometry.BREATHING, Material.BREATHING_STEEL,
                "sans-serif-medium", .94f, .96f, 1.85f, .68f, .28f, 1.08f),
        new Profile(3, "Ultra Instinct", Geometry.ULTRA, Material.AURA_ALLOY,
                "sans-serif-light", 1.00f, 1.02f, 1.75f, .62f, .34f, 1.18f),
        new Profile(4, "Titan Frontier", Geometry.FORTRESS, Material.MILITARY_TITANIUM,
                "sans-serif-condensed", .76f, .84f, 2.0f, .82f, .16f, .86f)
    };

    private static int activeIndex;
    private ToxiThemeEngine() { }

    static Profile profile() {
        return PROFILES[ToxiThemeSystem.normalize(activeIndex)];
    }

    static void activate(int index) {
        activeIndex = ToxiThemeSystem.normalize(index);
    }

    static int radius(float baseDp) {
        return Math.max(4, Math.round(baseDp * profile().cornerScale));
    }

    static float spacing(float baseDp) { return baseDp * profile().spacingScale; }

    /** Keep interactive targets at least 48 dp while allowing larger profiles to grow. */
    static float touchTarget(float baseDp) {
        return Math.max(48f, Math.max(baseDp, spacing(baseDp)));
    }

    static float iconStroke() {
        return profile().iconStroke;
    }

    static int edgeAlpha(int baseAlpha) {
        return Math.max(0, Math.min(255, Math.round(baseAlpha * profile().edgeStrength)));
    }

    static int highlightAlpha(int baseAlpha) {
        float strength = 0.65f + profile().highlightStrength;
        return Math.max(0, Math.min(255, Math.round(baseAlpha * strength)));
    }

    static long motionDuration(long baseMillis) {
        return Math.max(90L, Math.round(baseMillis * profile().motionScale));
    }

    static float motionDistance(float baseDp) {
        return baseDp * profile().motionScale;
    }
}
