package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Owns the App Lock PIN dialog and authenticated biometric unlock presentation. */
final class AppLockScreenController {
    private final MainActivity activity;

    AppLockScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void showSetPinDialog() {
        final EditText input = activity.pinField();
        ToxiVisualSystem.dialogBuilder(activity).setTitle(AppLock.hasPin(activity) ? "Change App Lock PIN" : "Set App Lock PIN")
                .setView(activity.wrapField(input)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.length() < 4) {
                        ToxiVisualSystem.toast(activity, "Use at least 4 digits");
                        return;
                    }
                    AppLock.setPin(activity, pin);
                    ToxiVisualSystem.toast(activity, "App Lock PIN saved");
                    activity.buildSettings();
                }).show();
    }

    /** Displays the non-cancelable lock surface while leaving PIN unlock available. */
    void showLockScreen() {
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCancelable(false);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(ToxiVisualSystem.dp(32), 0, ToxiVisualSystem.dp(32), 0);
        column.addView(ToxiVisualSystem.text(activity, "ToxiFiles is locked", 18, ToxiVisualSystem.TEXT_PRIMARY, true),
                activity.lpm(-2, -2, 0, 0, 0, 16));
        final EditText input = activity.pinField();
        column.addView(activity.wrapField(input), new LinearLayout.LayoutParams(-1, -2));
        TextView unlockButton = ToxiVisualSystem.text(activity, "Unlock", 16, ToxiVisualSystem.ACCENT, true);
        unlockButton.setGravity(Gravity.CENTER);
        unlockButton.setBackground(ToxiVisualSystem.surface(18, false));
        unlockButton.setPadding(0, ToxiVisualSystem.dp(14), 0, ToxiVisualSystem.dp(14));
        ToxiVisualSystem.interactive(unlockButton);
        unlockButton.setOnClickListener(view -> {
            if (AppLock.checkPin(activity, input.getText().toString().trim())) {
                MainActivity.unlocked = true;
                activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                dialog.dismiss();
            } else {
                long remaining = AppLock.lockoutRemainingMs(activity);
                ToxiVisualSystem.toast(activity, remaining > 0
                        ? "Too many attempts. Try again in " + Math.max(1, remaining / 1000) + " seconds"
                        : "Wrong PIN");
            }
        });
        column.addView(unlockButton, activity.lpm(-1, -2, 0, 20, 0, 0));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(root);
        dialog.setOnKeyListener((ignored, keyCode, event) -> keyCode == android.view.KeyEvent.KEYCODE_BACK);
        dialog.show();
        if (Build.VERSION.SDK_INT >= 28) tryBiometric(dialog);
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)
    private void tryBiometric(final Dialog dialog) {
        try {
            activity.executeIo(() -> {
                final javax.crypto.Cipher cipher;
                try {
                    cipher = BiometricUnlockKeyStore.createCipher();
                } catch (Exception keystoreFailure) {
                    android.util.Log.w("ToxiFiles", "Biometric Keystore unavailable; PIN remains available", keystoreFailure);
                    return;
                }
                activity.runUiIfAlive(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                    try {
                        android.hardware.biometrics.BiometricPrompt prompt = new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                                .setTitle("Unlock ToxiFiles")
                                .setNegativeButton("Use PIN", activity.getMainExecutor(), (ignored, which) -> { })
                                .build();
                        prompt.authenticate(new android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher),
                                new android.os.CancellationSignal(), activity.getMainExecutor(),
                                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                                    @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                                        if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                                        byte[] challenge = new byte[32];
                                        byte[] proof = null;
                                        try {
                                            android.hardware.biometrics.BiometricPrompt.CryptoObject authenticated = result.getCryptoObject();
                                            if (authenticated == null || authenticated.getCipher() == null) {
                                                throw new java.security.GeneralSecurityException("Biometric cryptographic proof is unavailable");
                                            }
                                            new java.security.SecureRandom().nextBytes(challenge);
                                            proof = authenticated.getCipher().doFinal(challenge);
                                            if (proof == null || proof.length <= challenge.length) {
                                                throw new java.security.GeneralSecurityException("Biometric cryptographic proof is invalid");
                                            }
                                            MainActivity.unlocked = true;
                                            activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                                            dialog.dismiss();
                                        } catch (Exception verificationFailure) {
                                            android.util.Log.w("ToxiFiles", "Biometric Keystore verification failed; PIN remains available", verificationFailure);
                                            ToxiVisualSystem.toast(activity, "Biometric verification failed. Use PIN.");
                                        } finally {
                                            java.util.Arrays.fill(challenge, (byte) 0);
                                            if (proof != null) java.util.Arrays.fill(proof, (byte) 0);
                                        }
                                    }
                                });
                    } catch (Exception promptFailure) {
                        android.util.Log.w("ToxiFiles", "Biometric prompt unavailable; PIN remains available", promptFailure);
                    }
                });
            });
        } catch (RuntimeException executorFailure) {
            android.util.Log.w("ToxiFiles", "Biometric setup unavailable; PIN remains available", executorFailure);
        }
    }
}
