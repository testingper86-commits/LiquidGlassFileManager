package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft-delete store. Deleted items are moved (not erased) into an app-private folder
 * so they can be restored later, same idea as a desktop Recycle Bin / Trash.
 * Each item lives inside its own "wrapper" folder named
 *   base64(original-parent-path) + "~" + deletedAtMillis
 * so the original location and delete time survive a process restart with no
 * separate database — the filesystem itself is the index.
 */
final class RecycleBin {
    private static final Set<String> EXPIRY_CLEANUP_PENDING = ConcurrentHashMap.newKeySet();

    /** Days before auto-purge; user-configurable in Settings (default 30). */
    static long maxAgeMs() { return ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30) * 24L * 60 * 60 * 1000; }

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
        a.executeOperation(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            int ok = 0, fail = 0;
            int total = src.size(), idx = 0;
            for (File s : src) {
                if (OperationCancellation.isCancelled()) { fail++; break; }
                idx++;
                final int fi = idx, ft = total;
                final String nm = s.getName();
                a.runUiIfAlive(() -> a.progress("Moving to Recycle Bin " + fi + " of " + ft + ": " + nm));
                try {
                    File parent = s.getParentFile();
                    if (parent == null) { fail++; continue; }
                    String wrapName = encode(parent.getAbsolutePath()) + "~" + java.util.UUID.randomUUID().toString();
                    File wrapper = new File(root(a), wrapName);
                    if (!wrapper.mkdir()) { fail++; continue; }
                    File target = new File(wrapper, s.getName());
                    boolean payloadSafe = false;
                    try {
                        try { java.nio.file.Files.move(s.toPath(), target.toPath()); payloadSafe = true; }
                        catch (java.io.IOException moveError) {
                            FileOps.copy(s, target);
                            FileOps.checkCancelled();
                            payloadSafe = true;
                            if (!FileOps.deleteTree(s)) throw new java.io.IOException("Copied to Recycle Bin, but source could not be removed; both copies were kept");
                        }
                        ok++;
                    } catch (Exception moveError) {
                        if (!payloadSafe) FileOps.deleteTree(wrapper);
                        throw moveError;
                    }
                } catch (Exception ex) { fail++; }
            }
            final int fok = ok, ffail = fail;
            final String msg = ffail == 0
                    ? "Moved " + fok + " item" + (fok == 1 ? "" : "s") + " to Recycle Bin"
                    : "Moved " + fok + ", failed " + ffail;
            a.runUiIfAlive(() -> { a.busy(false); d.run(msg); });
        
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    /** Lists current bin contents, newest-deleted first. Silently purges anything past MAX_AGE_MS. */
    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        List<File> expired = new ArrayList<>();
        File[] wraps = root(c).listFiles();
        if (wraps == null) return out;
        long now = System.currentTimeMillis();
        long retention = maxAgeMs();
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
            if (deletedAt > 0 && now - deletedAt > retention) {
                String path = w.getAbsolutePath();
                if (EXPIRY_CLEANUP_PENDING.add(path)) expired.add(w);
                continue;
            }
            File[] kids = w.listFiles();
            if (kids == null) continue;
            if (kids.length != 1) continue; // Preserve malformed wrappers instead of discarding unrecognized payloads.
            out.add(new Item(kids[0], w, new File(origPath), deletedAt));
        }
        scheduleExpiryCleanup(expired);
        out.sort((x, y) -> Long.compare(y.deletedAt, x.deletedAt));
        return out;
    }

    private static void scheduleExpiryCleanup(List<File> expired) {
        if (expired.isEmpty()) return;
        try {
            TaskCoordinator.execute(TaskCoordinator.Lane.IO, () -> {
                try {
                    for (File wrapper : expired) FileOps.deleteTree(wrapper);
                } finally {
                    for (File wrapper : expired) EXPIRY_CLEANUP_PENDING.remove(wrapper.getAbsolutePath());
                }
            });
        } catch (RuntimeException rejected) {
            for (File wrapper : expired) EXPIRY_CLEANUP_PENDING.remove(wrapper.getAbsolutePath());
        }
    }

    static void restore(final MainActivity a, final Item it, final FileOps.Done d) {
        a.busy(true);
        a.executeOperation(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            String msg;
            try {
                File destDir = it.originalParent;
                boolean fellBack = destDir == null || !destDir.isDirectory();
                if (fellBack) {
                    destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!destDir.isDirectory() && !destDir.mkdirs()) throw new java.io.IOException("Unable to create Downloads folder for restore");
                }
                File target = FileOps.unique(destDir, it.file.getName());
                try { java.nio.file.Files.move(it.file.toPath(), target.toPath()); }
                catch (java.io.IOException moveError) {
                    FileOps.copy(it.file, target);
                    FileOps.checkCancelled();
                    if (!FileOps.deleteTree(it.file)) throw new java.io.IOException("Restored a copy, but the Recycle Bin source could not be removed; both copies were kept", moveError);
                }
                if (!FileOps.deleteTree(it.wrapper)) throw new java.io.IOException("Restored file, but Recycle Bin cleanup failed");
                msg = fellBack ? "Restored to Downloads (original folder is gone)" : "Restored " + target.getName();
            } catch (Exception ex) { msg = "Restore failed: " + ex.getMessage(); }
            final String m = msg;
            a.runUiIfAlive(() -> { a.busy(false); d.run(m); });
        
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static void permanentDelete(final MainActivity a, final List<Item> items, final FileOps.Done d) {
        a.busy(true);
        a.executeOperation(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            int ok = 0;
            for (Item it : items) { if (OperationCancellation.isCancelled()) break; if (FileOps.deleteTree(it.wrapper)) ok++; }
            final int fok = ok;
            a.runUiIfAlive(() -> { a.busy(false); d.run("Permanently deleted " + fok + " item" + (fok == 1 ? "" : "s")); });
        
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static void empty(final MainActivity a, final FileOps.Done d) {
        a.busy(true);
        a.executeOperation(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            File[] ws = root(a).listFiles();
            int deleted = 0, failed = 0;
            boolean cancelled = false;
            if (ws != null) for (File w : ws) {
                if (OperationCancellation.isCancelled()) { cancelled = true; break; }
                if (FileOps.deleteTree(w)) deleted++; else failed++;
            }
            final int fdeleted = deleted, ffailed = failed;
            final boolean fcancelled = cancelled;
            final String msg = "Recycle Bin: removed " + fdeleted + " item" + (fdeleted == 1 ? "" : "s")
                    + (ffailed > 0 ? "; failed " + ffailed : "") + (fcancelled ? "; cancelled" : "");
            a.runUiIfAlive(() -> { a.busy(false); d.run(msg); });
        
            } finally { a.cancellation.finish(operationToken); }
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
