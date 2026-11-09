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
        })) render(null, false);
    }

    private void render(List<RecycleBin.Item> items, boolean loading) {
        activity.recycle.removeAllViews();
        activity.recycle.setPadding(ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(4),
                ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(28));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(8));
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setOnClickListener(view -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        LinearLayout titleBlock = new LinearLayout(activity);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(ToxiVisualSystem.dp(12), 0, 0, 0);
        titleBlock.addView(ToxiVisualSystem.text(activity, "RECOVERY SYSTEM", 9, ToxiVisualSystem.ACCENT, true));
        titleBlock.addView(ToxiVisualSystem.text(activity, "Recycle Bin", 25, ToxiVisualSystem.TEXT_PRIMARY, true));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView emptyButton = ToxiVisualSystem.text(activity, "Empty", 12, ToxiVisualSystem.ACCENT, true);
        emptyButton.setBackground(ToxiVisualSystem.surface(14, true));
        emptyButton.setPadding(ToxiVisualSystem.dp(15), ToxiVisualSystem.dp(11), ToxiVisualSystem.dp(15), ToxiVisualSystem.dp(11));
        emptyButton.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("Empty Recycle Bin?")
                .setMessage("Every item will be permanently deleted. This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Empty", (dialog, which) -> RecycleBin.empty(activity, message -> {
                    ToxiVisualSystem.toast(activity, message);
                    reload();
                })).show());
        ToxiVisualSystem.interactive(emptyButton);
        header.addView(emptyButton, new LinearLayout.LayoutParams(-2, -2));
        activity.recycle.addView(header, new LinearLayout.LayoutParams(-1, -2));

        int retentionDays = ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30);
        long totalBytes = 0L;
        if (items != null) for (RecycleBin.Item item : items) if (item.file.isFile()) totalBytes += Math.max(0L, item.file.length());

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(19), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(18));
        hero.setBackground(ToxiVisualSystem.surface(24, true));
        TextView overline = ToxiVisualSystem.text(activity, "SAFE DELETION LAYER", 9, ToxiVisualSystem.ACCENT, true);
        overline.setLetterSpacing(0.15f);
        hero.addView(overline);
        hero.addView(ToxiVisualSystem.text(activity, "A second chance for deleted files.", 18,
                ToxiVisualSystem.TEXT_PRIMARY, true), params(0, 8, 0, 5));
        hero.addView(ToxiVisualSystem.text(activity,
                "Restore an item while it is retained, or remove it permanently when you are certain.",
                12, ToxiVisualSystem.TEXT_SECONDARY, false));
        LinearLayout metrics = new LinearLayout(activity);
        metrics.setPadding(0, ToxiVisualSystem.dp(16), 0, 0);
        metrics.addView(metric(items == null ? "—" : String.valueOf(items.size()), "ITEMS HELD"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        metrics.addView(metric(items == null ? "—" : ToxiVisualSystem.formatSize(totalBytes), "SPACE HELD"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        metrics.addView(metric(retentionDays + "d", "RETENTION"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(metrics);
        activity.recycle.addView(hero, params(0, 8, 0, 18));

        TextView section = ToxiVisualSystem.text(activity, "DELETED ITEMS", 9, ToxiVisualSystem.TEXT_SECONDARY, true);
        section.setLetterSpacing(0.16f);
        activity.recycle.addView(section, params(4, 0, 4, 10));

        if (items == null) {
            LinearLayout status = stateCard(loading ? "Loading deleted items…" : "Could not load Recycle Bin",
                    loading ? "Your recovery index is being read." : "Check storage access and retry.");
            if (!loading) {
                TextView retry = ToxiVisualSystem.text(activity, "RETRY", 10, ToxiVisualSystem.ACCENT, true);
                retry.setPadding(0, ToxiVisualSystem.dp(12), 0, 0);
                retry.setOnClickListener(view -> reload());
                status.addView(retry);
            }
            activity.recycle.addView(status, params(0, 0, 0, 10));
        } else if (items.isEmpty()) {
            activity.recycle.addView(stateCard("Recycle Bin is clear",
                    "Deleted items that support recovery will appear here. Nothing is waiting to be restored."),
                    params(0, 0, 0, 10));
        } else {
            for (RecycleBin.Item item : items) {
                Entry entry = new Entry(item.file);
                View row = ToxiFileRow.create(activity);
                ToxiFileRow.Holder holder = (ToxiFileRow.Holder) row.getTag();
                ToxiFileRow.bind(holder, entry, false, false, "DELETED  ·  from " + item.originalParent.getName());
                row.setOnClickListener(view -> showItemMenu(item));
                holder.more.setOnClickListener(view -> showItemMenu(item));
                activity.recycle.addView(row, params(0, 0, 0, 5));
            }
        }
        ToxiVisualSystem.revealChildren(activity.recycle);
    }

    private LinearLayout stateCard(String title, String description) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ToxiVisualSystem.dp(19), ToxiVisualSystem.dp(21), ToxiVisualSystem.dp(19), ToxiVisualSystem.dp(20));
        card.setBackground(ToxiVisualSystem.surface(21, false));
        card.addView(ToxiVisualSystem.text(activity, title, 15, ToxiVisualSystem.TEXT_PRIMARY, true));
        card.addView(ToxiVisualSystem.text(activity, description, 12, ToxiVisualSystem.TEXT_SECONDARY, false), params(0, 6, 0, 0));
        return card;
    }

    private View metric(String value, String label) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(ToxiVisualSystem.text(activity, value, 17, ToxiVisualSystem.TEXT_PRIMARY, true));
        TextView caption = ToxiVisualSystem.text(activity, label, 8, ToxiVisualSystem.TEXT_SECONDARY, true);
        caption.setLetterSpacing(0.09f);
        caption.setPadding(0, ToxiVisualSystem.dp(3), 0, 0);
        block.addView(caption);
        return block;
    }

    private LinearLayout.LayoutParams params(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(ToxiVisualSystem.dp(left), ToxiVisualSystem.dp(top), ToxiVisualSystem.dp(right), ToxiVisualSystem.dp(bottom));
        return params;
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
