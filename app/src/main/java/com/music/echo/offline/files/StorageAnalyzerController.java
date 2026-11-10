package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Owns bounded storage analysis, local report generation and explicit report sharing. */
final class StorageAnalyzerController {
    private final MainActivity activity;

    StorageAnalyzerController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void open() {
        if (!activity.hasAccess()) {
            activity.showStorageAccess();
            return;
        }
        ToxiVisualSystem.toast(activity, "Analyzing storage…");
        activity.executeIo(() -> {
            OperationCancellation.Token operationToken = activity.cancellation.begin();
            final String result;
            try {
                File rootDir = Environment.getExternalStorageDirectory();
                long[] totals = new long[6];
                long[] counts = new long[6];
                List<StorageIndexEngine.Snapshot> largest = new ArrayList<>();
                long[] totalBytes = {0};
                int[] totalFiles = {0};
                StorageIndexEngine.Result scan = StorageIndexEngine.scan(rootDir, 60,
                        StorageIndexEngine.MAX_INDEX_ENTRIES, true, true, snapshot -> {
                            if (snapshot.directory) return;
                            long size = snapshot.length;
                            totalBytes[0] += size;
                            totalFiles[0]++;
                            Entry entry = new Entry(snapshot.file, false, snapshot.modifiedAt, size);
                            int category = Math.min(5, Indexer.cat(entry.kind));
                            totals[category] += size;
                            counts[category]++;
                            if (largest.size() < 15) {
                                largest.add(snapshot);
                            } else {
                                int smallest = 0;
                                for (int i = 1; i < largest.size(); i++) {
                                    if (largest.get(i).length < largest.get(smallest).length) smallest = i;
                                }
                                if (size > largest.get(smallest).length) largest.set(smallest, snapshot);
                            }
                        });
                largest.sort((left, right) -> Long.compare(right.length, left.length));
                String[] labels = {"Images", "Videos", "Audio", "Documents", "Archives", "Other files"};
                StringBuilder report = new StringBuilder();
                report.append("Scanned internal shared storage\n\nFiles: ").append(totalFiles[0])
                        .append("\nEstimated file size total: ").append(formatBytes(totalBytes[0])).append("\n\nBy category:\n");
                for (int i = 0; i < labels.length; i++) {
                    report.append(labels[i]).append(": ").append(counts[i]).append(" files • ")
                            .append(formatBytes(totals[i])).append("\n");
                }
                report.append("\nLargest files found (up to 15):\n");
                int count = Math.min(15, largest.size());
                for (int i = 0; i < count; i++) {
                    StorageIndexEngine.Snapshot item = largest.get(i);
                    report.append("\n").append(formatBytes(item.length)).append(" — ").append(item.file.getAbsolutePath());
                }
                if (count == 0) report.append("None found.");
                if (scan.incomplete) {
                    report.append("\n\nNote: Scan was incomplete or reached the safety limit. Results are estimates.");
                }
                result = report.toString();
            } catch (Exception failure) {
                String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                activity.runUiIfAlive(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    ToxiVisualSystem.toast(activity, "Storage analysis failed: " + detail);
                });
                return;
            } finally {
                activity.cancellation.finish(operationToken);
            }
            activity.runUiIfAlive(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                new AlertDialog.Builder(activity).setTitle("Storage analysis")
                        .setMessage(result).setPositiveButton("Done", null)
                        .setNeutralButton("Share report", (dialog, which) -> {
                            try {
                                Intent share = new Intent(Intent.ACTION_SEND);
                                share.setType("text/plain");
                                share.putExtra(Intent.EXTRA_TEXT, result);
                                activity.startActivity(Intent.createChooser(share, "Share storage report"));
                            } catch (Exception shareFailure) {
                                ToxiVisualSystem.toast(activity, "Unable to share report");
                            }
                        }).show();
            });
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
