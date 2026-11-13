package com.music.echo.offline.files;

/** Five complete ToxiFiles visual identities with independent metallic-glass design tokens. */
final class ToxiThemeSystem {
    static final String DESIGN_SYSTEM_BASE = "Obsidian Titanium";
    static final String[] NAMES = {
        "One Piece · Grand Voyage",
        "Naruto · Shinobi Spirit",
        "Demon Slayer · Breathing Legends",
        "Dragon Ball · Ultra Instinct",
        "Attack on Titan · Titan Frontier"
    };
    static final String[] DESCRIPTIONS = {
        "Abyss black · ocean teal · treasure gold",
        "Obsidian black · chakra orange · crimson red",
        "Midnight graphite · breathing crimson · flame gold",
        "Deep black · ultra violet · kinetic blue",
        "Gunmetal black · military olive · titan crimson"
    };
    static final int[] ACCENTS = {0xFF39D6D0, 0xFFFF8B3D, 0xFFED5B52, 0xFF9D8BFF, 0xFFB7C477};
    static final int[] ACCENT_ENDS = {0xFFFFC66E, 0xFFE63E35, 0xFFFFC65D, 0xFF39BDEB, 0xFFD95145};
    static final int[] CHROME = {0xFFB7C6CC, 0xFFBAC0C8, 0xFFC5B9B5, 0xFFB9C5E3, 0xFFB9BDB8};
    static final int[] CANVAS = {0xFF05090D, 0xFF090708, 0xFF0B0809, 0xFF070812, 0xFF090B0B};
    static final int[] SURFACES = {0xFF101820, 0xFF17100F, 0xFF1A1012, 0xFF111323, 0xFF151918};
    static final int[] RAISED = {0xFF1A2931, 0xFF281816, 0xFF2C1719, 0xFF20203A, 0xFF242B25};
    static final int[] EDGES = {0xFF536F78, 0xFF74513A, 0xFF76464A, 0xFF5C5A91, 0xFF626F5C};
    static final int[] MUTED = {0xFFA9C1C5, 0xFFC7A995, 0xFFC9A8A7, 0xFFB3B9D9, 0xFFB6BDAF};
    static final int[] SOFT_ACCENTS = {0xFF163A3C, 0xFF442116, 0xFF451C24, 0xFF28234F, 0xFF323A27};
    static final int[] LIGHT_CANVAS = {0xFFF0F6F6, 0xFFFFF2E9, 0xFFFFF0F0, 0xFFF1F2FF, 0xFFF2F4F0};
    static final int[] LIGHT_SURFACES = {0xFFFCFFFF, 0xFFFFFCF8, 0xFFFFFBFA, 0xFFFCFCFF, 0xFFFCFEFA};
    static final int[] LIGHT_RAISED = {0xFFE0EEEE, 0xFFF8E2D4, 0xFFF9DEDE, 0xFFE4E4FA, 0xFFE5EADF};
    static final int[] LIGHT_EDGES = {0xFFB4D0D1, 0xFFE8B99E, 0xFFE4B3B4, 0xFFC1BDE8, 0xFFC6D0B8};
    static final int[] LIGHT_MUTED = {0xFF4E7075, 0xFF85563F, 0xFF864F53, 0xFF5D5B91, 0xFF5E6C56};

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
        return n == 5 && DESCRIPTIONS.length == n && ACCENTS.length == n && ACCENT_ENDS.length == n
            && CHROME.length == n && CANVAS.length == n && SURFACES.length == n && RAISED.length == n
            && EDGES.length == n && MUTED.length == n && SOFT_ACCENTS.length == n
            && LIGHT_CANVAS.length == n && LIGHT_SURFACES.length == n && LIGHT_RAISED.length == n
            && LIGHT_EDGES.length == n && LIGHT_MUTED.length == n
            && new java.util.HashSet<>(java.util.Arrays.asList(NAMES)).size() == n
            && unique(ACCENTS) && unique(ACCENT_ENDS) && unique(CHROME) && unique(CANVAS)
            && unique(SURFACES) && unique(RAISED) && unique(EDGES) && unique(MUTED) && unique(SOFT_ACCENTS);
    }
}
