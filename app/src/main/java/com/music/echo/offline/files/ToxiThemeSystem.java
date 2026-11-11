package com.music.echo.offline.files;

/** Five curated anime-inspired identities with independent dark/light surface tokens. */
final class ToxiThemeSystem {
    static final String DESIGN_SYSTEM_BASE = "Obsidian Alloy";
    static final String[] NAMES = {"Solo Leveling · Shadow Monarch", "Demon Slayer · Breathing Legends", "Jujutsu Kaisen · Infinity Domain", "One Piece · Grand Voyage", "Evangelion · EVA Protocol"};
    static final String[] DESCRIPTIONS = {"Void black · monarch violet · spectral cyan", "Midnight ink · flame vermilion · breathing gold", "Abyss navy · cursed indigo · limitless blue", "Deep ocean · voyage teal · treasure gold", "EVA graphite · reactor violet · signal lime"};
    static final int[] ACCENTS = {0xFF9A7BFF, 0xFFFF765B, 0xFF7C8CFF, 0xFF4ED6C5, 0xFFB28BFF};
    static final int[] ACCENT_ENDS = {0xFF42D8E8, 0xFFFFC45C, 0xFF4B52C8, 0xFFFFD16A, 0xFFB7E65B};
    static final int[] CANVAS = {0xFF080711, 0xFF100A0A, 0xFF080A18, 0xFF071111, 0xFF0B0B0D};
    static final int[] SURFACES = {0xFF171326, 0xFF211513, 0xFF14182B, 0xFF10201F, 0xFF1A1C1D};
    static final int[] RAISED = {0xFF29203D, 0xFF38221D, 0xFF222A49, 0xFF193330, 0xFF2B3030};
    static final int[] EDGES = {0xFF55457C, 0xFF75463A, 0xFF46558C, 0xFF34746B, 0xFF596C4A};
    static final int[] MUTED = {0xFFB8ADD5, 0xFFD0A99C, 0xFFAAB6E2, 0xFFA4C8C2, 0xFFB9C1B2};
    static final int[] SOFT_ACCENTS = {0xFF30234C, 0xFF4B251D, 0xFF242C59, 0xFF17443E, 0xFF354222};
    static final int[] LIGHT_CANVAS = {0xFFF5F1FC, 0xFFFFF3EF, 0xFFF0F2FF, 0xFFECF8F5, 0xFFF2F4F0};
    static final int[] LIGHT_SURFACES = {0xFFFFFDFF, 0xFFFFFCFA, 0xFFFCFCFF, 0xFFFBFFFD, 0xFFFCFEFA};
    static final int[] LIGHT_RAISED = {0xFFEAE2F8, 0xFFF8E2DB, 0xFFE3E7FF, 0xFFDDF2ED, 0xFFE6EDE0};
    static final int[] LIGHT_EDGES = {0xFFC9BCE8, 0xFFE7BEB2, 0xFFBBC5F0, 0xFFB5DCD3, 0xFFC8D6B6};
    static final int[] LIGHT_MUTED = {0xFF685B82, 0xFF80564C, 0xFF58658E, 0xFF4E766F, 0xFF61714F};
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
            && CANVAS.length == n && SURFACES.length == n && RAISED.length == n && EDGES.length == n
            && MUTED.length == n && SOFT_ACCENTS.length == n && LIGHT_CANVAS.length == n
            && LIGHT_SURFACES.length == n && LIGHT_RAISED.length == n && LIGHT_EDGES.length == n
            && LIGHT_MUTED.length == n && new java.util.HashSet<>(java.util.Arrays.asList(NAMES)).size() == n
            && unique(ACCENTS) && unique(ACCENT_ENDS) && unique(CANVAS) && unique(SURFACES)
            && unique(RAISED) && unique(EDGES) && unique(MUTED) && unique(SOFT_ACCENTS);
    }
}
