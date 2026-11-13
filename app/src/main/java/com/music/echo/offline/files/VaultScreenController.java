package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Owns Vault PIN prompts, encrypted item presentation and user-directed item actions. */
final class VaultScreenController {
    private final MainActivity activity;

    VaultScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void showSetPinDialog() {
        final android.widget.EditText input = activity.pinField();
        ToxiVisualSystem.dialogBuilder(activity).setTitle(Vault.hasPin(activity) ? "Change Vault PIN" : "Set Vault PIN")
                .setView(activity.wrapField(input)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.length() < 4) {
                        ToxiVisualSystem.toast(activity, "Use at least 4 digits");
                        return;
                    }
                    Vault.setPin(activity, pin);
                    ToxiVisualSystem.toast(activity, "Vault PIN saved");
                    activity.buildSettings();
                }).show();
    }

    void promptUnlock() {
        if (!Vault.hasPin(activity)) {
            ToxiVisualSystem.toast(activity, "Set a Vault PIN first");
            return;
        }
        final android.widget.EditText input = activity.pinField();
        ToxiVisualSystem.dialogBuilder(activity).setTitle("Enter Vault PIN")
                .setView(activity.wrapField(input)).setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (dialog, which) -> {
                    if (Vault.checkPin(activity, input.getText().toString().trim())) {
                        build();
                        activity.showOverlay(activity.vaultScroll);
                    } else {
                        long remaining = Vault.sp(activity).getLong("blocked_until", 0L) - System.currentTimeMillis();
                        ToxiVisualSystem.toast(activity, remaining > 0
                                ? "Too many attempts. Try again in " + Math.max(1, remaining / 1000) + " seconds"
                                : "Wrong PIN");
                    }
                }).show();
    }

    void build() {
        activity.vault.removeAllViews();
        activity.vault.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setContentDescription("Back");
        back.setOnClickListener(view -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48))));
        LinearLayout titleBlock = new LinearLayout(activity);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0, 0, 0);
        titleBlock.addView(ToxiVisualSystem.text(activity, "PRIVATE SPACE", 9, ToxiVisualSystem.ACCENT, true));
        titleBlock.addView(ToxiVisualSystem.text(activity, "Vault", 25, ToxiVisualSystem.TEXT_PRIMARY, true));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1f));
        activity.vault.addView(header, new LinearLayout.LayoutParams(-1, -2));

        List<Vault.Item> items = Vault.list(activity);
        long totalBytes = 0L;
        for (Vault.Item item : items) if (item.file.isFile()) totalBytes += Math.max(0L, item.file.length());

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)));
        hero.setBackground(ToxiVisualSystem.accentSurface(26));
        TextView emblem = ToxiVisualSystem.text(activity, "◈", 34, ToxiVisualSystem.accentForeground(), true);
        emblem.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.NORMAL));
        hero.addView(emblem);
        hero.addView(ToxiVisualSystem.text(activity, "Your files. Your boundary.", 19, 0xFFFFFFFF, true), params(0, 8, 0, 5));
        hero.addView(ToxiVisualSystem.text(activity,
                "Protected file contents use AES-256 encryption with a key held by Android Keystore.",
                12, ToxiVisualSystem.accentForeground(239), false));
        LinearLayout metrics = new LinearLayout(activity);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(17)), 0, 0);
        metrics.addView(metric(items.size() + "", "SECURED ITEMS"), new LinearLayout.LayoutParams(0, -2, 1f));
        metrics.addView(metric(ToxiVisualSystem.formatSize(totalBytes), "SPACE USED"), new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(metrics);
        activity.vault.addView(hero, params(0, 8, 0, 18));

        sectionLabel("ENCRYPTED CONTENT");
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(activity);
            empty.setGravity(Gravity.CENTER);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)));
            empty.setBackground(ToxiVisualSystem.surface(22, false));
            empty.addView(ToxiVisualSystem.text(activity, "◌", 32, ToxiVisualSystem.ACCENT, true));
            empty.addView(ToxiVisualSystem.text(activity, "Nothing stored yet", 16, ToxiVisualSystem.TEXT_PRIMARY, true), params(0, 10, 0, 5));
            empty.addView(ToxiVisualSystem.text(activity,
                    "Send a file to Vault from its file actions to keep it encrypted and out of ordinary browsing.",
                    12, ToxiVisualSystem.TEXT_SECONDARY, false));
            activity.vault.addView(empty, params(0, 0, 0, 12));
        } else {
            for (Vault.Item item : items) {
                Entry entry = new Entry(item.file);
                View row = ToxiFileRow.create(activity);
                ToxiFileRow.Holder holder = (ToxiFileRow.Holder) row.getTag();
                ToxiFileRow.bind(holder, entry, false, false, "SECURED  ·  from " + item.originalParent.getName());
                row.setOnClickListener(view -> showItemMenu(item));
                holder.more.setOnClickListener(view -> showItemMenu(item));
                activity.vault.addView(row, params(0, 0, 0, 5));
            }
        }

        TextView securityNote = ToxiVisualSystem.text(activity,
                "The PIN unlocks this screen. It is not used to derive the encryption key. Keep your device credentials private.",
                11, ToxiVisualSystem.TEXT_SECONDARY, false);
        securityNote.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0);
        activity.vault.addView(securityNote);
        ToxiVisualSystem.revealChildren(activity.vault);
    }

    private View metric(String value, String label) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(ToxiVisualSystem.text(activity, value, 19, 0xFFFFFFFF, true));
        TextView caption = ToxiVisualSystem.text(activity, label, 8, ToxiVisualSystem.accentForeground(223), true);
        caption.setLetterSpacing(0.12f);
        caption.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0);
        block.addView(caption);
        return block;
    }

    private void sectionLabel(String value) {
        TextView label = ToxiVisualSystem.text(activity, value, 9, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.16f);
        activity.vault.addView(label, params(4, 0, 4, 10));
    }

    private LinearLayout.LayoutParams params(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(ToxiVisualSystem.dp(left), ToxiVisualSystem.dp(top), ToxiVisualSystem.dp(right), ToxiVisualSystem.dp(bottom));
        return params;
    }

    void showItemMenu(final Vault.Item item) {
        String[] options = {"Move out of Vault", "Delete forever"};
        ToxiVisualSystem.dialogBuilder(activity).setTitle(item.file.getName()).setItems(options, (dialog, which) -> {
            if (which == 0) {
                Vault.restoreOut(activity, item, message -> {
                    ToxiVisualSystem.toast(activity, message);
                    activity.afterOps();
                    build();
                });
            } else {
                ToxiVisualSystem.dialogBuilder(activity).setTitle("Delete forever?").setMessage("This can't be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (confirmDialog, confirmWhich) ->
                                Vault.deleteForever(activity, item, message -> {
                                    ToxiVisualSystem.toast(activity, message);
                                    build();
                                })).show();
            }
        }).show();
    }
}
