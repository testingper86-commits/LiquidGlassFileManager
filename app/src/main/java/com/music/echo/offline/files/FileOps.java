package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Copy / move / delete / zip / unzip / open / share. Heavy work runs on MainActivity.ops. */
final class FileOps {
    interface Done { void run(String msg); }

    static volatile boolean cancelRequested;
    static void requestCancel() { cancelRequested = true; }
    static void checkCancelled() throws IOException { if (cancelRequested) throw new IOException("Operation cancelled"); }

    static File unique(File dir, String name) {
        if (dir == null || BatchRenameEngine.validateName(name) != null) {
            throw new IllegalArgumentException("Invalid destination filename");
        }
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int i = name.lastIndexOf('.');
        if (i > 0) { base = name.substring(0, i); ext = name.substring(i); }
        for (int n = 1; n < 1000; n++) {
            f = new File(dir, base + " (" + n + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    private static File createUniqueFile(File dir, String name) throws IOException {
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int n = 0; n < 1000; n++) {
            String candidate = n == 0 ? name : base + " (" + n + ")" + ext;
            File file = new File(dir, candidate);
            try { if (file.createNewFile()) return file; }
            catch (IOException error) { if (!file.exists()) throw error; }
        }
        throw new IOException("Unable to reserve a unique output filename");
    }

    static void copy(File source, File target) throws IOException {
        checkCancelled();
        if (source == null || target == null) throw new IOException("Source and destination are required");
        if (target.exists()) throw new IOException("Destination already exists: " + target.getName());
        if (source.isDirectory()) {
            String sourcePath = source.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (targetPath.equals(sourcePath) || targetPath.startsWith(sourcePath + File.separator))
                throw new IOException("Folder destination is inside the source tree");
        }
        File parent = target.getParentFile();
        if (parent == null || !parent.isDirectory()) throw new IOException("Destination folder is unavailable");
        File staging = new File(parent, ".toxifiles-copy-" + java.util.UUID.randomUUID());
        try {
            copyInto(source, staging);
            checkCancelled();
            java.nio.file.Files.move(staging.toPath(), target.toPath());
        } catch (IOException copyError) {
            deleteTree(staging);
            throw copyError;
        } catch (RuntimeException copyError) {
            deleteTree(staging);
            throw copyError;
        }
    }

    private static void copyInto(File source, File target) throws IOException {
        checkCancelled();
        if (java.nio.file.Files.isSymbolicLink(source.toPath())) throw new IOException("Symbolic links are not copied");
        if (source.isDirectory()) {
            if (!target.mkdir()) throw new IOException("Can't create destination folder " + target.getName());
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Can't read folder " + source.getName());
            for (File child : children) copyInto(child, new File(target, child.getName()));
        } else {
            if (!target.createNewFile()) throw new IOException("Destination already exists: " + target.getName());
            try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) > 0) { checkCancelled(); out.write(buffer, 0, count); }
                out.flush();
                out.getFD().sync();
            } catch (IOException writeError) {
                target.delete();
                throw writeError;
            }
        }
    }

    static boolean deleteTree(File f) {
        try {
            if (f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) deleteTree(k);
            }
        } catch (Throwable ignored) { }
        return f.delete();
    }

    static void treeSize(File f, long[] t) {
        if (java.nio.file.Files.isSymbolicLink(f.toPath())) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) treeSize(k, t);
        } else { t[0] += f.length(); t[1]++; }
    }

    static void paste(final MainActivity a, final File[] src, final File dst, final boolean move, final Done d) {
        paste(a, src, dst, move, false, d);
    }

    static void paste(final MainActivity a, final File[] src, final File dst, final boolean move, final boolean skipExisting, final Done d) {
        cancelRequested = false;
        a.busy(true);
        a.ops.execute(() -> {
            int ok = 0, fail = 0, skipped = 0;
            String err = "";
            int total = src.length, idx = 0;
            for (File s : src) {
                if (cancelRequested) { fail++; err = "Operation cancelled"; break; }
                idx++;
                final int fi = idx, ft = total;
                final String nm = s.getName();
                a.runOnUiThread(() -> a.progress((move ? "Moving " : "Copying ") + fi + " of " + ft + ": " + nm));
                try {
                    if (move && dst.equals(s.getParentFile())) { ok++; continue; }
                    if (skipExisting && new File(dst, s.getName()).exists()) { skipped++; continue; }
                    if (s.isDirectory() && (dst.getCanonicalPath() + "/").startsWith(s.getCanonicalPath() + "/")) {
                        fail++; err = "Can't put a folder inside itself"; continue;
                    }
                    File t = unique(dst, s.getName());
                    if (move) {
                        try {
                            java.nio.file.Files.move(s.toPath(), t.toPath());
                        } catch (IOException moveError) {
                            copy(s, t);
                            if (!deleteTree(s)) throw new IOException("Copied to destination, but source could not be removed; both copies were kept", moveError);
                        }
                    } else copy(s, t);
                    ok++;
                } catch (Exception ex) { fail++; err = String.valueOf(ex.getMessage()); }
            }
            final String msg = fail == 0 ? (move ? "Moved " : "Copied ") + ok + " item" + (ok == 1 ? "" : "s")
                    + (skipped > 0 ? "; skipped " + skipped + " existing item" + (skipped == 1 ? "" : "s") : "") : err;
            a.runOnUiThread(() -> { a.busy(false); d.run(msg); });
        });
    }

    static void delete(final MainActivity a, final List<File> fs, final Done d) {
        cancelRequested = false;
        a.busy(true);
        a.ops.execute(() -> {
            int ok = 0, failed = 0, total = fs.size(), idx = 0;
            boolean cancelled = false;
            for (File f : fs) {
                if (cancelRequested) { cancelled = true; break; }
                idx++;
                final int fi = idx, ft = total;
                final String nm = f.getName();
                a.runOnUiThread(() -> a.progress("Deleting " + fi + " of " + ft + ": " + nm));
                if (deleteTree(f)) ok++; else failed++;
            }
            final String msg = "Deleted " + ok + " item" + (ok == 1 ? "" : "s")
                    + (failed > 0 ? "; failed " + failed : "") + (cancelled ? "; cancelled" : "");
            a.runOnUiThread(() -> { a.busy(false); d.run(msg); });
        });
    }

    static void zip(final MainActivity a, final List<File> fs, final File destDir, final Done d) {
        cancelRequested = false;
        a.busy(true);
        a.ops.execute(() -> {
            String msg;
            File out = null;
            try {
                String base = fs.size() == 1 ? fs.get(0).getName() : "Archive";
                int i = base.lastIndexOf('.');
                if (fs.size() == 1 && i > 0 && !fs.get(0).isDirectory()) base = base.substring(0, i);
                out = createUniqueFile(destDir, base + ".zip");
                try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out, false)))) {
                    Set<String> rootNames = new HashSet<>();
                    for (File f : fs) {
                        String entryName = f.getName();
                        if (!rootNames.add(entryName)) {
                            String rootBase = entryName, rootExt = "";
                            int dot = entryName.lastIndexOf('.');
                            if (dot > 0 && !f.isDirectory()) { rootBase = entryName.substring(0, dot); rootExt = entryName.substring(dot); }
                            int suffix = 2;
                            do { entryName = rootBase + " (" + suffix++ + ")" + rootExt; } while (!rootNames.add(entryName));
                        }
                        addZip(z, f, entryName, out);
                    }
                }
                msg = "Created " + out.getName();
            } catch (Exception ex) { if (out != null) deleteTree(out); msg = "Zip failed: " + ex.getMessage(); }
            final String m = msg;
            a.runOnUiThread(() -> { a.busy(false); d.run(m); });
        });
    }

    private static void addZip(ZipOutputStream z, File f, String name, File skip) throws IOException {
        checkCancelled();
        if (f.equals(skip) || java.nio.file.Files.isSymbolicLink(f.toPath())) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) throw new IOException("Can't read folder " + f.getName());
            for (File k : kids) addZip(z, k, name + "/" + k.getName(), skip);
        } else {
            z.putNextEntry(new ZipEntry(name));
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) { checkCancelled(); z.write(buf, 0, n); }
            }
            z.closeEntry();
        }
    }

    static List<String> listZip(File zip) throws IOException {
        List<String> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements() && out.size() < 400) {
                ZipEntry e = en.nextElement();
                out.add(e.isDirectory() ? e.getName() : e.getName() + "   (" + ToxiVisualSystem.formatSize(e.getSize()) + ")");
            }
        }
        return out;
    }

    static void unzip(final MainActivity a, final File zip, final Done d) {
        cancelRequested = false;
        a.busy(true);
        a.ops.execute(() -> {
            String msg;
            File dest = null;
            boolean destCreated = false;
            try {
                String base = zip.getName();
                int i = base.lastIndexOf('.');
                if (i > 0) base = base.substring(0, i);
                dest = unique(zip.getParentFile(), base);
                if (!dest.mkdir()) throw new IOException("Unable to reserve extraction folder without overwriting existing data");
                destCreated = true;
                String root = dest.getCanonicalPath();
                long expanded = 0L;
                final long initialFreeSpace = dest.getUsableSpace();
                java.util.Set<String> extractedPaths = new java.util.HashSet<>();
                try (ZipFile z = new ZipFile(zip)) {
                    int total = z.size(), idx = 0;
                    if (total > 20_000) throw new IOException("Archive has too many entries");
                    Enumeration<? extends ZipEntry> en = z.entries();
                    while (en.hasMoreElements()) {
                        checkCancelled();
                        ZipEntry e = en.nextElement();
                        idx++;
                        final int fi = idx, ft = total;
                        final String nm = e.getName();
                        a.runOnUiThread(() -> a.progress("Extracting " + fi + " of " + ft + ": " + nm));
                        String entryName = e.getName();
                        if (entryName == null || entryName.isEmpty() || entryName.startsWith("/") || entryName.startsWith("\\") || entryName.matches("^[A-Za-z]:.*")) continue;
                        String normalizedName = ArchivePath.normalize(entryName, e.isDirectory());
                        if (!extractedPaths.add(normalizedName)) throw new IOException("Archive contains duplicate entry paths");
                        File o = new File(dest, normalizedName);
                        String cp = o.getCanonicalPath();
                        if (!cp.startsWith(root + File.separator)) continue; // zip-slip guard
                        if (e.isDirectory()) {
                            if (java.nio.file.Files.isSymbolicLink(o.toPath())) throw new IOException("Archive directory path is a symbolic link");
                            if (!o.mkdirs() && !o.isDirectory()) throw new IOException("Unable to create archive folder");
                            continue;
                        }
                        if (e.getSize() > 0 && e.getSize() > 2L * 1024 * 1024 * 1024) throw new IOException("Archive entry exceeds 2 GB limit");
                        if (o.getParentFile() != null && !o.getParentFile().mkdirs() && !o.getParentFile().isDirectory()) throw new IOException("Unable to create archive parent folder");
                        if (java.nio.file.Files.isSymbolicLink(o.toPath()) || !o.createNewFile()) throw new IOException("Archive destination already exists or is unsafe");
                        long entryBytes = 0L;
                        try (InputStream in = z.getInputStream(e); OutputStream out = new FileOutputStream(o, false)) {
                            byte[] buf = new byte[65536];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                checkCancelled();
                                entryBytes += n; expanded += n;
                                if (entryBytes > 2L * 1024 * 1024 * 1024 || expanded > 4L * 1024 * 1024 * 1024) throw new IOException("Archive expanded-size limit exceeded");
                                if (expanded > initialFreeSpace) throw new IOException("Not enough free space to extract archive");
                                out.write(buf, 0, n);
                            }
                        } catch (Exception writeError) { o.delete(); throw writeError; }
                    }
                }
                msg = "Extracted to " + dest.getName();
            } catch (Exception ex) { if (destCreated && dest != null) deleteTree(dest); msg = "Extract failed: " + ex.getMessage(); }
            final String m = msg;
            a.runOnUiThread(() -> { a.busy(false); d.run(m); });
        });
    }

    static Uri uri(Activity a, File f) {
        return FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
    }

    static void external(Activity a, File f, boolean chooser) {
        try {
            Entry e = new Entry(f);
            Uri u = uri(a, f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, FileKinds.mime(e));
            i.setClipData(ClipData.newRawUri("", u));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(chooser ? Intent.createChooser(i, "Open with") : i);
        } catch (ActivityNotFoundException ex) {
            try {
                Uri u = uri(a, f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, "*/*");
                i.setClipData(ClipData.newRawUri("", u));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(Intent.createChooser(i, "Open with"));
            } catch (Exception e2) { ToxiVisualSystem.toast(a, "No app installed that can open this file"); }
        } catch (Exception ex) { ToxiVisualSystem.toast(a, "Can't open this file"); }
    }

    static void install(Activity a, File f) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
                ToxiVisualSystem.toast(a, "Allow \"Install unknown apps\" for this app, then tap the APK again");
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                return;
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri(a, f), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(i);
        } catch (Exception ex) { ToxiVisualSystem.toast(a, "Can't install this APK"); }
    }

    static void share(Activity a, List<File> fs) {
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            File first = null;
            for (File f : fs) if (f.isFile()) { if (first == null) first = f; uris.add(uri(a, f)); }
            if (uris.isEmpty()) { ToxiVisualSystem.toast(a, "Folders can't be shared — compress them first"); return; }
            Intent i;
            if (uris.size() == 1) {
                i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                i.setType(FileKinds.mime(new Entry(first)));
            } else {
                i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                i.setType("*/*");
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception ex) { ToxiVisualSystem.toast(a, "Can't share this file"); }
    }
}
