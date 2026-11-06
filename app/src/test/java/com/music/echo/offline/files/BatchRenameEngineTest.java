package com.music.echo.offline.files;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.Assert.*;

public class BatchRenameEngineTest {
    private File dir() throws Exception { return Files.createTempDirectory("toxifiles-rename-").toFile(); }
    private File file(File dir,String name) throws Exception { File f=new File(dir,name); assertTrue(f.createNewFile()); return f; }
    private BatchRenameEngine.Options options() { return new BatchRenameEngine.Options(); }

    @Test public void appliesPrefixSuffixReplacementCaseAndPreservesExtension() throws Exception {
        File d=dir(), source=file(d,"Summer Trip.JPG"); BatchRenameEngine.Options o=options();
        o.prefix="new_"; o.suffix="_done"; o.find="Trip"; o.replacement="Holiday"; o.capitalization=BatchRenameEngine.CASE_LOWER;
        List<BatchRenameEngine.Item> p=BatchRenameEngine.plan(Arrays.asList(source),o);
        assertTrue(p.get(0).valid()); assertEquals("new_summer holiday_done.JPG",p.get(0).target.getName());
    }



    @Test public void emptyReplacementRemovesMatchedText() throws Exception {
        File d=dir(), source=file(d,"draft copy.txt"); BatchRenameEngine.Options o=options(); o.find=" copy";
        List<BatchRenameEngine.Item> p=BatchRenameEngine.plan(Arrays.asList(source),o);
        assertTrue(p.get(0).valid()); assertEquals("draft.txt",p.get(0).target.getName());
    }

    @Test public void permitsCapitalizationOnlyRename() throws Exception {
        File d=dir(), source=file(d,"report.txt"); BatchRenameEngine.Options o=options(); o.capitalization=BatchRenameEngine.CASE_UPPER;
        List<BatchRenameEngine.Item> p=BatchRenameEngine.plan(Arrays.asList(source),o);
        assertTrue(p.get(0).valid()); assertEquals("REPORT.txt",p.get(0).target.getName());
    }

    @Test public void supportsSequentialNumberingAndPadding() throws Exception {
        File d=dir(), a=file(d,"a.txt"), b=file(d,"b.txt"); BatchRenameEngine.Options o=options();
        o.prefix="img_"; o.numbering=true; o.start=7; o.increment=3; o.padding=3;
        List<BatchRenameEngine.Item> p=BatchRenameEngine.plan(Arrays.asList(a,b),o);
        assertEquals("img_a 007.txt",p.get(0).target.getName()); assertEquals("img_b 010.txt",p.get(1).target.getName());
    }

    @Test public void rejectsExistingAndDuplicateDestinations() throws Exception {
        File d=dir(), a=file(d,"a.txt"), b=file(d,"b.txt"), occupied=file(d,"taken.txt");
        BatchRenameEngine.Options o=options(); o.find="a"; o.replacement="taken";
        assertEquals("Destination already exists",BatchRenameEngine.plan(Arrays.asList(a),o).get(0).problem);
        File duplicateDir=new File(d,"duplicates"); assertTrue(duplicateDir.mkdir());
        File x=file(duplicateDir,"afoo.txt"), y=file(duplicateDir,"fooa.txt"); o=options(); o.find="foo";
        List<BatchRenameEngine.Item> p=BatchRenameEngine.plan(Arrays.asList(x,y),o);
        assertTrue(p.stream().anyMatch(item -> "Duplicate destination in this batch".equals(item.problem)));
        assertTrue(occupied.exists());
    }

    @Test public void rejectsInvalidNamesAndInvalidNumberingSettings() throws Exception {
        File d=dir(), a=file(d,"a.txt"); BatchRenameEngine.Options o=options(); o.prefix="bad/";
        assertNotNull(BatchRenameEngine.plan(Arrays.asList(a),o).get(0).problem);
        o.prefix=""; o.numbering=true; o.increment=0;
        assertNotNull(BatchRenameEngine.plan(Arrays.asList(a),o).get(0).problem);
    }

    @Test public void filenameValidationRejectsTraversalSeparatorsReservedAndControlCharacters() {
        assertNotNull(BatchRenameEngine.validateName("../escape"));
        assertNotNull(BatchRenameEngine.validateName("folder\\escape"));
        assertNotNull(BatchRenameEngine.validateName("."));
        assertNotNull(BatchRenameEngine.validateName(".."));
        assertNotNull(BatchRenameEngine.validateName("bad\u0001name"));
        assertNotNull(BatchRenameEngine.validateName("a".repeat(256)));
        assertNull(BatchRenameEngine.validateName("Holiday photo 2026.jpg"));
        assertNull(BatchRenameEngine.validateName("旅行の写真.jpg"));
    }

