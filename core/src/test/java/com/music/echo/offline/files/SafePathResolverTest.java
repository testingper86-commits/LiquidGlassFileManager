package com.music.echo.offline.files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SafePathResolverTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void resolvesExistingNestedFilesInsideRoot() throws Exception {
        File root = temporary.newFolder("root");
        File assets = new File(root, "assets");
        if (!assets.mkdirs()) throw new IOException("Could not create test assets directory");
        File asset = new File(assets, "image.png");
        Files.write(asset.toPath(), new byte[]{1, 2, 3});
        assertEquals(asset.getCanonicalFile().toPath(),
                SafePathResolver.resolveExistingFile(root.toPath(), "assets/image.png"));
    }

    @Test public void preservesLiteralPercentEncodedLookingFileNamesWithoutDecodingAgain() throws Exception {
        File root = temporary.newFolder("root");
        File asset = new File(root, "%2e%2e");
        Files.write(asset.toPath(), new byte[]{4, 5, 6});
        assertEquals(asset.getCanonicalFile().toPath(),
                SafePathResolver.resolveExistingFile(root.toPath(), "%2e%2e"));
    }

    @Test public void rejectsParentTraversalAbsoluteAndBackslashPaths() throws Exception {
        File root = temporary.newFolder("root");
        String[] unsafe = {"../outside.txt", "nested/../../outside.txt", new File(root, "absolute.txt").getAbsolutePath(), "assets\\image.png"};
        for (String path : unsafe) {
            try {
                SafePathResolver.resolveExistingFile(root.toPath(), path);
                fail("Unsafe path accepted: " + path);
            } catch (IOException expected) {
                // Expected: every supplied path violates the relative-path contract.
            }
        }
    }

    @Test public void rejectsSymlinkTargetsOutsideTheRoot() throws Exception {
        File root = temporary.newFolder("root");
        File outside = temporary.newFile("outside.txt");
        Path link = root.toPath().resolve("escape.txt");
        try {
            Files.createSymbolicLink(link, outside.toPath());
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            Assume.assumeNoException(unsupported);
            return;
        }
        try {
            SafePathResolver.resolveExistingFile(root.toPath(), "escape.txt");
            fail("Symlink escape accepted");
        } catch (IOException expected) {
            // Expected: the resolved real path is outside the selected root.
        }
    }
}
