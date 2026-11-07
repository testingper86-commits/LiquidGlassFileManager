package com.music.echo.offline.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves an existing regular file strictly beneath a canonical directory. */
final class SafePathResolver {
    private SafePathResolver() { }

    static Path resolveExistingFile(Path rootDirectory, String relativePath) throws IOException {
        if (rootDirectory == null || relativePath == null || relativePath.isEmpty()
                || relativePath.indexOf((char) 0) >= 0 || relativePath.indexOf(92) >= 0) {
            throw new IOException("Invalid relative file path");
        }
        Path root = rootDirectory.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Asset root is not a directory");

        final Path relative;
        try {
            relative = Paths.get(relativePath);
        } catch (InvalidPathException invalid) {
            throw new IOException("Invalid relative file path", invalid);
        }
        if (relative.isAbsolute()) throw new IOException("Absolute asset paths are not allowed");
        for (Path segment : relative) {
            String value = segment.toString();
            if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
                throw new IOException("Path traversal is not allowed");
            }
        }

        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new IOException("Asset path escapes its root directory");
        }
        Path canonical = candidate.toRealPath();
        if (!canonical.startsWith(root) || canonical.equals(root)
                || !Files.isRegularFile(canonical) || !Files.isReadable(canonical)) {
            throw new IOException("Asset is not a readable regular file beneath its root");
        }
        return canonical;
    }
}
