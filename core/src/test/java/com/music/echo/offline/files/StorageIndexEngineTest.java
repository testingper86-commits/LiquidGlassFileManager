package com.music.echo.offline.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class StorageIndexEngineTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final class Collected {
        final List<StorageIndexEngine.Snapshot> entries = new ArrayList<>();
        StorageIndexEngine.Result result;
    }
    private Collected scan(File root, int depth, int limit) throws IOException {
        Collected collected = new Collected();
        collected.result = StorageIndexEngine.scan(root, depth, limit, collected.entries::add);
        return collected;
    }

    private Collected scan(File root, int depth, int limit, boolean includeHiddenEntries,
                           boolean includeRootAndroidDirectory) throws IOException {
        Collected collected = new Collected();
        collected.result = StorageIndexEngine.scan(root, depth, limit, includeHiddenEntries,
                includeRootAndroidDirectory, collected.entries::add);
        return collected;
    }

    private boolean contains(Collected collected, File expected) throws IOException {
        File canonical = expected.getCanonicalFile();
        for (StorageIndexEngine.Snapshot entry : collected.entries) {
            if (entry.file.getCanonicalFile().equals(canonical)) return true;
        }
        return false;
    }

    @Test public void streamsNestedDirectoriesAndFileMetadata() throws Exception {
        File root = temporary.newFolder("root"), nested = new File(root, "docs");
        assertTrue(nested.mkdir()); File document = new File(nested, "readme.txt"); Files.write(document.toPath(), new byte[]{1, 2, 3});
        Collected collected = scan(root, 9, 100);
        assertEquals(2, collected.entries.size());
        assertTrue(collected.entries.get(0).directory);
        StorageIndexEngine.Snapshot file = collected.entries.get(1);
        assertEquals(document.getCanonicalFile(), file.file.getCanonicalFile());
        assertEquals(3L, file.length);
        assertEquals(document.lastModified(), file.modifiedAt);
        assertFalse(collected.result.incomplete);
    }

    @Test public void skipsHiddenEntriesAndRootAndroidDirectory() throws Exception {
        File root = temporary.newFolder("root"), hidden = new File(root, ".cache"), android = new File(root, "Android");
        assertTrue(hidden.mkdir()); assertTrue(android.mkdir());
        Files.write(new File(hidden, "hidden.txt").toPath(), new byte[]{1});
        Files.write(new File(android, "private.txt").toPath(), new byte[]{1});
        File visible = new File(root, "visible.txt"); Files.write(visible.toPath(), new byte[]{2});
        Collected collected = scan(root, 9, 100);
        assertEquals(1, collected.entries.size());
        assertEquals(visible.getCanonicalFile(), collected.entries.get(0).file.getCanonicalFile());
    }

    @Test public void includesHiddenEntriesAndRootAndroidDirectoryWhenRequested() throws Exception {
        File root = temporary.newFolder("root"), hidden = new File(root, ".cache"), android = new File(root, "Android");
        assertTrue(hidden.mkdir()); assertTrue(android.mkdir());
        File hiddenFile = new File(hidden, "hidden.txt"), androidFile = new File(android, "private.txt");
        File visible = new File(root, "visible.txt");
        Files.write(hiddenFile.toPath(), new byte[]{1});
        Files.write(androidFile.toPath(), new byte[]{2});
        Files.write(visible.toPath(), new byte[]{3});

        Collected collected = scan(root, 9, 100, true, true);
        assertEquals(5, collected.entries.size());
        assertTrue(contains(collected, hidden));
        assertTrue(contains(collected, hiddenFile));
        assertTrue(contains(collected, android));
        assertTrue(contains(collected, androidFile));
        assertTrue(contains(collected, visible));
        assertFalse(collected.result.incomplete);
    }

    @Test public void skipsSymbolicLinksWithoutFollowingTheirTargets() throws Exception {
        File root = temporary.newFolder("root"), target = temporary.newFile("outside.txt");
        Files.write(target.toPath(), new byte[]{1, 2, 3});
        File link = new File(root, "linked.txt"); Files.createSymbolicLink(link.toPath(), target.toPath());
        Collected collected = scan(root, 9, 100);
        assertTrue(collected.entries.isEmpty());
    }

    @Test public void enforcesEntryLimitAndMarksResultsIncomplete() throws Exception {
        File root = temporary.newFolder("root");
        for (int i = 0; i < 5; i++) Files.write(new File(root, "file-" + i).toPath(), new byte[]{(byte) i});
        Collected collected = scan(root, 9, 2);
        assertEquals(2, collected.entries.size());
        assertTrue(collected.result.truncated);
        assertTrue(collected.result.incomplete);
        assertEquals(2, collected.result.entriesVisited);
    }

    @Test public void enforcesDepthLimitAndMarksUnscannedSubtreesIncomplete() throws Exception {
        File root = temporary.newFolder("root"), first = new File(root, "one"), second = new File(first, "two");
        assertTrue(first.mkdir()); assertTrue(second.mkdir());
        Files.write(new File(second, "deep.txt").toPath(), new byte[]{1});
        Collected collected = scan(root, 1, 100);
        assertEquals(2, collected.entries.size());
        assertTrue(collected.result.incomplete);
    }

    @Test public void cancellationStopsTraversalInsteadOfPublishingPartialResults() throws Exception {
        File root = temporary.newFolder("root"); Files.write(new File(root, "file.txt").toPath(), new byte[]{1});
        OperationCancellation cancellation = new OperationCancellation();
        OperationCancellation.Token token = cancellation.begin();
        try {
            cancellation.cancelActive();
            try { scan(root, 9, 100); fail("Cancelled storage scan returned a result"); }
            catch (IOException expected) { assertEquals("Operation cancelled", expected.getMessage()); }
        } finally { cancellation.finish(token); }
    }

    @Test public void volumeSnapshotRejectsImpossibleCapacityAndReportsUsedBytes() {
        StorageIndexEngine.VolumeSnapshot volume = new StorageIndexEngine.VolumeSnapshot("internal", "Internal", 1000L, 250L, true);
        assertEquals(750L, volume.usedBytes);
        assertEquals(250L, volume.availableBytes);
        try {
            new StorageIndexEngine.VolumeSnapshot("bad", "Bad", 100L, 101L, false);
            fail("Available bytes above total must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void categoryTotalsKeepAccessibleBytesSeparateAndMarkPartialScans() throws Exception {
        File root = temporary.newFolder("root");
        Files.write(new File(root, "photo.jpg").toPath(), new byte[]{1, 2, 3, 4});
        Files.write(new File(root, "report.pdf").toPath(), new byte[]{5, 6});
        Collected collected = scan(root, 9, 100);
        StorageIndexEngine.TotalsBuilder totals = new StorageIndexEngine.TotalsBuilder();
        totals.add(0, 4L);
        totals.add(3, 2L);
        StorageIndexEngine.CategoryTotals result = totals.finish(collected.result);
        assertEquals(6L, result.totalIndexedBytes);
        assertEquals(2, result.totalIndexedFiles);
        assertEquals(4L, result.bytes[0]);
        assertEquals(2L, result.bytes[3]);
        assertTrue(result.complete);
        StorageIndexEngine.CategoryTotals incomplete = totals.finish(new StorageIndexEngine.Result(2, true, true));
        assertFalse(incomplete.complete);
    }

    @Test public void categoryTotalsSaturateRatherThanOverflow() {
        StorageIndexEngine.TotalsBuilder totals = new StorageIndexEngine.TotalsBuilder();
        totals.add(0, Long.MAX_VALUE);
        totals.add(0, 1L);
        StorageIndexEngine.CategoryTotals result = totals.finish(new StorageIndexEngine.Result(2, false, false));
        assertEquals(Long.MAX_VALUE, result.bytes[0]);
        assertEquals(Long.MAX_VALUE, result.totalIndexedBytes);
    }

    @Test public void categoryTotalCompletenessRequiresAnActuallyCompleteScan() throws Exception {
        File root = temporary.newFolder("root");
        Collected collected = scan(root, 9, 100);
        StorageIndexEngine.TotalsBuilder totals = new StorageIndexEngine.TotalsBuilder();
        StorageIndexEngine.CategoryTotals complete = totals.finish(collected.result);
        assertTrue(complete.complete);
        assertEquals(0L, complete.totalIndexedBytes);
        assertEquals(0, complete.totalIndexedFiles);
        StorageIndexEngine.CategoryTotals unavailable = totals.finish(null);
        assertFalse(unavailable.complete);
    }

    @Test public void rejectsInvalidScanLimits() throws Exception {
        File root = temporary.newFolder("root");
        try { scan(root, -1, 100); fail("Negative depth accepted"); }
        catch (IllegalArgumentException expected) { }
        try { scan(root, 9, 0); fail("Zero entry limit accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
