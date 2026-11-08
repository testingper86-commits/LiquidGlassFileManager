package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Owns Recycle Bin screen loading, rendering, retention controls and item actions. */
final class RecycleBinScreenController {
    private final MainActivity activity;

    RecycleBinScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void show() {
        reload();
        activity.showOverlay(activity.recycleScroll);
    }

    void reload() {
        render(null, true);
        if (!activity.executeIo(() -> {
            List<RecycleBin.Item> items = RecycleBin.list(activity);
            activity.runUiIfAlive(() -> render(items, false));
        })) {
            render(null, false);
        }
    }

    private void render(List<RecycleBin.Item> items, boolean loading) {
        activity.recycle.removeAllViews();
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setOnClickListener(view -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        header.addView(ToxiVisualSystem.text(activity, "Recycle Bin", 24, ToxiVisualSystem.TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView emptyButton = ToxiVisualSystem.text(activity, "Empty", 14, ToxiVisualSystem.ACCENT, true);
        emptyButton.setPadding(ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(10));
        emptyButton.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Empty Recycle Bin?")
                .setMessage("Every item will be permanently deleted. This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Empty", (dialog, which) -> RecycleBin.empty(activity, message -> {
                    ToxiVisualSystem.toast(activity, message);
                    reload();
                })).show());
        ToxiVisualSystem.interactive(emptyButton);
        header.addView(emptyButton, new LinearLayout.LayoutParams(-2, -2));
        header.setMinimumHeight(ToxiVisualSystem.dp(56));
        activity.recycle.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView note = ToxiVisualSystem.text(activity, "Items are kept for "
                + ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30)
                + " days, then removed automatically. Change this in Settings.", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        note.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10));
        activity.recycle.addView(note);

        if (items == null) {
            TextView status = ToxiVisualSystem.text(activity, loading ? "Loading Recycle Bin…" : "Unable to load Recycle Bin. Try again.",
                    14, ToxiVisualSystem.TEXT_SECONDARY, false);
            status.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            activity.recycle.addView(status);
        } else if (items.isEmpty()) {
            TextView empty = ToxiVisualSystem.text(activity, "Recycle Bin is empty", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            empty.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            activity.recycle.addView(empty);
        } else {
            for (RecycleBin.Item item : items) {
                Entry entry = new Entry(item.file);
                View row = ToxiFileRow.create(activity);
                ToxiFileRow.Holder holder = (ToxiFileRow.Holder) row.getTag();
                ToxiFileRow.bind(holder, entry, false, false, "from " + item.originalParent.getName());
                row.setOnClickListener(view -> showItemMenu(item));
                holder.more.setOnClickListener(view -> showItemMenu(item));
                activity.recycle.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void showItemMenu(final RecycleBin.Item item) {
        String[] options = {"Restore", "Delete forever"};
        new AlertDialog.Builder(activity).setTitle(item.file.getName()).setItems(options, (dialog, which) -> {
            if (which == 0) {
                RecycleBin.restore(activity, item, message -> {
                    ToxiVisualSystem.toast(activity, message);
                    activity.afterOps();
                    reload();
                });
            } else {
                new AlertDialog.Builder(activity).setTitle("Delete forever?").setMessage("This can't be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (confirmDialog, confirmWhich) ->
                                RecycleBin.permanentDelete(activity, java.util.Collections.singletonList(item), message -> {
                                    ToxiVisualSystem.toast(activity, message);
                                    reload();
                                })).show();
            }
        }).show();
    }

    void showRetentionDialog() {
        final int[] days = {7, 14, 30, 60, 90};
        String[] labels = new String[days.length];
        for (int i = 0; i < days.length; i++) labels[i] = days[i] + " days";
        new AlertDialog.Builder(activity).setTitle("Recycle Bin retention")
                .setItems(labels, (dialog, which) -> {
                    ToxiVisualSystem.PREFERENCES.edit().putInt("recyclebin_days", days[which]).apply();
                    activity.buildSettings();
                }).show();
    }
}
