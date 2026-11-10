package com.music.echo.offline.files;

import android.content.Intent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Owns file-kind routing and dispatch to the existing built-in and platform viewers. */
final class FileOpenCoordinator {
    private final MainActivity activity;

    FileOpenCoordinator(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void open(Entry e, List<Entry> ctx) {
        if (e == null || e.f == null) {
            ToxiVisualSystem.toast(activity, "This file is no longer available");
            return;
        }
        if (e.dir || e.f.isDirectory()) { activity.openDir(e.f); return; }
        File file = e.f;
        if (!file.isFile() || !file.canRead()) {
            ToxiVisualSystem.toast(activity, "This file is missing or cannot be read");
            return;
        }

        // Built-in document viewers are mandatory for supported document extensions.
        // Do not let the optional media in-app preference divert PDF/HTML to an external chooser.
        String extension = FileKinds.extensionOf(file);
        if (FileKinds.isHtmlExtension(extension)) {
            Intent html = new Intent(activity, HtmlViewerActivity.class);
            html.putExtra(HtmlViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            activity.startActivity(html);
            return;
        }

        int kind = FileKinds.kindOfFile(file);
        if (kind == FileKinds.PDF) {
            openBuiltInViewer(file, 0);
            return;
        }

        boolean inApp = ToxiVisualSystem.booleanPreference("inapp", true);
        if (inApp && (kind == FileKinds.IMAGE || kind == FileKinds.VIDEO
                || kind == FileKinds.AUDIO || kind == FileKinds.TEXT)) {
            ArrayList<String> ps = new ArrayList<>();
            int idx = 0;
            if (kind == FileKinds.IMAGE || kind == FileKinds.VIDEO || kind == FileKinds.AUDIO) {
                if (ctx != null) for (Entry x : ctx) {
                    if (x != null && !x.dir && FileKinds.kindOfFile(x.f) == kind && x.f.isFile()) {
                        if (x.f.equals(file)) idx = ps.size();
                        ps.add(x.f.getAbsolutePath());
                    }
                }
                if (ps.isEmpty()) ps.add(file.getAbsolutePath());
                if (ps.size() > 400) {
                    int from = Math.max(0, Math.min(idx - 200, ps.size() - 400));
                    ps = new ArrayList<>(ps.subList(from, from + 400));
                    idx -= from;
                }
            } else {
                ps.add(file.getAbsolutePath());
            }
            Intent i = new Intent(activity, ViewerActivity.class);
            i.putStringArrayListExtra("paths", ps);
            i.putExtra("index", idx);
            activity.startActivity(i);
            return;
        }
        if (kind == FileKinds.ARCHIVE && "zip".equals(extension)) { showZip(file); return; }
        if (kind == FileKinds.APK) { FileOps.install(activity, file, activity.io); return; }
        // Unsupported native formats are handed to Android's app chooser on the first tap.
        FileOps.external(activity, file, true);
    }

    private void openBuiltInViewer(File file, int index) {
        ArrayList<String> paths = new ArrayList<>();
        paths.add(file.getAbsolutePath());
        Intent intent = new Intent(activity, ViewerActivity.class);
        intent.putStringArrayListExtra("paths", paths);
        intent.putExtra("index", index);
        activity.startActivity(intent);
    }

    void showZip(final File z) {
        Intent tools = new Intent(activity, ToxiToolsActivity.class);
        tools.putExtra(ToxiToolsActivity.EXTRA_FILE_PATH, z.getAbsolutePath());
        activity.startActivity(tools);
    }
}
