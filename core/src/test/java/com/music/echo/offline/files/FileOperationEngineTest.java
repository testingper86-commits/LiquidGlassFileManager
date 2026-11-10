package com.music.echo.offline.files;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FileOperationEngineTest {
    @Test public void uniqueRejectsTraversalAndReservesCollisionFreeNames() throws Exception {
        File root=Files.createTempDirectory("toxifiles-unique-").toFile();
        assertEquals(new File(root,"safe-name.txt").getCanonicalFile(),FileOperationEngine.unique(root,"safe-name.txt").getCanonicalFile());
        assertThrows(IllegalArgumentException.class,()->FileOperationEngine.unique(root,"../outside.txt"));
        assertThrows(IllegalArgumentException.class,()->FileOperationEngine.unique(root,"nested/child.txt"));
        assertThrows(IllegalArgumentException.class,()->FileOperationEngine.unique(root,"nested\\child.txt"));
        assertThrows(IllegalArgumentException.class,()->FileOperationEngine.unique(root,".."));
        Files.write(new File(root,"report.txt").toPath(),new byte[]{1});
        assertEquals("report (1).txt",FileOperationEngine.unique(root,"report.txt").getName());
    }

    @Test public void createUniqueDirectoryAtomicallyReservesCollisionFreeFolders() throws Exception {
        File root = Files.createTempDirectory("toxifiles-reserve-").toFile();
        File first = FileOperationEngine.createUniqueDirectory(root, "unpacked");
        File second = FileOperationEngine.createUniqueDirectory(root, "unpacked");
        assertTrue(first.isDirectory()); assertTrue(second.isDirectory());
        assertEquals("unpacked (1)", second.getName());
    }

    @Test public void publishesCompleteFileWithoutReplacingExistingData() throws Exception {
        File root=Files.createTempDirectory("toxifiles-copy-").toFile(),source=new File(root,"source.txt"),target=new File(root,"target.txt");
        Files.write(source.toPath(),"complete payload".getBytes(StandardCharsets.UTF_8));FileOperationEngine.copy(source,target);
        assertEquals("complete payload",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
        try{FileOperationEngine.copy(source,target);fail("Existing destination was overwritten");}
        catch(IOException expected){assertEquals("complete payload",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));}
    }

    @Test public void publishesDirectoryOnlyAfterItsTreeIsCopied() throws Exception {
        File root=Files.createTempDirectory("toxifiles-copy-tree-").toFile(),source=new File(root,"source"),nested=new File(source,"nested");
        assertTrue(nested.mkdirs());Files.write(new File(nested,"data.bin").toPath(),new byte[]{1,2,3,4});
        File target=new File(root,"target");FileOperationEngine.copy(source,target);
        assertArrayEquals(new byte[]{1,2,3,4},Files.readAllBytes(new File(target,"nested/data.bin").toPath()));
    }

    @Test public void rejectsCopyingDirectoryIntoItsOwnDescendant() throws Exception {
        File root=Files.createTempDirectory("toxifiles-copy-self-").toFile(),source=new File(root,"source");assertTrue(source.mkdir());
        Files.write(new File(source,"data.txt").toPath(),"data".getBytes(StandardCharsets.UTF_8));File target=new File(source,"nested-copy");
        try{FileOperationEngine.copy(source,target);fail("Recursive self-copy was accepted");}catch(IOException expected){assertFalse(target.exists());}
    }

    @Test public void cancelledCopyLeavesNoDestinationOrStagingTree() throws Exception {
        File root=Files.createTempDirectory("toxifiles-copy-cancel-").toFile(),source=new File(root,"source.txt");
        Files.write(source.toPath(),"payload".getBytes(StandardCharsets.UTF_8));File target=new File(root,"target.txt");
        OperationCancellation cancellation=new OperationCancellation();OperationCancellation.Token token=cancellation.begin();
        try{cancellation.cancelActive();try{FileOperationEngine.copy(source,target);fail("Cancelled copy continued");}catch(IOException expected){assertFalse(target.exists());}}
        finally{cancellation.finish(token);}
        File[] leftovers=root.listFiles((dir,name)->name.startsWith(".toxifiles-copy-"));assertNotNull(leftovers);assertEquals(0,leftovers.length);
    }

    @Test public void copyBoundsDeepDirectoryTraversalAndCleansStagingTree() throws Exception {
        File root = Files.createTempDirectory("toxifiles-copy-depth-").toFile();
        File source = new File(root, "source");
        assertTrue(source.mkdir());
        java.nio.file.Path nested = source.toPath();
        for (int i = 0; i < TreeTraversalBudget.DEFAULT_MAX_DEPTH + 8; i++) {
            nested = nested.resolve("d");
            Files.createDirectory(nested);
        }
        Files.write(nested.resolve("deep.bin"), new byte[]{1, 2, 3});
        File target = new File(root, "target");
        try {
            FileOperationEngine.copy(source, target);
            fail("Copy accepted a directory tree beyond the supported depth");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("nesting"));
        }
        assertFalse(target.exists());
        File[] leftovers = root.listFiles((dir, name) -> name.startsWith(".toxifiles-copy-"));
        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
    }

    @Test public void refusesSymlinkSourcesAndDoesNotFollowLinksDuringCopy() throws Exception {
        File root=Files.createTempDirectory("toxifiles-copy-link-").toFile(),target=new File(root,"target.txt"),outside=Files.createTempFile("toxifiles-outside-",".txt").toFile();
        Files.write(outside.toPath(),new byte[]{7,8,9});File link=new File(root,"link.txt");Files.createSymbolicLink(link.toPath(),outside.toPath());
        try{FileOperationEngine.copy(link,target);fail("Symbolic link source was copied");}catch(IOException expected){assertFalse(target.exists());}
        assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(outside.toPath()));
    }

    @Test public void deleteTreeDeletesLinksButPreservesTheirTargets() throws Exception {
        File root=Files.createTempDirectory("toxifiles-delete-link-").toFile(),outside=Files.createTempFile("toxifiles-preserve-",".bin").toFile();
        Files.write(outside.toPath(),new byte[]{3,4});File link=new File(root,"link.bin");Files.createSymbolicLink(link.toPath(),outside.toPath());
        assertTrue(FileOperationEngine.deleteTree(root));assertFalse(Files.exists(link.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertArrayEquals(new byte[]{3,4},Files.readAllBytes(outside.toPath()));
    }

    @Test public void treeSizeCountsRegularFilesAndSkipsSymbolicLinks() throws Exception {
        File root=Files.createTempDirectory("toxifiles-size-").toFile(),nested=new File(root,"nested");assertTrue(nested.mkdir());
        File data=new File(nested,"data.bin");Files.write(data.toPath(),new byte[]{1,2,3,4,5});File outside=Files.createTempFile("toxifiles-size-outside-",".bin").toFile();
        Files.write(outside.toPath(),new byte[]{1,2,3,4,5,6,7});Files.createSymbolicLink(new File(root,"link.bin").toPath(),outside.toPath());
        long[] totals=new long[2];assertTrue(FileOperationEngine.treeSize(root,totals));assertEquals(5L,totals[0]);assertEquals(1L,totals[1]);
    }

    @Test public void treeSizeBoundsDeepDirectoryTraversalWithoutRecursion() throws Exception {
        File root = Files.createTempDirectory("toxifiles-size-depth-").toFile();
        java.nio.file.Path nested = root.toPath();
        for (int i = 0; i < TreeTraversalBudget.DEFAULT_MAX_DEPTH + 8; i++) {
            nested = nested.resolve("d");
            Files.createDirectory(nested);
        }
        Files.write(nested.resolve("deep.bin"), new byte[]{1, 2, 3});
        long[] totals = new long[2];
        assertFalse(FileOperationEngine.treeSize(root, totals));
        assertEquals(0L, totals[0]);
        assertEquals(0L, totals[1]);
    }

    @Test public void treeSizeStopsWhenItsCallerNoLongerNeedsTheResult() throws Exception {
        File root = Files.createTempDirectory("toxifiles-size-cancel-").toFile();
        for (int i = 0; i < 100; i++) Files.write(new File(root, "file-" + i + ".bin").toPath(), new byte[]{1, 2, 3});
        long[] totals = new long[2];
        AtomicInteger checks = new AtomicInteger();
        assertFalse(FileOperationEngine.treeSize(root, totals, () -> checks.incrementAndGet() > 12));
        assertTrue("Cancelled traversal must stop before measuring every file", totals[1] < 100L);
    }

    @Test public void deleteTreeHandlesDeepDirectoryTraversalWithoutRecursion() throws Exception {
        File root = Files.createTempDirectory("toxifiles-delete-depth-").toFile();
        java.nio.file.Path nested = root.toPath();
        for (int i = 0; i < TreeTraversalBudget.DEFAULT_MAX_DEPTH + 8; i++) {
            nested = nested.resolve("d");
            Files.createDirectory(nested);
        }
        Files.write(nested.resolve("deep.bin"), new byte[]{1});
        assertTrue(FileOperationEngine.deleteTree(root));
        assertFalse("Deep directory tree should be fully removed", root.exists());
    }

    @Test public void deleteTreeRemovesNestedRegularFiles() throws Exception {
        File root=Files.createTempDirectory("toxifiles-delete-tree-").toFile(),nested=new File(root,"a/b");assertTrue(nested.mkdirs());
        Files.write(new File(nested,"file.bin").toPath(),new byte[]{1});assertTrue(FileOperationEngine.deleteTree(root));assertFalse(root.exists());
    }

    @Test public void cancelledRecursiveDeleteStopsBeforeRemovingTheTree() throws Exception {
        File root=Files.createTempDirectory("toxifiles-delete-cancel-").toFile(),nested=new File(root,"a/b");assertTrue(nested.mkdirs());
        File data=new File(nested,"file.bin");Files.write(data.toPath(),new byte[]{1,2,3});
        OperationCancellation cancellation=new OperationCancellation();OperationCancellation.Token token=cancellation.begin();
        try {
            cancellation.cancelActive();
            assertFalse(FileOperationEngine.deleteTree(root));
            assertTrue(root.isDirectory());assertTrue(data.isFile());
        } finally { cancellation.finish(token); }
    }
}
