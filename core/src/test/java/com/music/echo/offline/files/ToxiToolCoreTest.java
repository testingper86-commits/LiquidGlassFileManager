package com.music.echo.offline.files;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ToxiToolCoreTest {
    private File temp() throws IOException { return Files.createTempDirectory("toxifiles-tools-test").toFile(); }
    private void remove(File f) { if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)remove(k);}f.delete(); }
    @Test public void hashesUseStrongDigestsAndExposeCrc32Separately() throws Exception {
        Map<String,String> h=ToxiToolCore.hashes(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",h.get("SHA-256"));
        assertEquals("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",h.get("SHA-512"));
        assertEquals("352441c2",h.get("CRC32"));
        assertFalse(h.containsKey("MD5"));
        assertFalse(h.containsKey("MD5 (legacy)"));
        assertFalse(h.containsKey("SHA-1"));
        assertFalse(h.containsKey("SHA-1 (legacy)"));
    }
    @Test public void hexDumpCanBeParsedBack() {
        byte[] source=new byte[]{0,1,15,16,31,32,65,90,(byte)255};
        assertArrayEquals(source,ToxiToolCore.parseHex(ToxiToolCore.formatHex(source,0,source.length)));
    }
    @Test public void hexParserSupportsOffsetsPrefixesAndInlineComments() {
        assertArrayEquals(new byte[]{(byte)0xab,0x10,(byte)0xcd,(byte)0xef},ToxiToolCore.parseHex("0xAB, 10 # comment\r\n00000002  CD EF // comment"));
    }
    @Test public void hexRejectsIncompleteAndNonHexInput() {
        try{ToxiToolCore.parseHex("A");fail("odd nibble accepted");}catch(IllegalArgumentException expected){}
        try{ToxiToolCore.parseHex("GG");fail("non-hex accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void charsetConversionIsStrictAndRoundTrips() throws Exception {
        String text="ToxiFiles café";byte[] b=ToxiToolCore.encode(text,"UTF-16LE");assertEquals(text,ToxiToolCore.decode(b,"UTF-16LE"));
        try{ToxiToolCore.decode(new byte[]{(byte)0xc3,0x28},"UTF-8");fail("malformed UTF-8 accepted");}catch(java.nio.charset.CharacterCodingException expected){}
    }
    @Test public void boundedReadRejectsOversizedInput() throws Exception {
        try{ToxiToolCore.readBounded(new ByteArrayInputStream(new byte[9]),8);fail("limit ignored");}catch(IOException expected){}
    }
    @Test public void atomicWriteReplacesTargetCompletely() throws Exception {
        File d=temp(),f=new File(d,"value.txt");Files.write(f.toPath(),"old".getBytes(StandardCharsets.UTF_8));ToxiToolCore.atomicWrite(f,"new-value".getBytes(StandardCharsets.UTF_8));assertEquals("new-value",new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));remove(d);
    }
    @Test public void folderComparisonDetectsMissingAndContentDifferences() throws Exception {
        File d=temp(),l=new File(d,"left"),r=new File(d,"right");l.mkdir();r.mkdir();Files.write(new File(l,"same.txt").toPath(),new byte[]{1});Files.write(new File(r,"same.txt").toPath(),new byte[]{2});Files.write(new File(l,"left.txt").toPath(),new byte[]{3});
        List<String> result=ToxiToolCore.compareFolders(l,r);assertTrue(result.stream().anyMatch(x->x.startsWith("CONTENT DIFF")));assertTrue(result.stream().anyMatch(x->x.startsWith("LEFT ONLY")));remove(d);
    }
    @Test public void zipRewriteRenamesDeletesAndAddsEntries() throws Exception {
        File d=temp(),zip=new File(d,"source.zip"),added=new File(d,"added.txt");Files.write(added.toPath(),"new".getBytes(StandardCharsets.UTF_8));
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){out.putNextEntry(new ZipEntry("old.txt"));out.write("old".getBytes(StandardCharsets.UTF_8));out.closeEntry();out.putNextEntry(new ZipEntry("remove.txt"));out.write(1);out.closeEntry();}
        Map<String,String> renames=new HashMap<>();renames.put("old.txt","renamed.txt");Set<String> deletes=new HashSet<>();deletes.add("remove.txt");Map<String,File> additions=new HashMap<>();additions.put("added.txt",added);
        ToxiToolCore.rewriteZip(zip,renames,deletes,additions);try(ZipFile z=new ZipFile(zip)){assertNotNull(z.getEntry("renamed.txt"));assertNotNull(z.getEntry("added.txt"));assertNull(z.getEntry("old.txt"));assertNull(z.getEntry("remove.txt"));}remove(d);
    }
    @Test public void zipRewritePreservesDirectoryMarkersWhenRenaming() throws Exception {
        File d=temp(),zip=new File(d,"source.zip");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){out.putNextEntry(new ZipEntry("folder/"));out.closeEntry();}
        Map<String,String> renames=new HashMap<>();renames.put("folder/","renamed");
        ToxiToolCore.rewriteZip(zip,renames,Collections.emptySet(),Collections.emptyMap());
        try(ZipFile z=new ZipFile(zip)){ZipEntry directory=z.getEntry("renamed/");assertNotNull(directory);assertTrue(directory.isDirectory());}
        remove(d);
    }
    @Test public void zipPathTraversalIsRejected() throws Exception {
        try{ToxiToolCore.validateZipName("../escape.txt");fail("path traversal accepted");}catch(IOException expected){}
        try{ToxiToolCore.validateZipName("/absolute.txt");fail("absolute path accepted");}catch(IOException expected){}
        try{ToxiToolCore.validateZipName("C:/absolute.txt");fail("drive-qualified path accepted");}catch(IOException expected){}
    }
}
