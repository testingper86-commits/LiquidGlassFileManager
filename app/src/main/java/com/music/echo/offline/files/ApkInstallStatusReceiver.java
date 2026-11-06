package com.music.echo.offline.files;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

/** Handles PackageInstaller completion and launches its required user-confirmation UI. */
public final class ApkInstallStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch (RuntimeException unavailable) {
                    Toast.makeText(context, "Android could not open the installation confirmation", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(context, "Android did not provide an installation confirmation", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "APK installed successfully", Toast.LENGTH_LONG).show();
        } else {
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String message = detail == null || detail.trim().isEmpty()
                    ? "APK installation failed or was cancelled"
                    : "APK installation failed: " + detail;
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
