package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Environment;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.StatFs;
import android.provider.MediaStore;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                OperationCancellation.checkCurrent();
                List<StorageIndexEngine.VolumeSnapshot> volumes = readVolumes();
                Map<String, StorageIndexEngine.VolumeSnapshot> uniqueVolumes = new LinkedHashMap<>();
                for (StorageIndexEngine.VolumeSnapshot volume : volumes) uniqueVolumes.put(volume.volumeId, volume);

                StorageIndexEngine.TotalsBuilder totals = new StorageIndexEngine.TotalsBuilder();
                List<StorageIndexEngine.Snapshot> largest = new ArrayList<>();
                File rootDir = Environment.getExternalStorageDirectory();
                StorageIndexEngine.Result scan = StorageIndexEngine.scan(rootDir, 60,
                        StorageIndexEngine.MAX_INDEX_ENTRIES, true, true, snapshot -> {
                            if (snapshot.directory) return;
                            Entry entry = new Entry(snapshot.file, false, snapshot.modifiedAt, snapshot.length);
                            totals.add(Indexer.cat(entry.kind), snapshot.length);
                            if (largest.size() < 15) largest.add(snapshot);
                            else {
                                int smallest = 0;
                                for (int i = 1; i < largest.size(); i++) {
                                    if (largest.get(i).length < largest.get(smallest).length) smallest = i;
                                }
                                if (snapshot.length > largest.get(smallest).length) largest.set(smallest, snapshot);
                            }
                        });
                largest.sort((left, right) -> Long.compare(right.length, left.length));
                StorageIndexEngine.CategoryTotals categoryTotals = totals.finish(scan);
                String[] labels = Indexer.CAT_NAMES;
                if (labels.length != categoryTotals.bytes.length) {
                    throw new IllegalStateException("Storage category schema is inconsistent");
                }
                StringBuilder report = new StringBuilder();
                report.append("DEVICE VOLUME CAPACITY\n");
                if (uniqueVolumes.isEmpty()) report.append("System volume statistics unavailable.\n");
                for (StorageIndexEngine.VolumeSnapshot volume : uniqueVolumes.values()) {
                    report.append("\n").append(volume.label).append(volume.primary ? " (primary)" : "")
                            .append("\nTotal: ").append(formatBytes(volume.totalBytes))
                            .append("\nUsed: ").append(formatBytes(volume.usedBytes))
                            .append("\nAvailable to this app: ").append(formatBytes(volume.availableBytes))
                            .append("\n");
                }
                report.append("\nACCESSIBLE SHARED FILES\n")
                        .append("Files indexed: ").append(categoryTotals.totalIndexedFiles)
                        .append("\nIndexed file bytes: ").append(formatBytes(categoryTotals.totalIndexedBytes))
                        .append("\n\nBy category (accessible shared files only):\n");
                for (int i = 0; i < labels.length; i++) {
                    report.append(labels[i]).append(": ").append(categoryTotals.counts[i]).append(" files • ")
                            .append(formatBytes(categoryTotals.bytes[i])).append("\n");
                }
                OperationCancellation.checkCurrent();
                appendMediaStoreReference(report);
                OperationCancellation.checkCurrent();
                appendThisAppStorageStats(report);
                report.append("\nLargest accessible files (up to 15):\n");
                for (StorageIndexEngine.Snapshot item : largest) {
                    report.append("\n").append(formatBytes(item.length)).append(" — ").append(item.file.getAbsolutePath());
                }
                if (largest.isEmpty()) report.append("None found.\n");
                if (scan.incomplete || scan.truncated) {
                    report.append("\n\nINCOMPLETE: Some shared-storage paths could not be read or the safety limit was reached. "
                            + "Category totals are lower bounds for accessible files, not device capacity.\n");
                } else {
                    report.append("\n\nThe shared-storage traversal completed for paths visible to this app. "
                            + "Private app data and provider content outside the scanned shared-storage root are not included.\n");
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

    /** MediaStore is reported as its own inventory source; it is never added to the file-walk totals. */
    private void appendMediaStoreReference(StringBuilder report) {
        report.append("\nMEDIASTORE LIBRARY INVENTORY (REFERENCE ONLY)\n");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            report.append("MediaStore volume-wide inventory requires Android 10 or newer.\n");
            return;
        }
        String[] labels = {"Images", "Videos", "Audio", "Downloads"};
        int[] types = {0, 1, 2, 3};
        for (int index = 0; index < types.length; index++) {
            long bytes = 0L;
            int count = 0;
            boolean complete = true;
            try {
                for (String volume : MediaStore.getExternalVolumeNames(activity)) {
                    Uri uri = mediaStoreUri(types[index], volume);
                    if (uri == null) continue;
                    try (Cursor cursor = activity.getContentResolver().query(uri,
                            new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
                        if (cursor == null) { complete = false; continue; }
                        int sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                        if (sizeColumn < 0) { complete = false; continue; }
                        while (cursor.moveToNext()) {
                            if (cursor.isNull(sizeColumn)) { complete = false; continue; }
                            long size = cursor.getLong(sizeColumn);
                            if (size < 0) { complete = false; continue; }
                            bytes = StorageIndexEngine.saturatedAdd(bytes, size);
                            if (count < Integer.MAX_VALUE) count++;
                        }
                    }
                }
            } catch (RuntimeException providerFailure) {
                complete = false;
            }
            report.append(labels[index]).append(": ").append(count).append(" items • ")
                    .append(formatBytes(bytes)).append(complete ? " (MediaStore query complete)\n"
                            : " (partial or not fully accessible)\n");
        }
        report.append("These MediaStore figures are reference inventories and are intentionally excluded from shared-file totals to prevent duplicate counting.\n");
    }

    /** Query StorageStatsManager only for this app's own UID; broader package statistics need special user access. */
    private void appendThisAppStorageStats(StringBuilder report) {
        report.append("\nAPP-OWNED STORAGE (THIS APP ONLY)\n");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            report.append("StorageStatsManager requires Android 8 or newer.\n");
            return;
        }
        try {
            android.app.usage.StorageStatsManager statsManager =
                    (android.app.usage.StorageStatsManager) activity.getSystemService(android.content.Context.STORAGE_STATS_SERVICE);
            StorageManager storageManager = (StorageManager) activity.getSystemService(android.content.Context.STORAGE_SERVICE);
            File primary = Environment.getExternalStorageDirectory();
            if (statsManager == null || storageManager == null || primary == null) {
                report.append("System app-storage statistics unavailable.\n");
                return;
            }
            java.util.UUID storageUuid = storageManager.getUuidForPath(primary);
            android.app.usage.StorageStats stats = statsManager.queryStatsForUid(storageUuid, android.os.Process.myUid());
            long appBytes = stats.getAppBytes();
            long dataBytes = stats.getDataBytes();
            long cacheBytes = stats.getCacheBytes();
            report.append("App code: ").append(formatBytes(appBytes))
                    .append("\nApp data: ").append(formatBytes(dataBytes))
                    .append("\nApp cache: ").append(formatBytes(cacheBytes)).append("\n");
        } catch (SecurityException | IllegalArgumentException | IOException unavailable) {
            report.append("App-owned StorageStatsManager data is restricted or unavailable on this volume.\n");
        } catch (RuntimeException providerFailure) {
            report.append("App-owned StorageStatsManager data could not be read on this device.\n");
        }
    }

    private Uri mediaStoreUri(int type, String volume) {
        switch (type) {
            case 0: return MediaStore.Images.Media.getContentUri(volume);
            case 1: return MediaStore.Video.Media.getContentUri(volume);
            case 2: return MediaStore.Audio.Media.getContentUri(volume);
            case 3: return MediaStore.Downloads.getContentUri(volume);
            default: return null;
        }
    }

    private List<StorageIndexEngine.VolumeSnapshot> readVolumes() throws IOException {
        Map<String, StorageIndexEngine.VolumeSnapshot> result = new LinkedHashMap<>();
        StorageManager manager = (StorageManager) activity.getSystemService(android.content.Context.STORAGE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && manager != null) {
            for (StorageVolume volume : manager.getStorageVolumes()) {
                try {
                    File directory = volume.getDirectory();
                    if (directory == null || !directory.exists()) continue;
                    StatFs stats = new StatFs(directory.getAbsolutePath());
                    long total = Math.max(0L, stats.getTotalBytes());
                    long available = Math.max(0L, Math.min(total, stats.getAvailableBytes()));
                    String id = directory.getCanonicalPath();
                    String label = volume.getDescription(activity);
                    File primary = Environment.getExternalStorageDirectory();
                    boolean isPrimary = primary != null && id.equals(primary.getCanonicalPath());
                    result.put(id, new StorageIndexEngine.VolumeSnapshot(id, label, total, available, isPrimary));
                } catch (IOException | RuntimeException unavailable) {
                    // OEM StorageVolume implementations can fail while resolving a mounted volume.
                }
            }
        }
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null && primary.exists()) {
            try {
                StatFs stats = new StatFs(primary.getAbsolutePath());
                long total = Math.max(0L, stats.getTotalBytes());
                long available = Math.max(0L, Math.min(total, stats.getAvailableBytes()));
                String id = primary.getCanonicalPath();
                result.putIfAbsent(id, new StorageIndexEngine.VolumeSnapshot(id, "Primary shared storage",
                        total, available, true));
            } catch (IOException | RuntimeException unavailable) {
                // Keep unavailable volume statistics unavailable.
            }
        }
        return new ArrayList<>(result.values());
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
