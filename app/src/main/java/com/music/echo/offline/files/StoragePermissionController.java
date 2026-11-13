package com.music.echo.offline.files;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.activity.ComponentActivity;

/** Owns Android storage permission checks and the platform-specific access prompt. */
final class StoragePermissionController {
    private static final int REQUEST_LEGACY_STORAGE = 77;
    private final ComponentActivity activity;

    StoragePermissionController(ComponentActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23) {
            boolean read = activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            boolean write = Build.VERSION.SDK_INT > 28 || activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            return read && write;
        }
        return true;
    }

    void requestLegacyPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_LEGACY_STORAGE);
        }
    }

    void showAccessPrompt() {
        if (Build.VERSION.SDK_INT < 30) {
            requestLegacyPermissions();
            return;
        }
        ToxiVisualSystem.dialogBuilder(activity).setTitle("Allow access to all files")
                .setMessage("A file manager needs \"All files access\" to show, open, copy, move and delete your files. Files never leave your device.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", (dialog, which) -> openStorageSettings())
                .show();
    }

    private void openStorageSettings() {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception appSpecificSettingsUnavailable) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception generalSettingsUnavailable) {
                ToxiVisualSystem.toast(activity, "Open Settings > Apps > Special access > All files access");
            }
        }
    }
}
