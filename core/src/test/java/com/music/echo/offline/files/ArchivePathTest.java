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
        reject("bad" + (char) 0 + "name.txt", false);
        reject("file.txt:payload", false);
    }

    @Test public void toolZipEditorUsesTheSharedStrictValidator() throws Exception {
        assertEquals("assets/", ToxiToolCore.validateZipName("assets/"));
        assertEquals("renamed/", ToxiToolCore.validateZipName("renamed", true));
        assertEquals("assets/icon.png", ToxiToolCore.validateZipName("assets\\icon.png"));
        rejectTool("folder//file.txt");
        rejectTool("folder/./file.txt");
        rejectTool("bad" + (char) 0 + "name.txt");
        rejectTool("file.txt:payload");
    }

    private static void rejectTool(String path) throws Exception {
        try { ToxiToolCore.validateZipName(path); fail("Unsafe tool archive path accepted: " + path); }
        catch (java.io.IOException expected) { /* rejected */ }
    }

    private static void reject(String path, boolean directory) throws Exception {
        try { ArchivePath.normalize(path, directory); fail("Unsafe path accepted: " + path); }
        catch (java.io.IOException expected) { /* rejected */ }
    }
}
