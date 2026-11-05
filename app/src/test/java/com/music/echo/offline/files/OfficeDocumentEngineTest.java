package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.CellType;

public class OfficeDocumentEngineTest {
    @Test public void catalogContainsOnlyTheFourRequestedWorkspaces() {
        assertEquals(OfficeDocumentEngine.Level.PARTIAL,OfficeDocumentEngine.format("report.docx").level);
        assertEquals(OfficeDocumentEngine.Level.PARTIAL,OfficeDocumentEngine.format("budget.xlsx").level);
        assertEquals(OfficeDocumentEngine.Level.FULL,OfficeDocumentEngine.format("rows.csv").level);
        assertEquals(OfficeDocumentEngine.Level.VIEW_ONLY,OfficeDocumentEngine.format("legacy.doc").level);
        assertEquals(OfficeDocumentEngine.Level.PARTIAL,OfficeDocumentEngine.format("slides.pptx").level);
        assertEquals(OfficeDocumentEngine.Level.VIEW_ONLY,OfficeDocumentEngine.format("slides.ppt").level);
        assertEquals(OfficeDocumentEngine.Level.PARTIAL,OfficeDocumentEngine.format("scan.pdf").level);
        assertNull(OfficeDocumentEngine.format("binary.xlsb"));
        assertNull(OfficeDocumentEngine.format("unknown.bin"));
    }

    @Test public void csvParserHandlesQuotesCommasAndNewlines() {
        String csv="Name,Description,Value\r\nA,\"red, blue\",1\r\nB,\"two lines\ncontinued\",2";
        ArrayList<ArrayList<String>> rows=OfficeDocumentEngine.parseDelimited(csv,',');
        assertEquals(3,rows.size());
        assertEquals("red, blue",rows.get(1).get(1));
        assertEquals("two lines\ncontinued",rows.get(2).get(1));
        assertEquals(rows.size(),OfficeDocumentEngine.parseDelimited(OfficeDocumentEngine.toDelimited(rows,','),',').size());
    }

    @Test public void wordAndSpreadsheetDocumentsRoundTrip() throws Exception {
        File dir=Files.createTempDirectory("office-docx").toFile();
        File docx=new File(dir,"sample.docx");
        OfficeDocumentEngine.writeWord(docx,"First paragraph\nSecond paragraph");
        assertTrue(OfficeDocumentEngine.extractText(docx).contains("First paragraph"));
        File xlsx=new File(dir,"sample.xlsx");
        OfficeDocumentEngine.SheetData sheet=new OfficeDocumentEngine.SheetData("Budget");
        sheet.set(0,0,"Amount");sheet.set(1,0,"5");sheet.set(2,0,"=SUM(A2:A2)");
        OfficeDocumentEngine.writeWorkbook(xlsx,Collections.singletonList(sheet));
        assertTrue(OfficeDocumentEngine.extractText(xlsx).contains("Amount"));
        assertEquals("Budget",OfficeDocumentEngine.readSheets(xlsx).get(0).name);
        try(Workbook workbook=WorkbookFactory.create(xlsx)) {
            org.apache.poi.ss.usermodel.Cell formula=workbook.getSheetAt(0).getRow(2).getCell(0);
            assertEquals(CellType.NUMERIC,formula.getCachedFormulaResultType());
            assertEquals(5d,formula.getNumericCellValue(),0.001d);
        }
    }

    @Test public void pptxSlideTextIsExtractedFromBoundedOoxmlParts() throws Exception {
        File dir=Files.createTempDirectory("office-slides").toFile();
        File pptx=new File(dir,"sample.pptx");
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(pptx))){
            putSlide(zip,"ppt/slides/slide2.xml","Second slide");
            putSlide(zip,"ppt/slides/slide1.xml","First slide");
        }
        OfficeDocumentEngine.PresentationData read=OfficeDocumentEngine.readPresentation(pptx);
        assertEquals(2,read.slides.size());
        assertTrue(read.slides.get(0).contains("First slide"));
        assertTrue(OfficeDocumentEngine.extractText(pptx).contains("Second slide"));
    }

    @Test public void pptxSlideTextCanBeEditedWithoutChangingTheSourcePackage() throws Exception {
        File dir=Files.createTempDirectory("office-slide-edit").toFile();
        File source=new File(dir,"source.pptx"),edited=new File(dir,"edited.pptx");
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(source))) {
            putSlide(zip,"ppt/slides/slide1.xml","Original title");
            putSlide(zip,"ppt/slides/slide2.xml","Second slide remains");
        }
        byte[] original=Files.readAllBytes(source.toPath());
        OfficeDocumentEngine.writePresentationText(source,edited,0,"Edited title");
        assertTrue(OfficeDocumentEngine.readPresentation(edited).slides.get(0).contains("Edited title"));
        assertTrue(OfficeDocumentEngine.readPresentation(edited).slides.get(1).contains("Second slide remains"));
        assertArrayEquals(original,Files.readAllBytes(source.toPath()));
    }


    private static void putSlide(java.util.zip.ZipOutputStream zip,String name,String text)throws Exception {
        String xml="<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><p:cSld><p:spTree><a:t>"+text+"</a:t></p:spTree></p:cSld></p:sld>";
        zip.putNextEntry(new java.util.zip.ZipEntry(name));
        zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

}
