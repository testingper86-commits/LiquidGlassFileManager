package com.music.echo.offline.files;

import android.app.AlertDialog;
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
        new AlertDialog.Builder(activity).setTitle(Vault.hasPin(activity) ? "Change Vault PIN" : "Set Vault PIN")
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
        new AlertDialog.Builder(activity).setTitle("Enter Vault PIN")
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
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setContentDescription("Back");
        back.setOnClickListener(view -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        header.addView(ToxiVisualSystem.text(activity, "Vault", 24, ToxiVisualSystem.TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(new View(activity), new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        header.setMinimumHeight(ToxiVisualSystem.dp(56));
        activity.vault.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView note = ToxiVisualSystem.text(activity, "Encrypted with AES-256 (key stored in Android's hardware Keystore, not derived from your PIN). The PIN gates this screen; the encryption protects the file bytes even if someone pulls them off the device directly.",
                12, ToxiVisualSystem.TEXT_SECONDARY, false);
        note.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10));
        activity.vault.addView(note);

        List<Vault.Item> items = Vault.list(activity);
        if (items.isEmpty()) {
            TextView empty = ToxiVisualSystem.text(activity, "Vault is empty", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            empty.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            activity.vault.addView(empty);
        } else {
            for (Vault.Item item : items) {
                Entry entry = new Entry(item.file);
                View row = ToxiFileRow.create(activity);
                ToxiFileRow.Holder holder = (ToxiFileRow.Holder) row.getTag();
                ToxiFileRow.bind(holder, entry, false, false, "from " + item.originalParent.getName());
                row.setOnClickListener(view -> showItemMenu(item));
                holder.more.setOnClickListener(view -> showItemMenu(item));
                activity.vault.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void showItemMenu(final Vault.Item item) {
        String[] options = {"Move out of Vault", "Delete forever"};
        new AlertDialog.Builder(activity).setTitle(item.file.getName()).setItems(options, (dialog, which) -> {
            if (which == 0) {
                Vault.restoreOut(activity, item, message -> {
                    ToxiVisualSystem.toast(activity, message);
                    activity.afterOps();
                    build();
                });
            } else {
                new AlertDialog.Builder(activity).setTitle("Delete forever?").setMessage("This can't be undone.")
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
