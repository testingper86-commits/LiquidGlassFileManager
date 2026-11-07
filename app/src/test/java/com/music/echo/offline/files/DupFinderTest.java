package com.music.echo.offline.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DupFinderTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void groupsExactContentButNotDifferentContentOfTheSameSize() throws Exception {
        File first = temporary.newFile("first.bin");
        File second = temporary.newFile("second.bin");
        File different = temporary.newFile("different.bin");
        byte[] shared = new byte[]{0, 1, 2, 3, 4, 5};
        Files.write(first.toPath(), shared);
        Files.write(second.toPath(), shared);
        Files.write(different.toPath(), new byte[]{5, 4, 3, 2, 1, 0});

        List<DupFinder.Group> groups = DupFinder.find(Arrays.asList(
                new Entry(first), new Entry(second), new Entry(different)));

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).files.size());
        assertEquals(shared.length, groups.get(0).size);
    }

    @Test public void includesEmptyFilesAsExactDuplicates() throws Exception {
        File first = temporary.newFile("empty-a.bin");
        File second = temporary.newFile("empty-b.bin");

        List<DupFinder.Group> groups = DupFinder.find(Arrays.asList(new Entry(first), new Entry(second)));

        assertEquals(1, groups.size());
        assertEquals(0L, groups.get(0).size);
        assertEquals(2, groups.get(0).files.size());
    }

    @Test public void reportsDisappearingFilesInsteadOfReturningPartialResults() throws Exception {
        File first = temporary.newFile("first.bin");
        File second = temporary.newFile("second.bin");
        Files.write(first.toPath(), new byte[]{1, 2, 3});
        Files.write(second.toPath(), new byte[]{1, 2, 3});
        Entry firstSnapshot = new Entry(first);
        Entry secondSnapshot = new Entry(second);
        assertTrue(first.delete());

        try {
            DupFinder.find(Arrays.asList(firstSnapshot, secondSnapshot));
            fail("A missing indexed file must not produce a partial duplicate result");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("first.bin"));
        }
    }
}
