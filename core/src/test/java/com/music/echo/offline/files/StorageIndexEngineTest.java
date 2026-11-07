package com.music.echo.offline.files;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class StorageIndexEngineTest {
    @Test public void scansNestedFilesAndDirectoriesWithoutFollowingLinks() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-tree-");
        Path nested = Files.createDirectories(root.resolve("one/two"));
        Files.write(nested.resolve("payload.bin"), new byte[] {1, 2, 3});
        final int[] directories = {0};
        final int[] files = {0};

        StorageIndexEngine.Result result = StorageIndexEngine.scan(root.toFile(), snapshot -> {
            if (snapshot.directory) directories[0]++;
            else files[0]++;
        });

        assertEquals(4, result.entriesVisited);
        assertEquals(2, directories[0]);
        assertEquals(1, files[0]);
        assertFalse(result.truncated);
        assertFalse(result.incomplete);
    }

    @Test public void excludesHiddenEntriesByDefaultAndCanIncludeThemExplicitly() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-hidden-");
        Files.write(root.resolve("visible.txt"), new byte[] {1});
        Files.write(root.resolve(".private.txt"), new byte[] {2});
        final int[] defaultFiles = {0};
        final int[] allFiles = {0};

        StorageIndexEngine.Result visible = StorageIndexEngine.scan(root.toFile(), 4, 100, snapshot -> {
            if (!snapshot.directory) defaultFiles[0]++;
        });
        StorageIndexEngine.Result all = StorageIndexEngine.scan(root.toFile(), 4, 100, true, false, snapshot -> {
            if (!snapshot.directory) allFiles[0]++;
        });

        assertEquals(1, defaultFiles[0]);
        assertEquals(2, allFiles[0]);
        assertFalse(visible.incomplete);
        assertFalse(all.incomplete);
    }

    @Test public void neverFollowsSymbolicLinksOutsideTheRoot() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-link-root-");
        Path outside = Files.createTempDirectory("toxi-index-link-outside-");
        Files.write(outside.resolve("secret.txt"), new byte[] {9});
        try {
            Files.createSymbolicLink(root.resolve("linked"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return;
        }
        final int[] files = {0};
        StorageIndexEngine.Result result = StorageIndexEngine.scan(root.toFile(), snapshot -> {
            if (!snapshot.directory) files[0]++;
        });
        assertEquals(0, files[0]);
        assertEquals(1, result.entriesVisited);
        assertFalse(result.incomplete);
    }

    @Test public void reportsDepthLimitAsIncomplete() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-depth-");
        Files.createDirectories(root.resolve("one/two/three"));
        StorageIndexEngine.Result result = StorageIndexEngine.scan(root.toFile(), 1, 100, snapshot -> { });
        assertTrue(result.incomplete);
        assertFalse(result.truncated);
    }

    @Test public void reportsEntryLimitAsTruncatedAndIncomplete() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-count-");
        Files.write(root.resolve("a.txt"), new byte[] {1});
        Files.write(root.resolve("b.txt"), new byte[] {2});
        StorageIndexEngine.Result result = StorageIndexEngine.scan(root.toFile(), 4, 1, snapshot -> { });
        assertEquals(1, result.entriesVisited);
        assertTrue(result.truncated);
        assertTrue(result.incomplete);
    }

    @Test public void cancellationAbortsTheScanImmediately() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-cancel-");
        OperationCancellation cancellation = new OperationCancellation();
        OperationCancellation.Token token = cancellation.begin();
        try {
            cancellation.cancelActive();
            assertThrows(IOException.class, () -> StorageIndexEngine.scan(root.toFile(), snapshot -> { }));
        } finally {
            cancellation.finish(token);
        }
    }

    @Test public void rejectsInvalidTraversalLimits() throws Exception {
        Path root = Files.createTempDirectory("toxi-index-limits-");
        assertThrows(IllegalArgumentException.class, () -> StorageIndexEngine.scan(root.toFile(), -1, 10, snapshot -> { }));
        assertThrows(IllegalArgumentException.class, () -> StorageIndexEngine.scan(root.toFile(), 1, 0, snapshot -> { }));
    }
}
