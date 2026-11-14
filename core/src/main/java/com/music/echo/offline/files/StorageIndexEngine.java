package com.music.echo.offline.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Bounded, symlink-safe filesystem traversal shared by storage indexing features. */
final class StorageIndexEngine {
    static final int MAX_INDEX_ENTRIES = 150_000;
    static final int MAX_DEPTH = 9;

    interface EntryConsumer { void accept(Snapshot snapshot); }

    static final class Snapshot {
        final File file;
        final boolean directory;
        final long modifiedAt;
        final long length;
        Snapshot(File file, boolean directory, long modifiedAt, long length) {
            this.file = file; this.directory = directory; this.modifiedAt = modifiedAt; this.length = length;
        }
    }

    static final class Result {
        final int entriesVisited;
        final boolean truncated;
        final boolean incomplete;
        Result(int entriesVisited, boolean truncated, boolean incomplete) {
            this.entriesVisited = entriesVisited; this.truncated = truncated; this.incomplete = incomplete;
        }
    }

    static final class VolumeSnapshot {
        final String volumeId;
        final String label;
        final long totalBytes;
        final long availableBytes;
        final long usedBytes;
        final boolean primary;
        VolumeSnapshot(String volumeId, String label, long totalBytes, long availableBytes, boolean primary) {
            if (volumeId == null || volumeId.isEmpty() || totalBytes < 0 || availableBytes < 0 || availableBytes > totalBytes) {
                throw new IllegalArgumentException("Invalid storage volume statistics");
            }
            this.volumeId = volumeId;
            this.label = label == null ? "Storage" : label;
            this.totalBytes = totalBytes;
            this.availableBytes = availableBytes;
            this.usedBytes = totalBytes - availableBytes;
            this.primary = primary;
        }
    }

    static final class CategoryTotals {
        final long[] bytes;
        final int[] counts;
        final long totalIndexedBytes;
        final int totalIndexedFiles;
        final boolean complete;
        final int entriesVisited;
        CategoryTotals(long[] bytes, int[] counts, long totalIndexedBytes, int totalIndexedFiles,
                       boolean complete, int entriesVisited) {
            this.bytes = bytes.clone();
            this.counts = counts.clone();
            this.totalIndexedBytes = totalIndexedBytes;
            this.totalIndexedFiles = totalIndexedFiles;
            this.complete = complete;
            this.entriesVisited = entriesVisited;
        }
    }

    static final class TotalsBuilder {
        private final long[] bytes = new long[7];
        private final int[] counts = new int[7];
        private long totalBytes;
        private int totalFiles;
        void add(int category, long size) {
            if (category < 0 || category >= bytes.length || size < 0) throw new IllegalArgumentException("Invalid file category");
            bytes[category] = saturatedAdd(bytes[category], size);
            counts[category] = counts[category] == Integer.MAX_VALUE ? Integer.MAX_VALUE : counts[category] + 1;
            totalBytes = saturatedAdd(totalBytes, size);
            totalFiles = totalFiles == Integer.MAX_VALUE ? Integer.MAX_VALUE : totalFiles + 1;
        }
        CategoryTotals finish(Result scan) {
            return new CategoryTotals(bytes, counts, totalBytes, totalFiles,
                    scan != null && !scan.incomplete && !scan.truncated, scan == null ? 0 : scan.entriesVisited);
        }
    }

    static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static final class State {
        final int maxDepth;
        final int maxEntries;
        final EntryConsumer consumer;
        int entriesVisited;
        boolean truncated;
        boolean incomplete;
        State(int maxDepth, int maxEntries, EntryConsumer consumer) {
            this.maxDepth = maxDepth; this.maxEntries = maxEntries; this.consumer = consumer;
        }
    }

    private StorageIndexEngine() { }

    static Result scan(File root, EntryConsumer consumer) throws IOException {
        return scan(root, MAX_DEPTH, MAX_INDEX_ENTRIES, consumer);
    }

    static Result scan(File root, int maxDepth, int maxEntries, EntryConsumer consumer) throws IOException {
        return scan(root, maxDepth, maxEntries, false, false, consumer);
    }

    /**
     * Scan with explicit visibility rules. The file index excludes hidden entries and the root
     * Android directory; storage analysis opts in so its totals cover all accessible shared storage.
     */
    static Result scan(File root, int maxDepth, int maxEntries, boolean includeHiddenEntries,
                       boolean includeRootAndroidDirectory, EntryConsumer consumer) throws IOException {
        if (root == null || consumer == null) throw new IOException("Storage root and entry consumer are required");
        if (maxDepth < 0 || maxEntries < 1) throw new IllegalArgumentException("Storage scan limits are invalid");
        OperationCancellation.checkCurrent();
        State state = new State(maxDepth, maxEntries, consumer);
        final Path rootPath = root.toPath();
        try {
            if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) {
                state.incomplete = true;
                return result(state);
            }
        } catch (SecurityException denied) {
            state.incomplete = true;
            return result(state);
        }
        scanDirectory(rootPath, 0, true, includeHiddenEntries, includeRootAndroidDirectory, state);
        return result(state);
    }

    private static Result result(State state) {
        return new Result(state.entriesVisited, state.truncated, state.incomplete || state.truncated);
    }

    private static void scanDirectory(Path directory, int depth, boolean rootDirectory,
                                      boolean includeHiddenEntries, boolean includeRootAndroidDirectory,
                                      State state) throws IOException {
        OperationCancellation.checkCurrent();
        if (depth > state.maxDepth) { state.incomplete = true; return; }
        if (state.entriesVisited >= state.maxEntries) { state.truncated = true; state.incomplete = true; return; }

        final DirectoryStream<Path> opened;
        try { opened = Files.newDirectoryStream(directory); }
        catch (IOException | SecurityException unavailable) { state.incomplete = true; return; }

        try (DirectoryStream<Path> children = opened) {
            for (Path child : children) {
                OperationCancellation.checkCurrent();
                if (state.entriesVisited >= state.maxEntries) {
                    state.truncated = true; state.incomplete = true; return;
                }
                Path leaf = child.getFileName();
                if (leaf == null) { state.incomplete = true; continue; }
                String name = leaf.toString();
                if (!includeHiddenEntries && name.startsWith(".")) continue;

                final BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException | SecurityException changedOrDenied) {
                    state.incomplete = true;
                    continue;
                }
                if (attributes.isSymbolicLink()) continue;

                if (attributes.isDirectory()) {
                    if (rootDirectory && !includeRootAndroidDirectory && "Android".equals(name)) continue;
                    state.consumer.accept(new Snapshot(child.toFile(), true,
                            attributes.lastModifiedTime().toMillis(), 0L));
                    state.entriesVisited++;
                    scanDirectory(child, depth + 1, false, includeHiddenEntries, includeRootAndroidDirectory, state);
                    if (state.truncated) return;
                } else if (attributes.isRegularFile()) {
                    state.consumer.accept(new Snapshot(child.toFile(), false,
                            attributes.lastModifiedTime().toMillis(), attributes.size()));
                    state.entriesVisited++;
                } else {
                    state.incomplete = true;
                }
            }
        } catch (IOException failure) {
            if ("Operation cancelled".equals(failure.getMessage())) throw failure;
            state.incomplete = true;
        } catch (DirectoryIteratorException | SecurityException unavailable) {
            state.incomplete = true;
        }
    }
}
