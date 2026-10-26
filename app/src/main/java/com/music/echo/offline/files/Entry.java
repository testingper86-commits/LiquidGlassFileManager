package com.music.echo.offline.files;

import com.music.echo.offline.R;

import java.io.File;
import java.util.Locale;

/** Immutable snapshot of a file's metadata, built off the UI thread so scrolling never touches the disk. */
final class Entry {
    final File f;
    final boolean dir;
    final long mod, len;
    final String name, lname, ext;
    final int kind;

    Entry(File f) { this(f, f.isDirectory()); }

    private Entry(File f, boolean d) { this(f, d, f.lastModified(), d ? 0 : f.length()); }

    Entry(File f, boolean d, long mod, long len) {
        this.f = f;
        this.dir = d;
        this.mod = mod;
        this.len = len;
        this.name = f.getName();
        this.lname = name.toLowerCase(Locale.ROOT);
        int i = name.lastIndexOf('.');
        this.ext = (i > 0 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
        this.kind = d ? FileKinds.DIR : FileKinds.kindOfExt(ext);
    }
}
