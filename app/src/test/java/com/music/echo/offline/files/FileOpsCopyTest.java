package com.music.echo.offline.files;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class FileOpsCopyTest {

    @Test public void uniqueRejectsProviderAndPeerSuppliedPathTraversalNames() throws Exception {
        File root = Files.createTempDirectory("toxifiles-unique-").toFile();
        assertEquals(new File(root, "safe-name.txt").getCanonicalFile(),
                FileOps.unique(root, "safe-name.txt").getCanonicalFile());
        assertThrows(IllegalArgumentException.class, () -> FileOps.unique(root, "../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileOps.unique(root, "nested/child.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileOps.unique(root, "nested\\child.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileOps.unique(root, ".."));
    }

    @Test public void publishesCompleteFileWithoutReplacingExistingData() throws Exception {
        File root = Files.createTempDirectory("toxifiles-copy-").toFile();
        File source = new File(root, "source.txt");
        File target = new File(root, "target.txt");
        Files.write(source.toPath(), "complete payload".getBytes(StandardCharsets.UTF_8));
        FileOps.copy(source, target);
        assertEquals("complete payload", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        try {
            FileOps.copy(source, target);
            fail("Existing destination was overwritten");
        } catch (IOException expected) {
            assertEquals("complete payload", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        }
    }

    @Test public void publishesDirectoryOnlyAfterItsTreeIsCopied() throws Exception {
        File root = Files.createTempDirectory("toxifiles-copy-tree-").toFile();
        File source = new File(root, "source");
        File nested = new File(source, "nested");
        assertTrue(nested.mkdirs());
        Files.write(new File(nested, "data.bin").toPath(), new byte[]{1, 2, 3, 4});
        File target = new File(root, "target");
        FileOps.copy(source, target);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(new File(target, "nested/data.bin").toPath()));
    }

    @Test public void rejectsCopyingDirectoryIntoItsOwnDescendant() throws Exception {
        File root = Files.createTempDirectory("toxifiles-copy-self-").toFile();
        File source = new File(root, "source");
        assertTrue(source.mkdir());
        Files.write(new File(source, "data.txt").toPath(), "data".getBytes(StandardCharsets.UTF_8));
        File target = new File(source, "nested-copy");
        try { FileOps.copy(source, target); fail("Recursive self-copy was accepted"); }
        catch (IOException expected) { assertFalse(target.exists()); }
    }

    @Test public void cancelledCopyLeavesNoDestinationOrStagingTree() throws Exception {
        File root = Files.createTempDirectory("toxifiles-copy-cancel-").toFile();
        File source = new File(root, "source.txt");
        Files.write(source.toPath(), "payload".getBytes(StandardCharsets.UTF_8));
        File target = new File(root, "target.txt");
        OperationCancellation cancellation = new OperationCancellation();
        OperationCancellation.Token token = cancellation.begin();
        try {
            cancellation.cancelActive();
            try { FileOps.copy(source, target); fail("Cancelled copy continued"); }
            catch (IOException expected) { assertFalse(target.exists()); }
        } finally {
            cancellation.finish(token);
        }
        File[] leftovers = root.listFiles((dir, name) -> name.startsWith(".toxifiles-copy-"));
        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
    }
}
