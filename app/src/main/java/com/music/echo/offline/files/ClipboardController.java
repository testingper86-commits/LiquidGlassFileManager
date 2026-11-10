package com.music.echo.offline.files;

import java.io.File;

/** Owns the single in-memory copy/cut selection shared by file-browser views. */
final class ClipboardController {
    private File[] files;
    private boolean cut;

    void set(File[] files, boolean cut) {
        this.files = files == null || files.length == 0 ? null : files.clone();
        this.cut = this.files != null && cut;
    }

    boolean hasItems() { return files != null && files.length > 0; }

    int size() { return hasItems() ? files.length : 0; }

    File[] snapshot() { return hasItems() ? files.clone() : new File[0]; }

    boolean isCut() { return hasItems() && cut; }

    void clear() {
        files = null;
        cut = false;
    }
}
