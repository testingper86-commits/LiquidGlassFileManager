package com.music.echo.offline.files;

/** Ten original anime-inspired identities with independent dark/light surface tokens. */
final class ToxiThemeSystem {
    static final String[] NAMES = {
        "Solo Leveling · Shadow Monarch", "Demon Slayer · Breathing Legends",
        "Jujutsu Kaisen · Infinity Domain", "Attack on Titan · Titan Frontier",
        "Naruto · Shinobi Spirit", "One Piece · Grand Voyage",
        "Dragon Ball · Ultra Instinct", "Bleach · Soul Reaper",
        "Evangelion · EVA Protocol", "Sailor Moon · Eternal Moon"
    };
    static final String[] DESCRIPTIONS = {
        "Void black · monarch violet · spectral cyan", "Midnight ink · flame vermilion · breathing gold",
        "Abyss navy · cursed indigo · limitless blue", "Iron graphite · titan crimson · field amber",
        "Shinobi charcoal · chakra orange · leaf jade", "Deep ocean · voyage teal · treasure gold",
        "Cosmic black · instinct silver · energy blue", "Soul midnight · spirit ivory · reaper crimson",
        "EVA graphite · reactor violet · signal lime", "Lunar plum · celestial rose · moonlight silver"
    };
    static final int[] ACCENTS = {0xFF9A7BFF,0xFFFF765B,0xFF7C8CFF,0xFFE65A57,0xFFFFA04A,0xFF4ED6C5,0xFF9DD8FF,0xFFFF6B78,0xFFB28BFF,0xFFFFA9D2};
    static final int[] ACCENT_ENDS = {0xFF42D8E8,0xFFFFC45C,0xFF4B52C8,0xFFFFA34A,0xFF68D59A,0xFFFFD16A,0xFF657BFF,0xFFB24D79,0xFFB7E65B,0xFFB9A8FF};
    static final int[] CANVAS = {0xFF080711,0xFF100A0A,0xFF080A18,0xFF10090B,0xFF0C0B0A,0xFF071111,0xFF080B13,0xFF0C080E,0xFF0B0B0D,0xFF100A15};
    static final int[] SURFACES = {0xFF171326,0xFF211513,0xFF14182B,0xFF211416,0xFF1C1712,0xFF10201F,0xFF151B2A,0xFF1B121C,0xFF1A1C1D,0xFF211629};
    static final int[] RAISED = {0xFF29203D,0xFF38221D,0xFF222A49,0xFF3B2024,0xFF30251A,0xFF193330,0xFF25334A,0xFF33202F,0xFF2B3030,0xFF38243F};
    static final int[] EDGES = {0xFF55457C,0xFF75463A,0xFF46558C,0xFF79434A,0xFF71502F,0xFF34746B,0xFF4B668E,0xFF704052,0xFF596C4A,0xFF754E78};
    static final int[] MUTED = {0xFFB8ADD5,0xFFD0A99C,0xFFAAB6E2,0xFFD0A4A6,0xFFC8B69A,0xFFA4C8C2,0xFFAFC5E2,0xFFD0A8B8,0xFFB9C1B2,0xFFD2B4CF};
    static final int[] SOFT_ACCENTS = {0xFF30234C,0xFF4B251D,0xFF242C59,0xFF51232B,0xFF49301D,0xFF17443E,0xFF203D60,0xFF4A2438,0xFF354222,0xFF4C2948};
    static final int[] LIGHT_CANVAS = {0xFFF5F1FC,0xFFFFF3EF,0xFFF0F2FF,0xFFFFF0EF,0xFFFFF5EC,0xFFECF8F5,0xFFF0F5FF,0xFFFFF0F4,0xFFF2F4F0,0xFFFFF1FA};
    static final int[] LIGHT_SURFACES = {0xFFFFFDFF,0xFFFFFCFA,0xFFFCFCFF,0xFFFFFBFA,0xFFFFFCF8,0xFFFBFFFD,0xFFFCFEFF,0xFFFFFCFD,0xFFFCFEFA,0xFFFFFCFF};
    static final int[] LIGHT_RAISED = {0xFFEAE2F8,0xFFF8E2DB,0xFFE3E7FF,0xFFF8DEDE,0xFFF5E7D6,0xFFDDF2ED,0xFFE0EBFA,0xFFF4DFE7,0xFFE6EDE0,0xFFF5E2F2};
    static final int[] LIGHT_EDGES = {0xFFC9BCE8,0xFFE7BEB2,0xFFBBC5F0,0xFFE8B8BB,0xFFE4C7A6,0xFFB5DCD3,0xFFBDCEE8,0xFFE4BDCC,0xFFC8D6B6,0xFFE6C1DD};
    static final int[] LIGHT_MUTED = {0xFF685B82,0xFF80564C,0xFF58658E,0xFF805257,0xFF7A6248,0xFF4E766F,0xFF526B8B,0xFF805568,0xFF61714F,0xFF805C79};
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
            && CANVAS.length == n && SURFACES.length == n && RAISED.length == n && EDGES.length == n
            && MUTED.length == n && SOFT_ACCENTS.length == n && LIGHT_CANVAS.length == n
            && LIGHT_SURFACES.length == n && LIGHT_RAISED.length == n && LIGHT_EDGES.length == n
            && LIGHT_MUTED.length == n && new java.util.HashSet<>(java.util.Arrays.asList(NAMES)).size() == n
            && unique(ACCENTS) && unique(ACCENT_ENDS) && unique(CANVAS) && unique(SURFACES)
            && unique(RAISED) && unique(EDGES) && unique(MUTED) && unique(SOFT_ACCENTS);
    }
}