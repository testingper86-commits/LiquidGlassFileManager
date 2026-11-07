package com.music.echo.offline.files;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Persists only the URI permission modes actually granted by the Storage Access Framework.
 * Keep the platform calls here so callers cannot accidentally pass unrelated Intent flags.
 */
final class ToxiUriPermissions {
    private ToxiUriPermissions() {
    }

    static boolean isSafUri(Context context, Uri uri) {
        if (context == null || uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                || uri.getAuthority() == null || uri.getAuthority().isEmpty()) return false;
        try {
            return DocumentsContract.isDocumentUri(context, uri) || DocumentsContract.isTreeUri(uri);
        } catch (RuntimeException invalidUri) {
            return false;
        }
    }

    static void requireSafUri(Context context, Uri uri) throws IOException {
        if (!isSafUri(context, uri)) throw new IOException("Only documents selected through Android's Storage Access Framework are supported");
    }

    /** Require the explicit URI grant returned by the system document picker for each operation. */
    private static void requireSafUri(Context context, Uri uri, int requiredPermission) throws IOException {
        requireSafUri(context, uri);
        if (context.checkUriPermission(uri, Process.myPid(), Process.myUid(), requiredPermission)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IOException("The selected document does not grant the required access permission");
        }
    }

    static InputStream openInputStream(Context context, Uri uri) throws IOException {
        requireSafUri(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Selected document provider returned no input stream");
        return input;
    }

    static OutputStream openOutputStream(Context context, Uri uri, String mode) throws IOException {
        requireSafUri(context, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        OutputStream output = context.getContentResolver().openOutputStream(uri, mode);
        if (output == null) throw new IOException("Selected document provider returned no output stream");
        return output;
    }

    static Cursor query(Context context, Uri uri, String[] projection) throws IOException {
        requireSafUri(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return context.getContentResolver().query(uri, projection, null, null, null);
    }

    static void takeGranted(ContentResolver resolver, Uri uri, int grantedFlags) {
        if (resolver == null || uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                || uri.getAuthority() == null || uri.getAuthority().isEmpty()) {
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
