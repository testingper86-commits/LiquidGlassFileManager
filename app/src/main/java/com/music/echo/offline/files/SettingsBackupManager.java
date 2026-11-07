package com.music.echo.offline.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns the bounded, allow-listed portable settings backup format. */
final class SettingsBackupManager {
    private static final int MAX_IMPORT_BYTES = 1024 * 1024;
    private static final Set<String> PORTABLE_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "theme", "light", "haptic", "blur", "hidden", "sort", "grid",
            "font_family", "reduce_motion", "recyclebin_days", "dev_verbose",
            "dev_debug_toasts", "dev_logging")));

    private SettingsBackupManager() { }

    static File exportToDownloads(Context context, SharedPreferences preferences) throws Exception {
        if (context == null || preferences == null) throw new IllegalArgumentException("Settings context and preferences are required");
        JSONObject json = new JSONObject();
        Map<String, ?> values = preferences.getAll();
        for (String key : PORTABLE_KEYS) {
            Object value = values.get(key);
            if (value instanceof String || value instanceof Number || value instanceof Boolean) json.put(key, value);
        }

        File downloads = new File(Environment.getExternalStorageDirectory(), "Download");
        if (!downloads.isDirectory() && !downloads.mkdirs()) throw new java.io.IOException("Download folder is unavailable");
        File destination = FileOps.unique(downloads, "liquidglass_settings.json");
        File pending = new File(downloads, ".toxifiles-settings-" + UUID.randomUUID() + ".part");
        boolean published = false;
        try {
            try (OutputStream output = new FileOutputStream(pending)) {
                output.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            try {
                java.nio.file.Files.move(pending.toPath(), destination.toPath());
            } catch (java.io.IOException moveFailure) {
                try {
                    FileOps.copy(pending, destination);
                    if (!pending.delete()) pending.deleteOnExit();
                } catch (Exception copyFailure) {
                    copyFailure.addSuppressed(moveFailure);
                    if (destination.exists() && !destination.delete()) {
                        copyFailure.addSuppressed(new java.io.IOException("Could not remove incomplete settings export"));
                    }
                    throw copyFailure;
                }
            }
            published = true;
            return destination;
        } finally {
            if (!published && pending.exists() && !pending.delete()) pending.deleteOnExit();
        }
    }

    static void importFromUri(Context context, Uri uri, SharedPreferences preferences, int themeCount) throws Exception {
        if (context == null || uri == null || preferences == null || themeCount < 1) {
            throw new IllegalArgumentException("Settings source, preferences and theme definitions are required");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = ToxiUriPermissions.openInputStream(context, uri)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if ((long) bytes.size() + count > MAX_IMPORT_BYTES) {
                    throw new java.io.IOException("Settings file exceeds the 1 MB limit");
                }
                bytes.write(buffer, 0, count);
            }
        }
        final String encoded;
        try {
            encoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException malformed) {
            throw new java.io.IOException("Settings file is not valid UTF-8", malformed);
        }
        JSONObject json = new JSONObject(encoded);
        SharedPreferences.Editor editor = preferences.edit();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!PORTABLE_KEYS.contains(key)) continue;
            Object value = json.get(key);
            if ("theme".equals(key) && value instanceof Number) {
                Integer number = exactInteger((Number) value);
                if (number != null && number >= 0 && number < themeCount) editor.putInt(key, number);
            } else if ("font_family".equals(key) && value instanceof Number) {
                Integer number = exactInteger((Number) value);
                if (number != null && number >= 0 && number <= 3) editor.putInt(key, number);
            } else if ("recyclebin_days".equals(key) && value instanceof Number) {
                Integer number = exactInteger((Number) value);
                if (number != null && (number == 7 || number == 14 || number == 30 || number == 60 || number == 90)) editor.putInt(key, number);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof String && "sort".equals(key)) {
                String sort = (String) value;
                if (sort.length() <= 32) editor.putString(key, sort);
            }
        }
        if (!editor.commit()) throw new java.io.IOException("Settings could not be saved to local storage");
    }

    private static Integer exactInteger(Number value) {
        double number = value.doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) return null;
        return (int) number;
    }
}
