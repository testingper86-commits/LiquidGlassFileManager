package com.music.echo.offline.files;

/** Eight original, independent ToxiFiles visual identities. */
final class ToxiThemeSystem {
    static final String[] NAMES = {"Obsidian Alloy", "Mint Circuit", "Solar Ember", "Violet Arc", "Verdant Grid", "Arctic Pulse", "Nocturne Bloom", "Spectral Signal"};
    static final String[] DESCRIPTIONS = {
        "Midnight graphite · mineral mint · precision depth",
        "Deep evergreen · mineral mint · quiet clarity",
        "Charcoal oxide · molten coral · focused energy",
        "Ink violet · ion lavender · layered contrast",
        "Forest black · phosphor green · terminal clarity",
        "Polar navy · glacial cyan · cool precision",
        "Midnight plum · orchid light · soft contrast",
        "Blue-black · spectral periwinkle · crisp detail"
    };
    static final int[] ACCENTS = {0xFF9FE7C4,0xFF8FE8C2,0xFFFF916F,0xFFB7A0FF,0xFFB4EF76,0xFF72DDF2,0xFFE5A0D8,0xFFAAB9FF};
    static final int[] ACCENT_ENDS = {0xFF5CC8A1,0xFF48B995,0xFFE85E48,0xFF7654D8,0xFF68B847,0xFF3D9FCA,0xFFB65CA9,0xFF697EE0};
    static final int[] CANVAS = {0xFF090D13,0xFF07110E,0xFF140B09,0xFF0E0B17,0xFF091009,0xFF071118,0xFF130A14,0xFF0A0D19};
    static final int[] SURFACES = {0xFF141B24,0xFF10221B,0xFF251510,0xFF1A1428,0xFF152014,0xFF10212A,0xFF241426,0xFF171C30};
    static final int[] RAISED = {0xFF1C2733,0xFF183126,0xFF352019,0xFF271D3B,0xFF20311D,0xFF17313D,0xFF35203A,0xFF222A45};
    static final int[] EDGES = {0xFF3C4A59,0xFF315B49,0xFF75402F,0xFF57447E,0xFF486A38,0xFF356170,0xFF71436A,0xFF485887};
    static final int[] MUTED = {0xFF9AA8B7,0xFF94B8A8,0xFFC4A092,0xFFB3A6C9,0xFFA4B998,0xFF9DBBC5,0xFFC0A1BB,0xFFA7B2D1};
    static final int[] SOFT_ACCENTS = {0xFF273441,0xFF1A3A2D,0xFF48251C,0xFF30244B,0xFF263C1F,0xFF1A3945,0xFF42233F,0xFF293451};
    static final int[] LIGHT_CANVAS = {0xFFF3F6FA,0xFFF1F7F3,0xFFFFF4F0,0xFFF5F2FA,0xFFF4F7EF,0xFFF0F6F8,0xFFFAF2F7,0xFFF2F4FB};
    static final int[] LIGHT_SURFACES = {0xFFFFFFFF,0xFFFCFFFD,0xFFFFFCFA,0xFFFEFCFF,0xFFFCFFF9,0xFFFBFEFF,0xFFFFFBFE,0xFFFCFDFF};
    static final int[] LIGHT_RAISED = {0xFFE8EEF4,0xFFE5F1EA,0xFFF8EAE4,0xFFEDE7F7,0xFFEAF1E2,0xFFE2F0F4,0xFFF5E7F1,0xFFE7EBF8};
    static final int[] LIGHT_EDGES = {0xFFC6D0DC,0xFFBBD5C5,0xFFE8C6B8,0xFFD0C3E5,0xFFC9D9B8,0xFFB9D2DA,0xFFE1C3D7,0xFFC5CDE5};
    static final int[] LIGHT_MUTED = {0xFF596A7C,0xFF4D6B5A,0xFF806052,0xFF65577F,0xFF5A704B,0xFF4F6C78,0xFF79576D,0xFF566487};
    private ToxiThemeSystem() { }
    static int normalize(int index) { return Math.max(0, Math.min(NAMES.length - 1, index)); }
    static String description(int index) { return DESCRIPTIONS[normalize(index)]; }
    private static boolean unique(int[] values) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int value : values) seen.add(value);
        return seen.size() == 8;
    }
    static boolean hasIndependentTokens() {
        return NAMES.length == 8 && new java.util.HashSet<>(java.util.Arrays.asList(NAMES)).size() == 8
                && unique(ACCENTS) && unique(ACCENT_ENDS) && unique(CANVAS) && unique(SURFACES)
                && unique(RAISED) && unique(EDGES) && unique(MUTED) && unique(SOFT_ACCENTS);
    }
}
