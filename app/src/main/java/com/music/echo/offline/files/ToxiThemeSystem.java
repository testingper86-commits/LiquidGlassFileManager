package com.music.echo.offline.files;

/** ToxiFiles Prism design tokens: eight original colorways over one spatial material system. */
final class ToxiThemeSystem {
    static final String[] NAMES = {"Aether Black", "Glacier Mint", "Ion Cobalt", "Solar Alloy", "Orchid Flux", "Arctic Glass", "Verdant Signal", "Rose Quartz"};
    static final String[] DESCRIPTIONS = {
        "Carbon depth · spectral jade · quiet precision",
        "Deep teal · glacial mint · clear focus",
        "Midnight blue · ion cyan · technical clarity",
        "Graphite · molten amber · warm precision",
        "Ink violet · orchid light · dimensional calm",
        "Polar slate · electric blue · crisp contrast",
        "Forest graphite · phosphor green · organic signal",
        "Plum charcoal · rose quartz · soft luminosity"
    };
    static final int[] ACCENTS = {0xFF79F2C0,0xFF78E8D1,0xFF72C9FF,0xFFFFB66D,0xFFC3A0FF,0xFF8EDCFF,0xFFB8ED78,0xFFFFA7C8};
    static final int[] ACCENT_ENDS = {0xFF35C99A,0xFF2DBFA9,0xFF398BEA,0xFFE9823F,0xFF8B67E8,0xFF4CA9D8,0xFF70BC43,0xFFE875A6};
    static final int[] CANVAS = {0xFF070B10,0xFF07110F,0xFF080E19,0xFF110D0A,0xFF0F0B18,0xFF08121A,0xFF0A100B,0xFF140B12};
    static final int[] SURFACES = {0xFF111A23,0xFF10201D,0xFF111C2B,0xFF211811,0xFF1B1528,0xFF10212A,0xFF172016,0xFF24151F};
    static final int[] RAISED = {0xFF1A2934,0xFF19332D,0xFF1C3047,0xFF35251A,0xFF2C2140,0xFF1A3441,0xFF26351F,0xFF392330};
    static final int[] EDGES = {0xFF355B58,0xFF32665C,0xFF365B83,0xFF775035,0xFF604B82,0xFF376B80,0xFF4E6D3C,0xFF75465F};
    static final int[] MUTED = {0xFF9BB4B1,0xFF9CC8BD,0xFFA3BBD5,0xFFC8AD94,0xFFB9A9D1,0xFFA2C2D0,0xFFB2C29F,0xFFC9A8B8};
    static final int[] SOFT_ACCENTS = {0xFF183B36,0xFF17483F,0xFF193A5C,0xFF51301D,0xFF392752,0xFF194557,0xFF31451F,0xFF4C2639};
    static final int[] LIGHT_CANVAS = {0xFFF1F6F5,0xFFEFF8F5,0xFFF0F5FB,0xFFFFF5EC,0xFFF5F1FB,0xFFEEF7FA,0xFFF3F7EC,0xFFFBF1F5};
    static final int[] LIGHT_SURFACES = {0xFFFFFFFF,0xFFFCFFFE,0xFFFFFFFF,0xFFFFFCF8,0xFFFFFDFF,0xFFFCFEFF,0xFFFCFFF9,0xFFFFFCFE};
    static final int[] LIGHT_RAISED = {0xFFE6EFED,0xFFE1F2EC,0xFFE3EDF8,0xFFF7E9DB,0xFFEEE7F8,0xFFE1F0F5,0xFFE8F0DF,0xFFF6E5ED};
    static final int[] LIGHT_EDGES = {0xFFB9D1CB,0xFFB2D8CC,0xFFB8CDE4,0xFFE6C6A8,0xFFD0C1E7,0xFFB6D6E0,0xFFC8D8B7,0xFFE5C1D1};
    static final int[] LIGHT_MUTED = {0xFF536D68,0xFF4E7167,0xFF526A83,0xFF806044,0xFF695A84,0xFF4E707D,0xFF5D704C,0xFF80586C};
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
