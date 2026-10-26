package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.io.File;

/** Per-file color labels — organization independent of folders. Stored as absolutePath -> colorIndex. */
final class Labels {
    static final int[] COLORS = {
            Color.rgb(239, 83, 80), Color.rgb(255, 167, 38), Color.rgb(255, 213, 79),
            Color.rgb(102, 187, 106), Color.rgb(66, 165, 245), Color.rgb(171, 71, 188)
    };
    static final String[] NAMES = {"Red", "Orange", "Yellow", "Green", "Blue", "Purple"};

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("lg_labels", Context.MODE_PRIVATE); }

    static int get(Context c, File f) { return sp(c).getInt(f.getAbsolutePath(), -1); }

    static void set(Context c, File f, int colorIndex) {
        SharedPreferences.Editor e = sp(c).edit();
        if (colorIndex < 0) e.remove(f.getAbsolutePath()); else e.putInt(f.getAbsolutePath(), colorIndex);
        e.apply();
    }

    /** Carry a label across a rename/move so it stays attached to the same file. */
    static void moved(Context c, File from, File to) {
        int v = get(c, from);
        if (v >= 0) { set(c, from, -1); set(c, to, v); }
    }
}
