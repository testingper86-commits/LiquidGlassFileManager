package com.music.echo.offline.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class ArchiveOperationsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void createsZipWithNestedFilesAndEmptyDirectories() throws Exception {
        File source=temporary.newFolder("source"),empty=new File(source,"empty"),nested=new File(source,"nested");
        assertTrue(empty.mkdir());assertTrue(nested.mkdir());Files.write(new File(nested,"data.txt").toPath(),"payload".getBytes(StandardCharsets.UTF_8));
        File output=ArchiveOperations.createZip(Arrays.asList(source),temporary.newFolder("archives"));
        try(ZipFile zip=new ZipFile(output)){
            assertNotNull(zip.getEntry("source/empty/"));assertNotNull(zip.getEntry("source/nested/data.txt"));
            try(java.io.InputStream input=zip.getInputStream(zip.getEntry("source/nested/data.txt"))){assertEquals("payload",new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        }
    }

    @Test public void skipsSymbolicLinksWhenCreatingZip() throws Exception {
        File source=temporary.newFolder("source"),outside=temporary.newFile("outside.txt");Files.write(outside.toPath(),new byte[]{1,2,3});
        Files.createSymbolicLink(new File(source,"link.txt").toPath(),outside.toPath());
        File output=ArchiveOperations.createZip(Arrays.asList(source),temporary.newFolder("archives"));
        try(ZipFile zip=new ZipFile(output)){assertNull(zip.getEntry("source/link.txt"));}
    }

    @Test public void extractsValidNestedEntriesAndReportsProgress() throws Exception {
        File parent=temporary.newFolder("extract"),archive=new File(parent,"bundle.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive.toPath()))){zip.putNextEntry(new ZipEntry("folder/"));zip.closeEntry();zip.putNextEntry(new ZipEntry("folder/data.txt"));zip.write("hello".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        AtomicInteger progress=new AtomicInteger();File destination=ArchiveOperations.extractZip(archive,parent,message->progress.incrementAndGet());
        assertEquals("hello",new String(Files.readAllBytes(new File(destination,"folder/data.txt").toPath()),StandardCharsets.UTF_8));assertTrue(progress.get()>=2);
    }

    @Test public void rejectsZipSlipAndCleansPartialExtraction() throws Exception {
        File parent=temporary.newFolder("zip-slip"),archive=new File(parent,"malicious.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive.toPath()))){zip.putNextEntry(new ZipEntry("../escaped.txt"));zip.write(1);zip.closeEntry();}
        try{ArchiveOperations.extractZip(archive,parent,null);fail("Zip-slip entry was accepted");}catch(IOException expected){assertEquals("Unsafe archive entry path rejected",expected.getMessage());}
        assertFalse(new File(parent.getParentFile(),"escaped.txt").exists());File[] leftovers=parent.listFiles((dir,name)->name.startsWith("malicious"));assertNotNull(leftovers);assertEquals(0,leftovers.length);
    }

    @Test public void rejectsDuplicateNormalizedPaths() throws Exception {
        File parent=temporary.newFolder("duplicate-zip"),archive=new File(parent,"duplicate.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive.toPath()))){zip.putNextEntry(new ZipEntry("folder/"));zip.closeEntry();zip.putNextEntry(new ZipEntry("folder"));zip.write(1);zip.closeEntry();}
        try{ArchiveOperations.extractZip(archive,parent,null);fail("Duplicate normalized paths were accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("duplicate"));}
    }

    @Test public void listZipNeverReturnsMoreThanConfiguredLimit() throws Exception {
        File archive=temporary.newFile("many.zip");try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive.toPath()))){for(int i=0;i<405;i++){zip.putNextEntry(new ZipEntry("entry-"+i));zip.closeEntry();}}
        assertEquals(ArchiveOperations.MAX_LIST_ENTRIES,ArchiveOperations.listZip(archive,1000).size());
    }

    @Test public void cancelledExtractionRemovesPartialDestination() throws Exception {
        File parent=temporary.newFolder("cancel-zip"),archive=new File(parent,"cancel.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive.toPath()))){zip.putNextEntry(new ZipEntry("first.txt"));zip.write(1);zip.closeEntry();zip.putNextEntry(new ZipEntry("second.txt"));zip.write(2);zip.closeEntry();}
        OperationCancellation cancellation=new OperationCancellation();OperationCancellation.Token token=cancellation.begin();
        try{try{ArchiveOperations.extractZip(archive,parent,message->cancellation.cancelActive());fail("Cancelled extraction continued");}catch(IOException expected){assertEquals("Operation cancelled",expected.getMessage());}}
        finally{cancellation.finish(token);}
        File[] leftovers=parent.listFiles((dir,name)->name.startsWith("cancel"));assertNotNull(leftovers);assertEquals(1,leftovers.length);
    }
}
