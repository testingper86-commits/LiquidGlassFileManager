package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Background scan of internal storage: powers categories, recent files, storage chart and global search. */
final class Indexer {
    static final Indexer I = new Indexer();
    static final String[] CAT_NAMES = {"Images", "Videos", "Audio", "Documents", "Archives", "Apps", "Other"};

    volatile boolean ready, running, dirty, truncated;
    private static final int MAX_INDEX_ENTRIES = 150_000;
    long finished;
    List<Entry> all = new ArrayList<>();
    List<Entry> recent = new ArrayList<>();
    List<List<Entry>> cats = new ArrayList<>();
    long[] sizes = new long[7];
    int[] counts = new int[7];

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Entry> tmpAll;
    private List<List<Entry>> tmpCats;
    private long[] tmpSizes;
    private int[] tmpCounts;

    static int cat(int kind) {
        switch (kind) {
            case FileKinds.IMAGE: return 0;
            case FileKinds.VIDEO: return 1;
            case FileKinds.AUDIO: return 2;
            case FileKinds.PDF: case FileKinds.DOC: case FileKinds.SHEET: case FileKinds.SLIDE: case FileKinds.TEXT: return 3;
            case FileKinds.ARCHIVE: return 4;
            case FileKinds.APK: return 6;
            default: return 6;
        }
    }

    void start(final Runnable done) {
        if (running) return;
        running = true;
        dirty = false;
        truncated = false;
        new Thread(() -> {
            try {
                tmpAll = new ArrayList<>();
                tmpCats = new ArrayList<>();
                for (int i = 0; i < 7; i++) tmpCats.add(new ArrayList<Entry>());
                tmpSizes = new long[7];
                tmpCounts = new int[7];
                scan(Environment.getExternalStorageDirectory(), 0);
                java.util.Comparator<Entry> byDate = (x, y) -> Long.compare(y.mod, x.mod);
                for (List<Entry> l : tmpCats) Collections.sort(l, byDate);
                Collections.sort(tmpAll, byDate);
                List<Entry> rec = new ArrayList<>();
                for (Entry e : tmpAll) {
                    if (!e.dir && e.kind != FileKinds.OTHER) { rec.add(e); if (rec.size() >= 8) break; }
                }
                all = tmpAll; cats = tmpCats; sizes = tmpSizes; counts = tmpCounts; recent = rec;
                ready = true;
                finished = System.currentTimeMillis();
            } catch (Throwable t) {
                ready = true;
            } finally {
                running = false;
                ui.post(done);
            }
        }, "indexer").start();
    }

    private void scan(File dir, int depth) {
        if (depth > 9) return;
        if (tmpAll.size() >= MAX_INDEX_ENTRIES) { truncated = true; return; }
        try { if (java.nio.file.Files.isSymbolicLink(dir.toPath())) return; } catch (Exception ignored) { return; }
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (tmpAll.size() >= MAX_INDEX_ENTRIES) { truncated = true; return; }
            try { if (java.nio.file.Files.isSymbolicLink(f.toPath())) continue; } catch (Exception ignored) { continue; }
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth == 0 && n.equals("Android")) continue;
                tmpAll.add(new Entry(f, true, f.lastModified(), 0));
                scan(f, depth + 1);
            } else {
                Entry e = new Entry(f, false, f.lastModified(), f.length());
                int c = cat(e.kind);
                tmpAll.add(e);
                tmpCats.get(c).add(e);
                tmpSizes[c] += e.len;
                tmpCounts[c]++;
            }
        }
    }
}
