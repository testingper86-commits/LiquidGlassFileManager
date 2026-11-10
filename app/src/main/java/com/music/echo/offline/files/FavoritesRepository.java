package com.music.echo.offline.files;

import android.content.SharedPreferences;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns the existing favorite paths and their local SharedPreferences persistence. */
final class FavoritesRepository {
    private final Set<String> paths = new LinkedHashSet<>();

    Set<String> paths() { return paths; }

    void load(SharedPreferences preferences) {
        paths.clear();
        Set<String> saved = preferences.getStringSet("paths", null);
        if (saved != null) paths.addAll(saved);
    }

    void save(SharedPreferences preferences) {
        preferences.edit().putStringSet("paths", new HashSet<>(paths)).apply();
    }

    boolean contains(File file) { return paths.contains(file.getAbsolutePath()); }

    void toggle(File file) {
        String path = file.getAbsolutePath();
        if (!paths.remove(path)) paths.add(path);
    }

    void remove(List<File> files) {
        if (files == null) return;
        for (File file : files) if (file != null) paths.remove(file.getAbsolutePath());
    }

    void rename(File from, File to) {
        if (from != null && to != null && paths.remove(from.getAbsolutePath())) paths.add(to.getAbsolutePath());
    }
}
