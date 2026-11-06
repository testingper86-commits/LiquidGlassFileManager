package com.music.echo.offline.files;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class HtmlViewerRoutingTest {
    @Test public void recognizesHtmlExtensionsCaseInsensitively() {
        for (String extension : new String[]{"html", "htm", "HTML", "HtM", "xhtml", "xht", "shtml", "shtm"}) {
            assertTrue(extension, FileKinds.isHtmlExtension(extension));
        }
        for (String extension : new String[]{"xhtmlx", "txt", "pdf", "", null}) {
            assertFalse(String.valueOf(extension), FileKinds.isHtmlExtension(extension));
        }
    }

    @Test public void normalizesFileExtensionsBeforeClassifying() {
        assertEquals("pdf", FileKinds.extensionOf(new File("report.PDF")));
        assertEquals("html", FileKinds.extensionOf(new File("page.HTML")));
        assertEquals("htm", FileKinds.extensionOf(new File("page.htm ")));
        assertEquals("", FileKinds.extensionOf(new File(".hidden")));
        assertEquals("", FileKinds.extensionOf(new File("README")));
    }

    @Test public void pdfAndHtmlAreDetectedFromTheActualFileName() {
        assertEquals(FileKinds.PDF, FileKinds.kindOfFile(new File("scan.PDF")));
        assertEquals(FileKinds.PDF, FileKinds.kindOfFile(new File("report.pdf ")));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfFile(new File("page.HTML")));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfFile(new File("page.xhtml")));
        assertTrue(FileKinds.isPdfExtension(" PDF "));
        assertFalse(FileKinds.isPdfExtension("pdfx"));
    }

    @Test public void classifiesBuiltInViewerFileTypes() {
        assertEquals(FileKinds.TEXT, FileKinds.kindOfExt("csv"));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfExt("toml"));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfExt("jsonl"));
        assertEquals(FileKinds.TEXT, FileKinds.kindOfExt("tsx"));
        assertEquals(FileKinds.OTHER, FileKinds.kindOfExt("unknown-binary-format"));
        assertEquals(FileKinds.PDF, FileKinds.kindOfExt("pdf"));
        assertEquals(FileKinds.VIDEO, FileKinds.kindOfExt("mp4"));
        assertEquals(FileKinds.VIDEO, FileKinds.kindOfExt("mkv"));
        assertEquals(FileKinds.AUDIO, FileKinds.kindOfExt("mp3"));
        assertEquals(FileKinds.AUDIO, FileKinds.kindOfExt("flac"));
        assertEquals(FileKinds.IMAGE, FileKinds.kindOfExt("webp"));
    }
}
