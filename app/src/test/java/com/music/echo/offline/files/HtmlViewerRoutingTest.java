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
}
