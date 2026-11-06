package com.music.echo.offline.files;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class DeveloperStudioCoreTest {
    @Test public void detectsPngSignatureAndExtensionMismatch() {
        byte[] png={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};
        String result=DeveloperStudioCore.signature(png,"wrong.jpg");
        assertTrue(result.contains("PNG image"));
        assertTrue(result.contains("Extension mismatch: YES"));
    }
    @Test public void signatureUsesBigEndianPngDimensions() {
        byte[] b=new byte[24];byte[] sig={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};System.arraycopy(sig,0,b,0,8);b[19]=2;b[23]=3;
        assertTrue(DeveloperStudioCore.signature(b,"image.png").contains("2 × 3"));
    }
    @Test public void analyzesPngHeaderDimensions() {
        byte[] b=new byte[26];byte[] sig={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};System.arraycopy(sig,0,b,0,8);
        b[19]=2;b[23]=3;b[24]=8;b[25]=6;
        String result=DeveloperStudioCore.binaryStructure(b,"image.png");
        assertTrue(result.contains("Width: 2"));assertTrue(result.contains("Height: 3"));
    }
    @Test public void diffReportsAddedAndRemovedLines() {
        String result=DeveloperStudioCore.diff("one\ntwo\nthree","one\nchanged\nthree");
        assertTrue(result.contains("Added: 1"));assertTrue(result.contains("Removed: 1"));
        assertTrue(result.contains("+ changed"));assertTrue(result.contains("- two"));
    }
    @Test public void regexReportsGroupsPositionsAndReplacement() {
        String result=DeveloperStudioCore.regex("(\\w+)=(\\d+)","id=42", "$1:$2");
        assertTrue(result.contains("Matches: 1"));assertTrue(result.contains("group 1: id"));assertTrue(result.contains("id:42"));
    }
    @Test public void regexRejectsNestedQuantifiers() {
        try { DeveloperStudioCore.regex("(a+)+$","aaaaaaaaaaaaaaaa!",null); fail("unsafe pattern must be rejected"); }
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("catastrophic"));}
    }
    @Test public void validatorFindsUnbalancedDelimitersButIgnoresStringsAndComments() {
        assertTrue(DeveloperStudioCore.validateSource("class A { String s=\"}\"; /* { */ }").contains("passed"));
        assertTrue(DeveloperStudioCore.validateSource("class A { void x() {").contains("unclosed"));
    }
    @Test public void formatterProducesIndentedBlocks() {
        String formatted=DeveloperStudioCore.formatSource("class A{void f(){return;}}");
        assertTrue(formatted.contains("    void f()"));assertTrue(formatted.contains("        return;"));
    }
    @Test public void symbolScannerFindsTypesAndMethods() {
        String result=DeveloperStudioCore.symbols("package demo;\npublic class Sample {\n public void run() {}\n}");
        assertTrue(result.contains("Sample"));assertTrue(result.contains("run"));
    }
    @Test public void dependencyInspectorExtractsGradleAndImports() {
        String result=DeveloperStudioCore.dependencies("import java.util.List;\ndependencies { implementation(\"org.example:core:1.0\") }","build.gradle");
        assertTrue(result.contains("java.util.List"));assertTrue(result.contains("org.example:core:1.0"));
    }
    @Test public void relationshipExplorerResolvesLocalImports() {
        Map<String,String> files=new LinkedHashMap<>();files.put("src/Main.java","import demo.Helper;\nclass Main {}");files.put("src/demo/Helper.java","package demo; class Helper {}");
        String result=DeveloperStudioCore.fileRelationships(files);
        assertTrue(result.contains("src/Main.java"));assertTrue(result.contains("src/demo/Helper.java"));
    }
    @Test public void utf8DecoderRejectsMalformedBytes() {
        try { DeveloperStudioCore.decodeUtf8(new byte[]{(byte)0xc3,0x28}); fail("malformed UTF-8 must fail"); }
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("valid UTF-8"));}
    }
}
