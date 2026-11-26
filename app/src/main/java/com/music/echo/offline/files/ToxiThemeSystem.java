package com.music.echo.offline.files;

/** Ten complete ToxiFiles visual identities with independent metallic-glass design tokens. */
final class ToxiThemeSystem {
    static final String DESIGN_SYSTEM_BASE = "Obsidian Titanium";
    static final String[] NAMES = {
        "One Piece · Grand Voyage",
        "Naruto · Shinobi Spirit",
        "Demon Slayer · Breathing Legends",
        "Dragon Ball · Ultra Instinct",
        "Attack on Titan · Titan Frontier",
        "Marvel Avengers · Earth's Mightiest",
        "Oggy · Cartoon Chaos",
        "Animal Kingdom · Wild Earth",
        "Transformers · Cybertron",
        "Alien Worlds · Beyond Earth"
    };
    static final String[] DESCRIPTIONS = {
        "Abyss black · ocean teal · treasure gold",
        "Obsidian black · chakra orange · crimson red",
        "Midnight graphite · breathing crimson · flame gold",
        "Deep black · ultra violet · kinetic blue",
        "Gunmetal black · military olive · titan crimson",
        "Heroic crimson · arc-reactor blue · victory gold",
        "Playful sky blue · citrus orange · cartoon silver",
        "Forest shadow · wildlife amber · canopy green",
        "Cybertron steel · energon blue · autobot red",
        "Xeno black · bioluminescent green · acid lime"
    };
    static final int[] ACCENTS = {0xFF39D6D0, 0xFFFF8B3D, 0xFFF04D5E, 0xFF59C7FF, 0xFFE43757, 0xFFE53935, 0xFF36B8F4, 0xFFDCA64B, 0xFF39A8FF, 0xFF71E35A};
    static final int[] ACCENT_ENDS = {0xFFFFC66E, 0xFFE63E35, 0xFFB9A9FF, 0xFF8D7CFF, 0xFFF2B64B, 0xFFFFC247, 0xFFFF8A35, 0xFF5FA66A, 0xFFE24A43, 0xFFC3F36B};
    static final int[] CHROME = {0xFFB7C6CC, 0xFFBAC0C8, 0xFFC4CCDB, 0xFFD2D9E3, 0xFFAEBBC4, 0xFFC8D0DC, 0xFFD2E4EE, 0xFFC6B99A, 0xFFC2CDD8, 0xFFB8C7BE};
    static final int[] CANVAS = {0xFF05090D, 0xFF090708, 0xFF000810, 0xFF090B10, 0xFF050810, 0xFF070A12, 0xFF07121D, 0xFF0A100B, 0xFF080D14, 0xFF050C0A};
    static final int[] SURFACES = {0xFF101820, 0xFF17100F, 0xFF241827, 0xFF111C31, 0xFF202832, 0xFF171E2C, 0xFF12283A, 0xFF1A281C, 0xFF172330, 0xFF11201A};
    static final int[] RAISED = {0xFF1A2931, 0xFF281816, 0xFF2C1719, 0xFF20203A, 0xFF242B25, 0xFF252F42, 0xFF1D354A, 0xFF273829, 0xFF223344, 0xFF1A3024};
    static final int[] EDGES = {0xFF536F78, 0xFF74513A, 0xFF76464A, 0xFF5C5A91, 0xFF626F5C, 0xFF59677F, 0xFF4C7D99, 0xFF667B52, 0xFF5B728A, 0xFF4D7659};
    static final int[] MUTED = {0xFFA9C1C5, 0xFFC7A995, 0xFFFBF4F0, 0xFFF4F8FF, 0xFFAEBBC4, 0xFFBAC5D6, 0xFFB2D0E0, 0xFFC1CBAF, 0xFFB8C8D8, 0xFFB2CBBB};
    static final int[] SOFT_ACCENTS = {0xFF163A3C, 0xFF442116, 0xFF451C24, 0xFF28234F, 0xFF323A27, 0xFF3D1B24, 0xFF143A55, 0xFF34441F, 0xFF173653, 0xFF173D29};
    static final int[] LIGHT_CANVAS = {0xFFF0F6F6, 0xFFFFF2E9, 0xFFFFF0F0, 0xFFF1F2FF, 0xFFF2F4F0, 0xFFF4F5F8, 0xFFEFF8FE, 0xFFF3F5E9, 0xFFF0F5F9, 0xFFF0F7F0};
    static final int[] LIGHT_SURFACES = {0xFFFCFFFF, 0xFFFFFCF8, 0xFFFFFBFA, 0xFFFCFCFF, 0xFFFCFEFA, 0xFFFFFFFF, 0xFFFEFFFF, 0xFFFDFFFC, 0xFFFCFEFF, 0xFFFBFFFC};
    static final int[] LIGHT_RAISED = {0xFFE0EEEE, 0xFFF8E2D4, 0xFFF9DEDE, 0xFFE4E4FA, 0xFFE5EADF, 0xFFE5E9F0, 0xFFDDEFFA, 0xFFE6EBD5, 0xFFDDE8F1, 0xFFDDECDD};
    static final int[] LIGHT_EDGES = {0xFFB4D0D1, 0xFFE8B99E, 0xFFE4B3B4, 0xFFC1BDE8, 0xFFC6D0B8, 0xFFC1C9D6, 0xFFB6D7EA, 0xFFC9D1B2, 0xFFB9CBDC, 0xFFB9D0BC};
    static final int[] LIGHT_MUTED = {0xFF4E7075, 0xFF85563F, 0xFF864F53, 0xFF5D5B91, 0xFF5E6C56, 0xFF596579, 0xFF456E86, 0xFF61714A, 0xFF526B82, 0xFF4F7156};

    private ToxiThemeSystem() { }
    static int normalize(int index) { return Math.max(0, Math.min(NAMES.length - 1, index)); }
    static String description(int index) { return DESCRIPTIONS[normalize(index)]; }
    private static boolean unique(int[] values) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int value : values) seen.add(value);
        return seen.size() == values.length;
    }
    static boolean hasIndependentTokens() {
        int n = NAMES.length;
        return n == 10 && DESCRIPTIONS.length == n && ACCENTS.length == n && ACCENT_ENDS.length == n
            && CHROME.length == n && CANVAS.length == n && SURFACES.length == n && RAISED.length == n
            && EDGES.length == n && MUTED.length == n && SOFT_ACCENTS.length == n
            && LIGHT_CANVAS.length == n && LIGHT_SURFACES.length == n && LIGHT_RAISED.length == n
            && LIGHT_EDGES.length == n && LIGHT_MUTED.length == n
            && new java.util.HashSet<>(java.util.Arrays.asList(NAMES)).size() == n
            && unique(ACCENTS) && unique(ACCENT_ENDS) && unique(CHROME) && unique(CANVAS)
            && unique(SURFACES) && unique(RAISED) && unique(EDGES) && unique(MUTED) && unique(SOFT_ACCENTS);
    }
}
