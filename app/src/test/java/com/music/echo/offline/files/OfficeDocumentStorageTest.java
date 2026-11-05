package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class OfficeDocumentStorageTest {
    @Test public void atomicReplacementWritesLatestDocumentWithoutRecoverySnapshots() throws Exception {
        File root=Files.createTempDirectory("office-store").toFile();
        File target=new File(root,"report.docx");
        OfficeDocumentStorage.atomicWrite(root,target,tmp->Files.write(tmp.toPath(),"first".getBytes(StandardCharsets.UTF_8)));
        OfficeDocumentStorage.atomicWrite(root,target,tmp->Files.write(tmp.toPath(),"second".getBytes(StandardCharsets.UTF_8)));
        assertEquals("second",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
        assertEquals(1,root.listFiles().length);
    }

    @Test public void filenamesCannotEscapeWorkspace() {
        assertEquals("_bad_name_.docx",OfficeDocumentStorage.safeName("../bad/name?.docx"));
        assertEquals("document",OfficeDocumentStorage.safeName("..."));
    }

    @Test public void failedSaveLeavesOriginalUntouched() throws Exception {
        File root=Files.createTempDirectory("office-failed-save").toFile();
        File target=new File(root,"safe.pdf");
        Files.write(target.toPath(),"original".getBytes(StandardCharsets.UTF_8));
        try {
            OfficeDocumentStorage.atomicWrite(root,target,tmp->{
                Files.write(tmp.toPath(),"partial".getBytes(StandardCharsets.UTF_8));
                throw new java.io.IOException("simulated writer failure");
            });
            fail("Expected failed writer");
        } catch(java.io.IOException expected) {
            assertEquals("original",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
        }
    }

    @Test public void emptyDocumentPayloadCanBeWrittenAtomically() throws Exception {
        File root=Files.createTempDirectory("office-empty").toFile();
        File target=new File(root,"empty.pdf");
        OfficeDocumentStorage.atomicWrite(root,target,tmp->Files.write(tmp.toPath(),new byte[0]));
        assertTrue(target.isFile());
        assertEquals(0,target.length());
    }

    @Test public void legacyPrivateDocumentsMigrateWithoutOverwritingExistingFiles() throws Exception {
        File root=Files.createTempDirectory("office-migration").toFile();
        File legacy=new File(new File(root,"toxifiles360"),"documents");
        assertTrue(legacy.mkdirs());
        Files.write(new File(legacy,"report.docx").toPath(),"old document".getBytes(StandardCharsets.UTF_8));
        File current=OfficeDocumentStorage.documents(root);
        assertEquals("old document",new String(Files.readAllBytes(new File(current,"report.docx").toPath()),StandardCharsets.UTF_8));
        assertFalse(new File(legacy,"report.docx").exists());
    }
}
