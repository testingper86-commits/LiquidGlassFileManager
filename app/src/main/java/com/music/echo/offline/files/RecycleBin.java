package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Soft-delete store. Deleted items are moved (not erased) into an app-private folder
 * so they can be restored later, same idea as a desktop Recycle Bin / Trash.
 * Each item lives inside its own "wrapper" folder named
 *   base64(original-parent-path) + "~" + deletedAtMillis
 * so the original location and delete time survive a process restart with no
 * separate database — the filesystem itself is the index.
 */
final class RecycleBin {
    /** Days before auto-purge; user-configurable in Settings (default 30). */
    static long maxAgeMs() { return Ui.sp.getInt("recyclebin_days", 30) * 24L * 60 * 60 * 1000; }

    static final class Item {
        final File file;           // the real file/folder, kept under its original name
        final File wrapper;        // its private wrapper folder inside the bin
        final File originalParent; // folder it was deleted from (may no longer exist)
        final long deletedAt;
        Item(File file, File wrapper, File originalParent, long deletedAt) {
            this.file = file; this.wrapper = wrapper; this.originalParent = originalParent; this.deletedAt = deletedAt;
        }
    }

    static File root(Context c) {
        File r = new File(c.getFilesDir(), "recyclebin");
        if (!r.exists()) r.mkdirs();
        return r;
    }

    private static String encode(String path) {
        return Base64.encodeToString(path.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String decode(String enc) {
        return new String(Base64.decode(enc, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
    }

    /** Moves each file/folder into the bin. Falls back to copy+delete if renameTo fails (e.g. cross-volume). */
    static void moveToBin(final MainActivity a, final List<File> src, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            FileOps.cancelRequested = false;
            int ok = 0, fail = 0;
            int total = src.size(), idx = 0;
            for (File s : src) {
                if (FileOps.cancelRequested) { fail++; break; }
                idx++;
                final int fi = idx, ft = total;
                final String nm = s.getName();
                a.runOnUiThread(() -> a.progress("Moving to Recycle Bin " + fi + " of " + ft + ": " + nm));
                try {
                    File parent = s.getParentFile();
                    if (parent == null) { fail++; continue; }
                    String wrapName = encode(parent.getAbsolutePath()) + "~" + java.util.UUID.randomUUID().toString();
                    File wrapper = new File(root(a), wrapName);
                    if (!wrapper.mkdirs()) { fail++; continue; }
                    File target = new File(wrapper, s.getName());
                    if (!s.renameTo(target)) { FileOps.copy(s, target); FileOps.deleteTree(s); }
                    ok++;
                } catch (Exception ex) { fail++; }
            }
            final int fok = ok, ffail = fail;
            final String msg = ffail == 0
                    ? "Moved " + fok + " item" + (fok == 1 ? "" : "s") + " to Recycle Bin"
                    : "Moved " + fok + ", failed " + ffail;
            a.runOnUiThread(() -> { a.busy(false); d.run(msg); });
        });
    }

    /** Lists current bin contents, newest-deleted first. Silently purges anything past MAX_AGE_MS. */
    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        File[] wraps = root(c).listFiles();
        if (wraps == null) return out;
        long now = System.currentTimeMillis();
        for (File w : wraps) {
            if (!w.isDirectory()) continue;
            String nm = w.getName();
            int i = nm.lastIndexOf('~');
            long deletedAt = 0;
            String origPath = "";
            try {
                if (i > 0) {
                    origPath = decode(nm.substring(0, i));
                    String suffix = nm.substring(i + 1);
                    try { deletedAt = Long.parseLong(suffix); } catch (NumberFormatException legacyOrUuid) { deletedAt = w.lastModified(); }
                }
            } catch (Exception ignored) { }
            if (deletedAt > 0 && now - deletedAt > maxAgeMs()) { FileOps.deleteTree(w); continue; }
            File[] kids = w.listFiles();
            if (kids == null || kids.length == 0) { FileOps.deleteTree(w); continue; }
            out.add(new Item(kids[0], w, new File(origPath), deletedAt));
        }
        out.sort((x, y) -> Long.compare(y.deletedAt, x.deletedAt));
        return out;
    }

    static void restore(final MainActivity a, final Item it, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            String msg;
            try {
                File destDir = it.originalParent;
                boolean fellBack = destDir == null || !destDir.isDirectory();
                if (fellBack) destDir = Environment.getExternalStorageDirectory();
                File target = FileOps.unique(destDir, it.file.getName());
                if (!it.file.renameTo(target)) { FileOps.copy(it.file, target); FileOps.deleteTree(it.file); }
                FileOps.deleteTree(it.wrapper);
                msg = fellBack ? "Restored to internal storage (original folder is gone)" : "Restored " + target.getName();
            } catch (Exception ex) { msg = "Restore failed: " + ex.getMessage(); }
            final String m = msg;
            a.runOnUiThread(() -> { a.busy(false); d.run(m); });
        });
    }

    static void permanentDelete(final MainActivity a, final List<Item> items, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            FileOps.cancelRequested = false;
            int ok = 0;
            for (Item it : items) { if (FileOps.cancelRequested) break; if (FileOps.deleteTree(it.wrapper)) ok++; }
            final int fok = ok;
            a.runOnUiThread(() -> { a.busy(false); d.run("Permanently deleted " + fok + " item" + (fok == 1 ? "" : "s")); });
        });
    }

    static void empty(final MainActivity a, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            FileOps.cancelRequested = false;
            File[] ws = root(a).listFiles();
            int n = ws == null ? 0 : ws.length;
            if (ws != null) for (File w : ws) { if (FileOps.cancelRequested) break; FileOps.deleteTree(w); }
            a.runOnUiThread(() -> { a.busy(false); d.run("Recycle Bin emptied (" + n + " item" + (n == 1 ? "" : "s") + ")"); });
        });
    }

    /** Total reclaimable size + item count — does a real tree-size walk, call off the UI thread. */
    static long[] totals(Context c) {
        long size = 0; int count = 0;
        for (Item it : list(c)) {
            long[] t = new long[2];
            FileOps.treeSize(it.file, t);
            size += t[0]; count++;
        }
        return new long[]{size, count};
    }
}
