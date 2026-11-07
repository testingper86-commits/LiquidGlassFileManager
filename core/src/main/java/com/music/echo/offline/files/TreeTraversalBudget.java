package com.music.echo.offline.files;

import java.io.IOException;

/** Bounds recursive file/document tree work against excessive depth and provider cycles. */
final class TreeTraversalBudget {
    static final int DEFAULT_MAX_ENTRIES = 100_000;
    static final int DEFAULT_MAX_DEPTH = 256;

    private final int maxEntries;
    private final int maxDepth;
    private int visitedEntries;

    TreeTraversalBudget() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_DEPTH);
    }

    TreeTraversalBudget(int maxEntries, int maxDepth) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must not be negative");
        this.maxEntries = maxEntries;
        this.maxDepth = maxDepth;
    }

    void enter(int depth) throws IOException {
        if (depth < 0) throw new IllegalArgumentException("depth must not be negative");
        if (depth > maxDepth) {
            throw new IOException("Directory nesting exceeds the supported limit of " + maxDepth);
        }
        if (visitedEntries >= maxEntries) {
            throw new IOException("Tree contains more than the supported limit of " + maxEntries + " entries");
        }
        visitedEntries++;
    }

    int visitedEntries() {
        return visitedEntries;
    }
}
