package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.os.Environment;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.File;
import java.util.List;

/** Owns Wi-Fi transfer prompts and lifecycle-safe progress presentation. */
final class LanTransferScreenController {
    private final MainActivity activity;

    LanTransferScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void receive() {
        final AlertDialog waitDialog = new AlertDialog.Builder(activity).setTitle("Waiting for sender…")
                .setMessage("Starting…")
                .setNegativeButton("Cancel", (dialog, which) -> LanTransfer.cancel())
                .setCancelable(false).show();
        File destination = new File(Environment.getExternalStorageDirectory(), "Download/LanReceived");
        LanTransfer.receive(destination, new LanTransfer.Listener() {
            @Override public void onWaiting(String ip, int port, String pairingCode) {
                activity.runUiIfAlive(() -> waitDialog.setMessage("On the sending device, open the file action menu, choose Send via Wi-Fi, then enter:\n\n"
                        + ip + ":" + port + "\nPairing code: " + pairingCode
                        + "\n\nKeep this code private. Files are encrypted during transfer."));
            }

            @Override public void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject) {
                activity.runUiIfAlive(() -> {
                    if (waitDialog.isShowing()) waitDialog.dismiss();
                    StringBuilder preview = new StringBuilder();
                    int shown = Math.min(8, names.size());
                    for (int i = 0; i < shown; i++) preview.append("\n• ").append(names.get(i));
                    if (names.size() > shown) preview.append("\n…and ").append(names.size() - shown).append(" more");
                    new AlertDialog.Builder(activity).setTitle("Incoming transfer")
                            .setMessage("From " + fromIp + "\n" + count + " file" + (count == 1 ? "" : "s")
                                    + ", " + ToxiVisualSystem.formatSize(totalBytes) + "\n\nFiles:" + preview)
                            .setCancelable(false)
                            .setNegativeButton("Decline", (dialog, which) -> reject.run())
                            .setPositiveButton("Accept", (dialog, which) -> {
                                accept.run();
                                activity.busy(true);
                                activity.progress("Receiving…");
                            }).show();
                });
            }

            @Override public void onFileStart(int index, int total, String name) {
                activity.runUiIfAlive(() -> activity.progress("Receiving " + index + " of " + total + ": " + name));
            }

            @Override public void onDone(String message) {
                activity.runUiIfAlive(() -> {
                    activity.busy(false);
                    ToxiVisualSystem.toast(activity, message);
                    activity.afterOps();
                });
            }

            @Override public void onError(String message) {
                activity.runUiIfAlive(() -> {
                    activity.busy(false);
                    if (waitDialog.isShowing()) waitDialog.dismiss();
                    ToxiVisualSystem.toast(activity, message);
                });
            }
        });
    }

    void send(final List<File> files) {
        if (files == null || files.isEmpty()) return;
        final EditText address = new EditText(activity);
        ToxiMotion.watchFocus(address);
        address.setHint("e.g. 192.168.1.42:52017");
        address.setSingleLine(true);
        final EditText pairingCode = new EditText(activity);
        ToxiMotion.watchFocus(pairingCode);
        pairingCode.setHint("10-character pairing code");
        pairingCode.setSingleLine(true);
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(activity.wrapField(address));
        fields.addView(activity.wrapField(pairingCode));
        new AlertDialog.Builder(activity).setTitle("Send via Wi-Fi")
                .setMessage("Enter the receiver address and one-time pairing code shown on the receiving device. Both devices must be on the same trusted Wi-Fi network.")
                .setView(fields).setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) -> {
                    String raw = address.getText().toString().trim();
                    String code = pairingCode.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
                    if (raw.isEmpty() || code.length() != 10) {
                        ToxiVisualSystem.toast(activity, "Enter the address and 10-character pairing code");
                        return;
                    }
                    String ip = raw.contains(":") ? raw.substring(0, raw.indexOf(':')) : raw;
                    activity.busy(true);
                    activity.progress("Connecting securely…");
                    LanTransfer.send(ip, code, files, new LanTransfer.Listener() {
                        @Override public void onWaiting(String ip, int port, String pairingCode) { }
                        @Override public void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject) { }
                        @Override public void onFileStart(int index, int total, String name) {
                            activity.runUiIfAlive(() -> activity.progress("Sending " + index + " of " + total + ": " + name));
                        }
                        @Override public void onDone(String message) {
                            activity.runUiIfAlive(() -> {
                                activity.busy(false);
                                ToxiVisualSystem.toast(activity, message);
                            });
                        }
                        @Override public void onError(String message) {
                            activity.runUiIfAlive(() -> {
                                activity.busy(false);
                                ToxiVisualSystem.toast(activity, message);
                            });
                        }
                    });
                }).show();
    }
}
