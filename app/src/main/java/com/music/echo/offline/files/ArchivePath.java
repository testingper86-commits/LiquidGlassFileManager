package com.music.echo.offline.files;

import java.io.IOException;

/** Strict ZIP entry path validation, independent of host path-separator semantics. */
final class ArchivePath {
    private ArchivePath() {}

    static String normalize(String name, boolean directory) throws IOException {
        if (name == null || name.isEmpty()) throw new IOException("Empty archive entry path");
        String path = name.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) throw new IOException("Absolute archive entry path rejected");
        if (directory && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty()) throw new IOException("Empty archive entry path");
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment))
                throw new IOException("Unsafe archive entry path rejected");
        }
        return path;
    }
}