    @Test public void executeAndUndoRestoreOriginalNamesWithoutOverwrite() throws Exception {
        File d=dir(), a=file(d,"a.txt"), b=file(d,"b.txt"); BatchRenameEngine.Options o=options(); o.prefix="new_";
        BatchRenameEngine.Result done=BatchRenameEngine.execute(BatchRenameEngine.plan(Arrays.asList(a,b),o));
        assertTrue(done.success()); assertFalse(a.exists()); assertTrue(new File(d,"new_a.txt").exists());
        BatchRenameEngine.Result undone=BatchRenameEngine.undo(done);
        assertTrue(undone.errors.toString(),undone.errors.isEmpty()); assertTrue(a.exists()); assertTrue(b.exists());
    }

    @Test public void rollsBackEarlierItemsWhenDestinationAppearsMidBatch() throws Exception {
        File d=dir(), a=file(d,"a.txt"), b=file(d,"b.txt"); BatchRenameEngine.Options o=options(); o.prefix="new_";
        List<BatchRenameEngine.Item> plan=BatchRenameEngine.plan(Arrays.asList(a,b),o);
        assertTrue(file(d,"new_b.txt").exists());
        BatchRenameEngine.Result result=BatchRenameEngine.execute(plan);
        assertFalse(result.errors.isEmpty()); assertTrue(a.exists()); assertTrue(b.exists()); assertFalse(new File(d,"new_a.txt").exists());
    }


    @Test public void permissionFailureLeavesOriginalFileUntouched() throws Exception {
        File d=dir(), source=file(d,"locked.txt"); BatchRenameEngine.Options o=options(); o.prefix="renamed_";
        List<BatchRenameEngine.Item> plan=BatchRenameEngine.plan(Arrays.asList(source),o);
        Set<PosixFilePermission> original=Files.getPosixFilePermissions(d.toPath());
        try {
            Set<PosixFilePermission> locked=EnumSet.copyOf(original);
            locked.remove(PosixFilePermission.OWNER_WRITE); locked.remove(PosixFilePermission.GROUP_WRITE); locked.remove(PosixFilePermission.OTHERS_WRITE);
            Files.setPosixFilePermissions(d.toPath(),locked);
            BatchRenameEngine.Result result=BatchRenameEngine.execute(plan);
            assertFalse(result.errors.isEmpty()); assertTrue(source.exists()); assertFalse(new File(d,"renamed_locked.txt").exists());
        } finally { Files.setPosixFilePermissions(d.toPath(),original); }
    }


    @Test public void rollbackRetainsRecoveryRecordWhenBothPathsExist() throws Exception {
        File d=dir(), source=file(d,"original.txt"), target=file(d,"renamed.txt");
        BatchRenameEngine.Result result=new BatchRenameEngine.Result();
        result.completed.add(new BatchRenameEngine.Item(source,target,null));
        java.lang.reflect.Method rollback=BatchRenameEngine.class.getDeclaredMethod("rollback",BatchRenameEngine.Result.class);
        rollback.setAccessible(true); rollback.invoke(null,result);
        assertEquals(1,result.completed.size());
        assertFalse(result.errors.isEmpty());
        assertTrue(source.exists()); assertTrue(target.exists());
    }

    @Test public void reportsMissingSourcesAndHandlesLargeBatches() throws Exception {
        File d=dir(), missing=new File(d,"missing.txt");
        assertFalse(BatchRenameEngine.plan(Arrays.asList(missing),options()).get(0).valid());
        List<File> files=new ArrayList<>(); for(int i=0;i<250;i++) files.add(file(d,"item_"+i+".dat"));
        BatchRenameEngine.Options o=options(); o.suffix="_renamed";
        List<BatchRenameEngine.Item> plan=BatchRenameEngine.plan(files,o);
        assertEquals(250,plan.size()); assertTrue(plan.stream().allMatch(BatchRenameEngine.Item::valid));
        BatchRenameEngine.Result result=BatchRenameEngine.execute(plan); assertTrue(result.errors.toString(),result.success());
        assertEquals(250,result.completed.size());
    }

    @Test public void resolveChildRejectsTraversalAndSymlinkEscapes() throws Exception {
        File root = dir();
        File parent = new File(root, "parent");
        assertTrue(parent.mkdir());
        try {
            BatchRenameEngine.resolveChild(parent, "../outside.txt");
            fail("Traversal name must be rejected");
        } catch (java.io.IOException expected) { }
        File outside = file(root, "outside.txt");
        File link = new File(parent, "escape.txt");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            return;
        }
        try {
            BatchRenameEngine.resolveChild(parent, "escape.txt");
            fail("A symlink target outside the selected parent must be rejected");
        } catch (java.io.IOException expected) { }
        File safe = BatchRenameEngine.resolveChild(parent, "safe name.txt");
        assertEquals(parent.getCanonicalFile(), safe.getParentFile().getCanonicalFile());
    }
}
