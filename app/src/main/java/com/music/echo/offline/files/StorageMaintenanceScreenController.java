package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Owns duplicate-file and empty-folder maintenance screens and their safe Recycle Bin actions. */
final class StorageMaintenanceScreenController {
    private final MainActivity activity;

    StorageMaintenanceScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void openDuplicates() {
        Indexer.PublishedSnapshot index = Indexer.I.snapshot();
        if (!index.ready) {
            ToxiVisualSystem.toast(activity, "Still scanning storage — try again in a moment");
            return;
        }
        ToxiVisualSystem.toast(activity, "Scanning for duplicates…");
        activity.executeIo(() -> {
            OperationCancellation.Token operationToken = activity.cancellation.begin();
            try {
                final List<DupFinder.Group> groups = DupFinder.find(index.all);
                activity.runUiIfAlive(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    buildDuplicates(groups);
                    activity.showOverlay(activity.dupScroll);
                });
            } catch (IOException scanFailure) {
                if ("Operation cancelled".equals(scanFailure.getMessage())) return;
                final String detail = scanFailure.getMessage();
                activity.runUiIfAlive(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    ToxiVisualSystem.toast(activity, "Duplicate scan failed: "
                            + (detail == null || detail.trim().isEmpty() ? "a file could not be read" : detail));
                });
            } finally {
                activity.cancellation.finish(operationToken);
            }
        });
    }

    void buildDuplicates(final List<DupFinder.Group> groups) {
        activity.dup.removeAllViews();
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setContentDescription("Back");
        back.setOnClickListener(view -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        header.addView(ToxiVisualSystem.text(activity, "Duplicate Files", 22, ToxiVisualSystem.TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        header.setMinimumHeight(ToxiVisualSystem.dp(56));
        activity.dup.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (groups.isEmpty()) {
            TextView empty = ToxiVisualSystem.text(activity, "No exact duplicates found", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            empty.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            activity.dup.addView(empty);
            return;
        }
        long reclaimable = 0;
        for (DupFinder.Group group : groups) reclaimable += group.size * (group.files.size() - 1);
        TextView summary = ToxiVisualSystem.text(activity, groups.size() + " duplicate group"
                + (groups.size() == 1 ? "" : "s") + " — up to " + ToxiVisualSystem.formatSize(reclaimable)
                + " reclaimable", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        summary.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(6));
        activity.dup.addView(summary);

        TextView bulk = ToxiVisualSystem.text(activity, "Move all but the oldest copy to Recycle Bin", 14, ToxiVisualSystem.ACCENT, true);
        bulk.setBackground(ToxiVisualSystem.surface(18, false));
        bulk.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12));
        bulk.setGravity(Gravity.CENTER);
        ToxiVisualSystem.interactive(bulk);
        bulk.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Move duplicates to Recycle Bin?")
                .setMessage("Keeps the oldest file in each group, moves the rest to Recycle Bin — they can still be restored from there.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move", (dialog, which) -> {
                    List<File> victims = new ArrayList<>();
                    for (DupFinder.Group group : groups) {
                        File keep = group.files.get(0);
                        for (File file : group.files) if (file.lastModified() < keep.lastModified()) keep = file;
                        for (File file : group.files) if (file != keep) victims.add(file);
                    }
                    RecycleBin.moveToBin(activity, victims, message -> {
                        ToxiVisualSystem.toast(activity, message);
                        activity.afterOps();
                        openDuplicates();
                    });
                }).show());
        activity.dup.addView(bulk, activity.lpm(-1, -2, 16, 4, 16, 14));

        for (DupFinder.Group group : groups) {
            TextView groupHeader = ToxiVisualSystem.text(activity, group.files.size() + " copies • "
                    + ToxiVisualSystem.formatSize(group.size) + " each", 12, ToxiVisualSystem.TEXT_SECONDARY, true);
            groupHeader.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2));
            activity.dup.addView(groupHeader);
            for (File file : group.files) {
                Entry entry = new Entry(file);
                View row = ToxiFileRow.create(activity);
                ToxiFileRow.Holder holder = (ToxiFileRow.Holder) row.getTag();
                ToxiFileRow.bind(holder, entry, false, false, file.getParentFile() == null ? "" : file.getParentFile().getAbsolutePath());
                View.OnClickListener action = view -> new AlertDialog.Builder(activity).setTitle("Move to Recycle Bin?")
                        .setMessage(file.getAbsolutePath()).setNegativeButton("Cancel", null)
                        .setPositiveButton("Move", (dialog, which) -> RecycleBin.moveToBin(activity,
                                java.util.Collections.singletonList(file), message -> {
                                    ToxiVisualSystem.toast(activity, message);
                                    activity.afterOps();
                                    openDuplicates();
                                })).show();
                row.setOnClickListener(action);
                holder.more.setOnClickListener(action);
                activity.dup.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void openCleanup() {
        Indexer.PublishedSnapshot index = Indexer.I.snapshot();
        if (!index.ready) {
            ToxiVisualSystem.toast(activity, "Still scanning storage — try again in a moment");
            return;
        }
        ToxiVisualSystem.toast(activity, "Scanning for empty folders…");
        activity.executeIo(() -> {
            OperationCancellation.Token operationToken = activity.cancellation.begin();
            try {
                final List<File> emptyFolders = new ArrayList<>();
                for (Entry entry : index.all) {
                    if (OperationCancellation.isCancelled()) return;
                    if (entry.dir) {
                        File[] children = entry.f.listFiles();
                        if (OperationCancellation.isCancelled()) return;
                        if (children != null && children.length == 0) emptyFolders.add(entry.f);
                    }
                }
                if (OperationCancellation.isCancelled()) return;
                activity.runUiIfAlive(() -> {
                    buildEmptyFolders(emptyFolders);
                    activity.showOverlay(activity.dupScroll);
                });
            } finally {
                activity.cancellation.finish(operationToken);
            }
        });
    }

    void buildEmptyFolders(final List<File> emptyFolders) {
        activity.dup.removeAllViews();
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setContentDescription("Back");
        back.setOnClickListener(view -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        header.addView(ToxiVisualSystem.text(activity, "Empty Folders", 22, ToxiVisualSystem.TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        header.setMinimumHeight(ToxiVisualSystem.dp(56));
        activity.dup.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (emptyFolders.isEmpty()) {
            TextView empty = ToxiVisualSystem.text(activity, "No empty folders found", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            empty.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            activity.dup.addView(empty);
            return;
        }
        TextView summary = ToxiVisualSystem.text(activity, emptyFolders.size() + " empty folder"
                + (emptyFolders.size() == 1 ? "" : "s"), 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        summary.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(6));
        activity.dup.addView(summary);

        TextView bulk = ToxiVisualSystem.text(activity, "Delete all empty folders", 14, ToxiVisualSystem.ACCENT, true);
        bulk.setBackground(ToxiVisualSystem.surface(18, false));
        bulk.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12));
        bulk.setGravity(Gravity.CENTER);
        ToxiVisualSystem.interactive(bulk);
        bulk.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Delete " + emptyFolders.size() + " empty folders?")
                .setMessage("They contain nothing, so this goes straight to Recycle Bin, same as any other delete.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> RecycleBin.moveToBin(activity, emptyFolders, message -> {
                    ToxiVisualSystem.toast(activity, message);
                    activity.afterOps();
                    openCleanup();
                })).show());
        activity.dup.addView(bulk, activity.lpm(-1, -2, 16, 4, 16, 14));

        for (File file : emptyFolders) {
            Entry entry = new Entry(file);
            View row = ToxiFileRow.create(activity);
            ToxiFileRow.Holder holder = (ToxiFileRow.Holder) row.getTag();
            ToxiFileRow.bind(holder, entry, false, false, file.getParentFile() == null ? "" : file.getParentFile().getAbsolutePath());
            View.OnClickListener action = view -> RecycleBin.moveToBin(activity,
                    java.util.Collections.singletonList(file), message -> {
                        ToxiVisualSystem.toast(activity, message);
                        activity.afterOps();
                        openCleanup();
                    });
            row.setOnClickListener(action);
            holder.more.setOnClickListener(action);
            activity.dup.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }
}
