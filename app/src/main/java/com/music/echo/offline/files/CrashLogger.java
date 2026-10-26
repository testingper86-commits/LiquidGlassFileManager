package com.music.echo.offline.files;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Captures uncaught Java crashes locally so the user can share diagnostics after restarting. */
public final class CrashLogger {
    private static final String DIR = "crash-logs";
    private static final String LATEST = "latest_crash.txt";
    private static volatile boolean installed;

    private CrashLogger() {}

    public static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { writeCrash(app, thread, error); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    private static void writeCrash(Context context, Thread thread, Throwable error) throws Exception {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) return;
        long now = System.currentTimeMillis();
        StringBuilder report = new StringBuilder(4096);
        report.append("LiquidFiles crash report\n")
                .append("crash_time_ms=").append(now).append('\n')
                .append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(new Date(now))).append('\n')
                .append("app_version=").append(appVersion(context)).append('\n')
                .append("package=").append(context.getPackageName()).append('\n')
                .append("android_version=").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                .append("thread=").append(thread == null ? "unknown" : thread.getName()).append("\n\n")
                .append("FATAL EXCEPTION\n");
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        report.append(sw);
        File latest = new File(dir, LATEST);
        try (FileOutputStream out = new FileOutputStream(latest, false)) {
            out.write(report.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        // Keep a small rolling history for repeated crashes without consuming much storage.
        File archived = new File(dir, "crash-" + now + ".txt");
        try (FileOutputStream out = new FileOutputStream(archived, false)) {
            out.write(report.toString().getBytes(StandardCharsets.UTF_8));
        }
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash-") && name.endsWith(".txt"));
        if (files != null && files.length > 5) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 5; i < files.length; i++) try { files[i].delete(); } catch (Throwable ignored) { }
        }
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (Exception ignored) { return "unknown"; }
    }

    public static File latest(Context context) {
        return new File(new File(context.getFilesDir(), DIR), LATEST);
    }

    public static boolean hasReport(Context context) {
        File file = latest(context);
        return file.isFile() && file.length() > 0;
    }

    public static long reportTime(Context context) {
        File file = latest(context);
        return file.isFile() ? file.lastModified() : 0L;
    }

    public static long promptedTime(Context context) {
        return context.getSharedPreferences("crash_logger", Context.MODE_PRIVATE).getLong("prompted_time", 0L);
    }

    public static void markPrompted(Context context, long time) {
        context.getSharedPreferences("crash_logger", Context.MODE_PRIVATE).edit().putLong("prompted_time", time).apply();
    }
}
