package com.music.echo.offline.files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
public class DuplicateFinderTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();
    private DuplicateFinder.Snapshot snapshot(File file){return new DuplicateFinder.Snapshot(file,false,file.lastModified(),file.length());}
    @Test public void groupsExactContentButNotDifferentContentOfTheSameSize()throws Exception{
        File first=temporary.newFile("first.bin"),second=temporary.newFile("second.bin"),different=temporary.newFile("different.bin");byte[] shared={0,1,2,3,4,5};
        Files.write(first.toPath(),shared);Files.write(second.toPath(),shared);Files.write(different.toPath(),new byte[]{5,4,3,2,1,0});
        List<DuplicateFinder.Group> groups=DuplicateFinder.find(Arrays.asList(snapshot(first),snapshot(second),snapshot(different)));
        assertEquals(1,groups.size());assertEquals(2,groups.get(0).files.size());assertEquals(shared.length,groups.get(0).size);
    }
    @Test public void includesEmptyFilesAsExactDuplicates()throws Exception{
        File first=temporary.newFile("empty-a.bin"),second=temporary.newFile("empty-b.bin");List<DuplicateFinder.Group> groups=DuplicateFinder.find(Arrays.asList(snapshot(first),snapshot(second)));
        assertEquals(1,groups.size());assertEquals(0L,groups.get(0).size);assertEquals(2,groups.get(0).files.size());
    }
    @Test public void reportsDisappearingFilesInsteadOfReturningPartialResults()throws Exception{
        File first=temporary.newFile("first.bin"),second=temporary.newFile("second.bin");Files.write(first.toPath(),new byte[]{1,2,3});Files.write(second.toPath(),new byte[]{1,2,3});
        DuplicateFinder.Snapshot a=snapshot(first),b=snapshot(second);assertTrue(first.delete());
        try{DuplicateFinder.find(Arrays.asList(a,b));fail("A missing indexed file must not produce partial results");}catch(IOException expected){assertTrue(expected.getMessage().contains("first.bin"));}
    }
    @Test public void honorsScopedOperationCancellation()throws Exception{
        File file=temporary.newFile("cancelled.bin");OperationCancellation cancellation=new OperationCancellation();OperationCancellation.Token token=cancellation.begin();
        try{cancellation.cancelActive();try{DuplicateFinder.find(Arrays.asList(snapshot(file)));fail("Cancelled scan returned results");}catch(IOException expected){assertEquals("Operation cancelled",expected.getMessage());}}
        finally{cancellation.finish(token);}
    }
}
