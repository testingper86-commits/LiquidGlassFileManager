package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;

/**
 * Optional whole-app PIN/biometric gate. Re-armed every time the app leaves the
 * foreground (see MainActivity.onPause), so backgrounding the app always re-locks it.
 */
final class AppLock {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("lg_applock", Context.MODE_PRIVATE); }
    static boolean hasPin(Context c) { return sp(c).contains("pinhash"); }
    static boolean enabled(Context c) { return Ui.bool("applock_on", false) && hasPin(c); }

    static void setPin(Context c, String pin) {
        sp(c).edit().putString("pinhash", PinHash.create(pin)).putInt("failed_attempts", 0).putLong("blocked_until", 0L).apply();
    }

    static boolean isLockedOut(Context c) { return System.currentTimeMillis() < sp(c).getLong("blocked_until", 0L); }

    static long lockoutRemainingMs(Context c) { return Math.max(0L, sp(c).getLong("blocked_until", 0L) - System.currentTimeMillis()); }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences prefs = sp(c);
        long now = System.currentTimeMillis();
        if (now < prefs.getLong("blocked_until", 0L)) return false;
        String stored = prefs.getString("pinhash", "");
        if (!hasPin(c) || !PinHash.verify(pin, stored, "lgapplock:")) return recordFailure(c, prefs, now);
        if (!stored.startsWith("pbkdf2$")) prefs.edit().putString("pinhash", PinHash.create(pin)).apply();
        prefs.edit().putInt("failed_attempts", 0).putLong("blocked_until", 0L).apply();
        return true;
    }

    private static boolean recordFailure(Context c, SharedPreferences prefs, long now) {
        int failures = prefs.getInt("failed_attempts", 0) + 1;
        long delay = failures < 5 ? 0L : Math.min(15 * 60_000L, (long) Math.pow(2, Math.min(10, failures - 4)) * 1_000L);
        prefs.edit().putInt("failed_attempts", failures).putLong("blocked_until", now + delay).apply();
        return false;
    }

    static void clearPin(Context c) { sp(c).edit().clear().apply(); }
}
