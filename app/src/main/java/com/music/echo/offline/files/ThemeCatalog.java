package com.music.echo.offline.files;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Immutable catalogue for the eight isolated ToxiFiles visual finishes. */
final class ThemeCatalog {
    static final String[] NAMES = {
            "Cyborg Titanium",
            "Cyborg Neural",
            "Cyborg Overdrive",
            "Cyborg Phantom",
            "Matrix Hacker",
            "Cyber Hacker",
            "Dark Web",
            "Ghost Protocol"
    };

    static final int[] COLORS = {
            0xFF9CAFC4,
            0xFF00E5C0,
            0xFFFF4D36,
            0xFF843BFF,
            0xFF39FF14,
            0xFF00E5FF,
            0xFFB967FF,
            0xFF7B8CFF
    };

    static final String[] NOTES = {
            "Titanium silver • precision glass",
            "Neural teal • connected circuitry",
            "Overdrive red • high-energy glass",
            "Phantom violet • stealth finish",
            "Matrix green • terminal glow",
            "Cyber cyan • electric grid",
            "Dark web purple • deep contrast",
            "Ghost blue • spectral glass"
    };

    private ThemeCatalog() { }

    static int normalizeIndex(int index) {
        return Math.max(0, Math.min(NAMES.length - 1, index));
    }

    static String note(int index) {
        return NOTES[normalizeIndex(index)];
    }

    static boolean hasEightDistinctColors() {
        Set<Integer> colors = new HashSet<>();
        for (int color : COLORS) colors.add(color);
        return colors.size() == NAMES.length;
    }
}
