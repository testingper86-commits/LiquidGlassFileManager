package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlViewerRoutingTest {
    @Test public void recognizesOnlyHtmlAndHtmExtensionsCaseInsensitively() {
        assertTrue(FileKinds.isHtmlExtension("html"));
        assertTrue(FileKinds.isHtmlExtension("htm"));
        assertTrue(FileKinds.isHtmlExtension("HTML"));
        assertTrue(FileKinds.isHtmlExtension("HtM"));
        assertFalse(FileKinds.isHtmlExtension("xhtml"));
        assertFalse(FileKinds.isHtmlExtension("txt"));
        assertFalse(FileKinds.isHtmlExtension(""));
        assertFalse(FileKinds.isHtmlExtension(null));
    }

    @Test public void classifiesBuiltInViewerFileTypes() {
        assertEquals(FileKinds.PDF, FileKinds.kindOfExt("pdf"));
        assertEquals(FileKinds.VIDEO, FileKinds.kindOfExt("mp4"));
        assertEquals(FileKinds.VIDEO, FileKinds.kindOfExt("mkv"));
        assertEquals(FileKinds.AUDIO, FileKinds.kindOfExt("mp3"));
        assertEquals(FileKinds.AUDIO, FileKinds.kindOfExt("flac"));
        assertEquals(FileKinds.IMAGE, FileKinds.kindOfExt("webp"));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfExt("html"));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfExt("htm"));
    }
}
