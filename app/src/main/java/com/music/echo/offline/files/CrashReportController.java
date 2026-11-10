package com.music.echo.offline.files;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import androidx.activity.ComponentActivity;
import androidx.core.content.FileProvider;
import java.io.File;

/** Owns the user-facing lifecycle and sharing actions for locally stored crash reports. */
final class CrashReportController {
    private final ComponentActivity activity;

    CrashReportController(ComponentActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void maybeOfferCrashReport() {
        if (activity.isFinishing() || activity.isDestroyed() || !CrashLogger.hasReport(activity)) return;
        long crashTime = CrashLogger.reportTime(activity);
        if (crashTime <= CrashLogger.promptedTime(activity)) return;
        CrashLogger.markPrompted(activity, crashTime);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("App crash report available")
                .setMessage("ToxiFiles saved a local diagnostic report from the last unexpected crash. You can share it to help identify the cause. The report stays on this device unless you choose to share it.")
                .setPositiveButton("Share report", (dialog, which) -> shareCrashReport())
                .setNegativeButton("Later", null)
                .setNeutralButton("Delete report", (dialog, which) -> deleteCrashReport())
                .show();
    }

    void showCrashLogOptions() {
        if (!CrashLogger.hasReport(activity)) {
            new android.app.AlertDialog.Builder(activity).setTitle("Crash logs")
                    .setMessage("No crash report has been captured yet. If the app closes unexpectedly, ToxiFiles will save diagnostic details locally and offer them the next time you open the app.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new android.app.AlertDialog.Builder(activity).setTitle("Crash reports & diagnostics")
                .setMessage("A crash report is stored privately inside the app. It includes the exception stack trace, app version, Android version, device model, and crashing thread. Nothing is uploaded automatically.")
                .setPositiveButton("Share report", (dialog, which) -> shareCrashReport())
                .setNegativeButton("Close", null)
                .setNeutralButton("Delete report", (dialog, which) -> deleteCrashReport()).show();
    }

    private void shareCrashReport() {
        File report = CrashLogger.latest(activity);
        if (!report.isFile() || report.length() == 0) {
            ToxiVisualSystem.toast(activity, "No crash report available");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", report);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "ToxiFiles crash report");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newUri(activity.getContentResolver(), "Crash report", uri));
            activity.startActivity(Intent.createChooser(share, "Share crash report"));
        } catch (Exception failure) {
            ToxiVisualSystem.toast(activity, "Could not share crash report");
        }
    }

    private void deleteCrashReport() {
        boolean deleted = CrashLogger.deleteReports(activity);
        ToxiVisualSystem.toast(activity, deleted ? "Crash reports deleted" : "Could not delete crash report");
    }
}
