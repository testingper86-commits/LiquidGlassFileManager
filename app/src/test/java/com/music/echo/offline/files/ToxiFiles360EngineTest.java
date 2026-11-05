package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class ToxiFiles360EngineTest {
    @Test public void catalogSeparatesActualCompatibilityLevels() {
        assertEquals(ToxiFiles360Engine.Level.PARTIAL,ToxiFiles360Engine.format("report.docx").level);
        assertEquals(ToxiFiles360Engine.Level.PARTIAL,ToxiFiles360Engine.format("budget.xlsx").level);
        assertEquals(ToxiFiles360Engine.Level.FULL,ToxiFiles360Engine.format("rows.csv").level);
        assertEquals(ToxiFiles360Engine.Level.VIEW_ONLY,ToxiFiles360Engine.format("legacy.doc").level);
        assertEquals(ToxiFiles360Engine.Level.UNSUPPORTED,ToxiFiles360Engine.format("binary.xlsb").level);
        assertEquals(ToxiFiles360Engine.Level.UNSUPPORTED,ToxiFiles360Engine.format("notes.onepkg").level);
    }

    @Test public void csvParserHandlesQuotesCommasAndNewlines() {
        String csv="Name,Description,Value\r\nA,\"red, blue\",1\r\nB,\"two lines\ncontinued\",2";
        ArrayList<ArrayList<String>> rows=ToxiFiles360Engine.parseDelimited(csv,',');
        assertEquals(3,rows.size());
        assertEquals("red, blue",rows.get(1).get(1));
        assertEquals("two lines\ncontinued",rows.get(2).get(1));
        assertEquals(rows.size(),ToxiFiles360Engine.parseDelimited(ToxiFiles360Engine.toDelimited(rows,','),',').size());
    }

    @Test public void wordAndSpreadsheetDocumentsRoundTrip() throws Exception {
        File dir=Files.createTempDirectory("tox360-office").toFile();
        File docx=new File(dir,"sample.docx");
        ToxiFiles360Engine.writeWord(docx,"First paragraph\nSecond paragraph");
        assertTrue(ToxiFiles360Engine.extractText(docx).contains("First paragraph"));
        File xlsx=new File(dir,"sample.xlsx");
        ToxiFiles360Engine.SheetData sheet=new ToxiFiles360Engine.SheetData("Budget");
        sheet.set(0,0,"Amount");sheet.set(1,0,"5");sheet.set(2,0,"=SUM(A2:A2)");
        ToxiFiles360Engine.writeWorkbook(xlsx,Collections.singletonList(sheet));
        assertTrue(ToxiFiles360Engine.extractText(xlsx).contains("Amount"));
        assertEquals("Budget",ToxiFiles360Engine.readSheets(xlsx).get(0).name);
    }

    @Test public void pptxSlideTextIsExtractedFromBoundedOoxmlParts() throws Exception {
        File dir=Files.createTempDirectory("tox360-slides").toFile();
        File pptx=new File(dir,"sample.pptx");
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(pptx))){
            putSlide(zip,"ppt/slides/slide2.xml","Second slide");
            putSlide(zip,"ppt/slides/slide1.xml","First slide");
        }
        ToxiFiles360Engine.PresentationData read=ToxiFiles360Engine.readPresentation(pptx);
        assertEquals(2,read.slides.size());
        assertTrue(read.slides.get(0).contains("First slide"));
        assertTrue(ToxiFiles360Engine.extractText(pptx).contains("Second slide"));
    }

    private static void putSlide(java.util.zip.ZipOutputStream zip,String name,String text)throws Exception {
        String xml="<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><p:cSld><p:spTree><a:t>"+text+"</a:t></p:spTree></p:cSld></p:sld>";
        zip.putNextEntry(new java.util.zip.ZipEntry(name));
        zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Test public void rtfConversionAndFormatLookupAreDeterministic() {
        assertEquals("Hello world",ToxiFiles360Engine.stripRtf("{\\rtf1\\ansi Hello\\par world}"));
        assertEquals("xlsx",ToxiFiles360Engine.extension("Budget.XLSX"));
        assertNull(ToxiFiles360Engine.format("archive.unknown"));
    }
}
