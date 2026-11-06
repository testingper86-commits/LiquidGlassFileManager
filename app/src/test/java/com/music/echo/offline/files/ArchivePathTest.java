package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArchivePathTest {
    @Test public void acceptsDirectoryEntriesWithTrailingSlash() throws Exception {
        assertEquals("assets", ArchivePath.normalize("assets/", true));
        assertEquals("assets/icon.png", ArchivePath.normalize("assets\\icon.png", false));
    }

    @Test public void rejectsTraversalAbsoluteAndAmbiguousPaths() throws Exception {
        reject("../outside.txt", false);
        reject("folder/../../outside.txt", false);
        reject("/absolute.txt", false);
        reject("C:\\outside.txt", false);
        reject("folder//file.txt", false);
        reject("folder/./file.txt", false);
        reject("folder/../file.txt", false);
    }

    private static void reject(String path, boolean directory) throws Exception {
        try { ArchivePath.normalize(path, directory); fail("Unsafe path accepted: " + path); }
        catch (java.io.IOException expected) { /* rejected */ }
    }
}
