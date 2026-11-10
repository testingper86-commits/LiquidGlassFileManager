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

    static void checkCancelled() throws IOException { OperationCancellation.checkCurrent(); }

    static File unique(File dir, String name) { return FileOperationEngine.unique(dir, name); }

    static void copy(File source, File target) throws IOException { FileOperationEngine.copy(source, target); }

    static boolean deleteTree(File file) { return FileOperationEngine.deleteTree(file); }

    static boolean treeSize(File file, long[] totals) { return FileOperationEngine.treeSize(file, totals); }

    static boolean treeSize(File file, long[] totals, java.util.function.BooleanSupplier cancelled) { return FileOperationEngine.treeSize(file, totals, cancelled); }


    private static void execute(MainActivity a, Done done, String failureMessage, Runnable operation) {
        a.busy(true);
        try {
            a.ops.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException failure) {
                    String detail = failure.getMessage();
                    String message = failureMessage + (detail == null || detail.trim().isEmpty() ? "" : ": " + detail);
                    a.runUiIfAlive(() -> { a.busy(false); done.run(message); });
                }
            });
        } catch (RuntimeException rejected) {
            a.runUiIfAlive(() -> { a.busy(false); done.run(failureMessage + ": operation could not be queued"); });
        }
    }

    static void paste(final MainActivity a, final File[] src, final File dst, final boolean move, final boolean skipExisting, final Done d) {
        execute(a, d, move ? "Move failed" : "Copy failed", () -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
                int ok = 0, fail = 0, skipped = 0;
                String err = "";
                int total = src.length, idx = 0;
                for (File s : src) {
                    if (OperationCancellation.isCancelled()) { fail++; err = "Operation cancelled"; break; }
                    idx++;
                    final int fi = idx, ft = total;
                    final String nm = s.getName();
                    a.runUiIfAlive(() -> a.progress((move ? "Moving " : "Copying ") + fi + " of " + ft + ": " + nm));
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
                a.runUiIfAlive(() -> { a.busy(false); d.run(msg); });

            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static void delete(final MainActivity a, final List<File> fs, final Done d) {
        execute(a, d, "Delete failed", () -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
                int ok = 0, failed = 0, total = fs.size(), idx = 0;
                boolean cancelled = false;
                for (File f : fs) {
                    if (OperationCancellation.isCancelled()) { cancelled = true; break; }
                    idx++;
                    final int fi = idx, ft = total;
                    final String nm = f.getName();
                    a.runUiIfAlive(() -> a.progress("Deleting " + fi + " of " + ft + ": " + nm));
                    if (deleteTree(f)) ok++;
                    else if (OperationCancellation.isCancelled()) { cancelled = true; break; }
                    else failed++;
                }
                final String msg = "Deleted " + ok + " item" + (ok == 1 ? "" : "s")
                        + (failed > 0 ? "; failed " + failed : "") + (cancelled ? "; cancelled" : "");
                a.runUiIfAlive(() -> { a.busy(false); d.run(msg); });

            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static void zip(final MainActivity a, final List<File> fs, final File destDir, final Done d) {
        execute(a, d, "ZIP creation failed", () -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
                String message;
                try { File output = ArchiveOperations.createZip(fs, destDir); message = "Created " + output.getName(); }
                catch (Exception failure) { message = "Zip failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()); }
                final String result = message;
                a.runUiIfAlive(() -> { a.busy(false); d.run(result); });
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static List<String> listZip(File zip) throws IOException {
        List<String> result = new ArrayList<>();
        for (ArchiveOperations.EntryInfo entry : ArchiveOperations.listZip(zip, ArchiveOperations.MAX_LIST_ENTRIES))
            result.add(entry.directory ? entry.name : entry.name + "   (" + ToxiVisualSystem.formatSize(entry.size) + ")");
        return result;
    }

    static void unzip(final MainActivity a, final File zip, final Done d) {
        execute(a, d, "ZIP extraction failed", () -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
                String message;
                try {
                    File destination = ArchiveOperations.extractZip(zip, zip.getParentFile(), progress -> a.runUiIfAlive(() -> a.progress(progress)));
                    message = "Extracted to " + destination.getName();
                } catch (Exception failure) { message = "Extract failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()); }
                final String result = message;
                a.runUiIfAlive(() -> { a.busy(false); d.run(result); });
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static Uri uri(Activity a, File f) {
        return FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
    }

    static void external(Activity a, File f, boolean chooser) {
        try {
            Entry e = new Entry(f);
            if (e.kind == FileKinds.APK || "apk".equalsIgnoreCase(e.ext)) {
                ToxiVisualSystem.toast(a, "Use the Install action for APK files");
                return;
            }
            Uri u = uri(a, f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            // APKs are rejected above; installation is available only through the explicit, user-confirmed PackageInstaller flow.
            // codeql[java/android/arbitrary-apk-installation]
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

    static void install(Activity activity, File file, java.util.concurrent.Executor executor) {
        if (activity == null || executor == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            ToxiVisualSystem.toast(activity, "Allow \"Install unknown apps\" for this app, then tap the APK again");
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        ToxiVisualSystem.toast(activity, "Preparing APK for Android's installer");
        final android.content.Context appContext = activity.getApplicationContext();
        try {
            executor.execute(() -> installWithPackageInstaller(activity, appContext, file));
        } catch (RuntimeException rejected) {
            ToxiVisualSystem.toast(activity, "APK installation could not be started");
        }
    }

    private static void installWithPackageInstaller(Activity activity, android.content.Context appContext, File file) {
        android.content.pm.PackageInstaller installer = appContext.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        boolean committed = false;
        try {
            if (file == null || !file.isFile() || !file.canRead() || file.length() <= 0L
                    || !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")
                    || java.nio.file.Files.isSymbolicLink(file.toPath())) {
                throw new IOException("Select a readable regular APK file");
            }
            android.content.pm.PackageInstaller.SessionParams params =
                    new android.content.pm.PackageInstaller.SessionParams(
                            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(file.length());
            sessionId = installer.createSession(params);
            android.content.pm.PackageInstaller.Session session = installer.openSession(sessionId);
            if (session == null) throw new IOException("Package installer session is unavailable");
            try {
                try (InputStream input = new FileInputStream(file);
                     OutputStream output = session.openWrite("base.apk", 0, file.length())) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new java.io.InterruptedIOException("APK installation was interrupted");
                        }
                        output.write(buffer, 0, read);
                    }
                    session.fsync(output);
                }
                Intent statusIntent = new Intent(appContext, ApkInstallStatusReceiver.class)
                        .setAction(appContext.getPackageName() + ".APK_INSTALL_STATUS." + sessionId);
                int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) pendingFlags |= android.app.PendingIntent.FLAG_MUTABLE;
                android.app.PendingIntent callback = android.app.PendingIntent.getBroadcast(
                        appContext, sessionId, statusIntent, pendingFlags);
                session.commit(callback.getIntentSender());
                committed = true;
            } finally {
                session.close();
            }
            postInstallToast(activity, "Waiting for Android's installation confirmation");
        } catch (Exception failure) {
            if (sessionId >= 0 && !committed) {
                try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            }
            postInstallToast(activity, "APK installation failed");
        }
    }

    private static void postInstallToast(Activity activity, String message) {
        if (activity == null) return;
        try {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) ToxiVisualSystem.toast(activity, message);
            });
        } catch (RuntimeException ignored) { }
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
