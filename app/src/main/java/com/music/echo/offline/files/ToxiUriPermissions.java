package com.music.echo.offline.files;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

/**
 * Persists only the URI permission modes actually granted by the Storage Access Framework.
 * Keep the platform calls here so callers cannot accidentally pass unrelated Intent flags.
 */
final class ToxiUriPermissions {
    private ToxiUriPermissions() {
    }

    static void takeGranted(ContentResolver resolver, Uri uri, int grantedFlags) {
        if (resolver == null || uri == null) {
            return;
        }
        if ((grantedFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some document providers grant transient access only.
            }
        }
        if ((grantedFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Read and write persistence are independent provider capabilities.
            }
        }
    }
}
