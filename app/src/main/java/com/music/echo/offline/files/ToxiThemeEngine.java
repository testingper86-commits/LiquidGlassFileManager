package com.music.echo.offline.files;

/** Runtime design-language profiles: each identity owns geometry, material, type and motion tokens. */
final class ToxiThemeEngine {
    enum Geometry { VOYAGE, SHINOBI, BREATHING, ULTRA, FORTRESS, HEROIC, CARTOON, WILDLIFE, CYBERNETIC, EXTRATERRESTRIAL }
    enum Material { OCEAN_TITANIUM, CHAKRA_FORGED, BREATHING_STEEL, AURA_ALLOY, MILITARY_TITANIUM, HERO_ALLOY, CARTOON_GLASS, ORGANIC_TITANIUM, CYBER_FORGED, XENO_BIOLUMINESCENT }
    enum Motif { COMPASS, SPIRAL, FLOW, ORBIT, RAMPART, STAR_SHIELD, PLAYFUL_SWIRL, CLAW, ENERGON_CIRCUIT, XENO_HIVE }

    static final class Profile {
        final int index;
        final String identity;
        final Geometry geometry;
        final Material material;
        final Motif motif;
        final String typeface;
        final float cornerScale, spacingScale, iconStroke, edgeStrength, highlightStrength, motionScale;

        Profile(int index, String identity, Geometry geometry, Material material, Motif motif, String typeface,
                float cornerScale, float spacingScale, float iconStroke, float edgeStrength,
                float highlightStrength, float motionScale) {
            this.index = index;
            this.identity = identity;
            this.geometry = geometry;
            this.material = material;
            this.motif = motif;
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
        new Profile(0, "Grand Voyage", Geometry.VOYAGE, Material.OCEAN_TITANIUM, Motif.COMPASS,
                "sans-serif", 1.12f, 1.08f, 1.75f, .58f, .24f, 1.00f),
        new Profile(1, "Shinobi Spirit", Geometry.SHINOBI, Material.CHAKRA_FORGED, Motif.SPIRAL,
                "sans-serif-condensed", .82f, .88f, 2.00f, .72f, .20f, .92f),
        new Profile(2, "Breathing Legends", Geometry.BREATHING, Material.BREATHING_STEEL, Motif.FLOW,
                "sans-serif-medium", .94f, .96f, 1.82f, .68f, .28f, 1.08f),
        new Profile(3, "Ultra Instinct", Geometry.ULTRA, Material.AURA_ALLOY, Motif.ORBIT,
                "sans-serif-light", 1.00f, 1.02f, 1.90f, .62f, .34f, 1.18f),
        new Profile(4, "Titan Frontier", Geometry.FORTRESS, Material.MILITARY_TITANIUM, Motif.RAMPART,
                "sans-serif-condensed", .76f, .84f, 1.96f, .82f, .16f, .86f),
        new Profile(5, "Earth's Mightiest", Geometry.HEROIC, Material.HERO_ALLOY, Motif.STAR_SHIELD,
                "sans-serif-medium", 1.06f, 1.04f, 1.88f, .70f, .32f, 1.12f),
        new Profile(6, "Cartoon Chaos", Geometry.CARTOON, Material.CARTOON_GLASS, Motif.PLAYFUL_SWIRL,
                "sans-serif", 1.22f, 1.14f, 1.62f, .54f, .38f, 1.24f),
        new Profile(7, "Wild Earth", Geometry.WILDLIFE, Material.ORGANIC_TITANIUM, Motif.CLAW,
                "sans-serif-medium", .68f, 1.10f, 1.78f, .66f, .26f, .96f),
        new Profile(8, "Cybertron", Geometry.CYBERNETIC, Material.CYBER_FORGED, Motif.ENERGON_CIRCUIT,
                "sans-serif-condensed", .86f, .92f, 2.08f, .78f, .30f, 1.16f),
        new Profile(9, "Beyond Earth", Geometry.EXTRATERRESTRIAL, Material.XENO_BIOLUMINESCENT, Motif.XENO_HIVE,
                "sans-serif-light", 1.30f, .98f, 1.84f, .74f, .42f, 1.06f)
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

    /** The selected profile is the single source of truth for display typography. */
    static String headingTypeface() { return profile().typeface; }

    /** Neutral high-legibility body face; theme character is carried by display typography. */
    static String bodyTypeface() { return "sans-serif"; }

    /** Monospaced face for paths, dates, sizes and compact technical metadata. */
    static String monoTypeface() { return "monospace"; }

    static float spacing(float baseDp) { return baseDp * profile().spacingScale; }

    /** Keep interactive targets at least 48 dp while allowing larger profiles to grow. */
    static float touchTarget(float baseDp) {
        return Math.max(48f, Math.max(baseDp, spacing(baseDp)));
    }

    static float iconStroke() { return profile().iconStroke; }

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

    static float motionDistance(float baseDp) { return baseDp * profile().motionScale; }
}
