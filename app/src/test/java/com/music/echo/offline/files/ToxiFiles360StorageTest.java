package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class ToxiFiles360StorageTest {
    @Test public void atomicReplacementKeepsRecoverablePreviousVersion() throws Exception {
        File root=Files.createTempDirectory("tox360-store").toFile();
        File target=new File(root,"note.txt");
        ToxiFiles360Storage.atomicWrite(root,target,tmp->Files.write(tmp.toPath(),"first".getBytes(StandardCharsets.UTF_8)));
        ToxiFiles360Storage.atomicWrite(root,target,tmp->Files.write(tmp.toPath(),"second".getBytes(StandardCharsets.UTF_8)));
        assertEquals("second",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
        List<File> history=ToxiFiles360Storage.history(root,target);
        assertEquals(1,history.size());
        assertEquals("first",new String(Files.readAllBytes(history.get(0).toPath()),StandardCharsets.UTF_8));
        ToxiFiles360Storage.restore(root,history.get(0),target);
        assertEquals("first",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
    }

    @Test public void filenamesCannotEscapeWorkspace() {
        assertEquals("_bad_name_.txt",ToxiFiles360Storage.safeName("../bad/name?.txt"));
        assertEquals("document",ToxiFiles360Storage.safeName("..."));
    }

    @Test public void failedSaveLeavesOriginalUntouched() throws Exception {
        File root=Files.createTempDirectory("tox360-failed-save").toFile();
        File target=new File(root,"safe.txt");
        Files.write(target.toPath(),"original".getBytes(StandardCharsets.UTF_8));
        try {
            ToxiFiles360Storage.atomicWrite(root,target,tmp->{
                Files.write(tmp.toPath(),"partial".getBytes(StandardCharsets.UTF_8));
                throw new java.io.IOException("simulated writer failure");
            });
            fail("Expected failed writer");
        } catch(java.io.IOException expected) {
            assertEquals("original",new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
        }
    }

    @Test public void emptyNotesCanBeSavedAtomically() throws Exception {
        File root=Files.createTempDirectory("tox360-empty").toFile();
        File target=new File(root,"empty.txt");
        ToxiFiles360Storage.atomicWrite(root,target,tmp->Files.write(tmp.toPath(),new byte[0]));
        assertTrue(target.isFile());
        assertEquals(0,target.length());
    }
}
