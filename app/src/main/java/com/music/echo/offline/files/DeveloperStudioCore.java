package com.music.echo.offline.files;

import java.io.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.zip.CRC32;

/** Bounded, deterministic, offline analysis primitives for Developer Studio. */
final class DeveloperStudioCore {
    static final int MAX_TEXT_CHARS = 2_000_000;
    static final int MAX_DIFF_LINES = 2_000;
    static final int MAX_RELATION_FILES = 10_000;
    private DeveloperStudioCore() { }

    static String decodeUtf8(byte[] data) throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(data)).toString(); }
        catch (CharacterCodingException e) { throw new IOException("File is not valid UTF-8; choose a supported text encoding or inspect it as binary.", e); }
    }
    static String sha256(byte[] data) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(data)); }
    static String hex(byte[] data) { StringBuilder s=new StringBuilder(data.length*2); for(byte b:data)s.append(String.format(Locale.ROOT,"%02x",b&255)); return s.toString(); }
    static String signature(byte[] b, String filename) {
        String ext=""; if(filename!=null){int p=filename.lastIndexOf('.');if(p>=0)ext=filename.substring(p+1).toLowerCase(Locale.ROOT);}
        String type="Unknown / no recognized magic signature",mime="application/octet-stream",detail="No known signature matched the sampled header."; int off=-1;
        if(starts(b,0x50,0x4b,0x03,0x04)||starts(b,0x50,0x4b,0x05,0x06)||starts(b,0x50,0x4b,0x07,0x08)){type="ZIP container";mime="application/zip";detail="ZIP local-file, empty-archive or spanning signature.";off=0;}
        else if(starts(b,0x7f,0x45,0x4c,0x46)){type="ELF executable / shared object";mime="application/x-elf";detail="ELF header; class="+(b.length>4?(b[4]==1?"32-bit":b[4]==2?"64-bit":"unknown"):"unknown")+", byte order="+(b.length>5?(b[5]==1?"little-endian":b[5]==2?"big-endian":"unknown"):"unknown");off=0;}
        else if(starts(b,0x4d,0x5a)){type="DOS / Windows PE executable";mime="application/vnd.microsoft.portable-executable";detail="MZ DOS header; PE header offset is stored at 0x3c.";off=0;}
        else if(starts(b,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)){type="PNG image";mime="image/png";detail=b.length>=24?"PNG IHDR dimensions: "+u32(b,16)+" × "+u32(b,20):"PNG signature; incomplete IHDR.";off=0;}
        else if(starts(b,0xff,0xd8,0xff)){type="JPEG image";mime="image/jpeg";detail="JPEG start-of-image marker.";off=0;}
        else if(starts(b,0x47,0x49,0x46,0x38)){type="GIF image";mime="image/gif";detail="GIF87a/GIF89a header.";off=0;}
        else if(starts(b,0x25,0x50,0x44,0x46)){type="PDF document";mime="application/pdf";detail="PDF header.";off=0;}
        else if(starts(b,0x53,0x51,0x4c,0x69,0x74,0x65,0x20,0x66,0x6f,0x72,0x6d,0x61,0x74,0x20,0x33,0x00)){type="SQLite 3 database";mime="application/vnd.sqlite3";detail="SQLite database header.";off=0;}
        else if(starts(b,0x42,0x4d)){type="BMP bitmap";mime="image/bmp";detail="Windows bitmap header.";off=0;}
        else if(starts(b,0x52,0x49,0x46,0x46)&&b.length>=12&&ascii(b,8,4).equals("WAVE")){type="WAV audio";mime="audio/wav";detail="RIFF/WAVE container.";off=0;}
        else if(starts(b,0x49,0x44,0x33)){type="MP3 audio with ID3 tag";mime="audio/mpeg";detail="ID3 metadata header.";off=0;}
        else if(starts(b,0x4f,0x67,0x67,0x53)){type="Ogg container";mime="audio/ogg";detail="OggS page capture pattern.";off=0;}
        else if(starts(b,0x1f,0x8b)){type="GZIP archive";mime="application/gzip";detail="GZIP stream header.";off=0;}
        else if(starts(b,0x37,0x7a,0xbc,0xaf,0x27,0x1c)){type="7-Zip archive";mime="application/x-7z-compressed";detail="7z archive signature.";off=0;}
        else if(starts(b,0x52,0x61,0x72,0x21,0x1a,0x07)){type="RAR archive";mime="application/vnd.rar";detail="RAR archive signature.";off=0;}
        else if(starts(b,0xca,0xfe,0xba,0xbe)){type="Java class file";mime="application/java-vm";detail="Java class-file magic.";off=0;}
        else if(starts(b,0x00,0x61,0x73,0x6d)){type="WebAssembly module";mime="application/wasm";detail="WebAssembly binary header.";off=0;}
        else if(startsAt(b,4,0x66,0x74,0x79,0x70)){type="ISO base media (ftyp at offset 4)";mime="video/mp4";detail="MP4/HEIF-family file-type box.";off=4;}
        else if(starts(b,0x00,0x00,0x01,0x00)){type="Windows icon resource";mime="image/x-icon";detail="ICO header.";off=0;}
        else if(starts(b,0x00,0x00,0x02,0x00)){type="Windows cursor resource";mime="image/x-icon";detail="CUR header.";off=0;}
        boolean mismatch=(!ext.isEmpty()&&((ext.equals("apk")&&!type.equals("ZIP container"))||(ext.equals("pdf")&&!type.equals("PDF document"))||(ext.equals("png")&&!type.equals("PNG image"))||(ext.equals("jpg")||ext.equals("jpeg"))&&!type.equals("JPEG image")||(ext.equals("sqlite")||ext.equals("db")||ext.equals("sqlite3"))&&!type.equals("SQLite 3 database")));
        return "Detected type: "+type+"\nMIME: "+mime+"\nSignature offset: "+(off<0?"none":String.format(Locale.ROOT,"0x%X",off))+"\nExtension: "+(ext.isEmpty()?"(none)":ext)+"\nExtension mismatch: "+(mismatch?"YES":"not detected")+"\nDetails: "+detail+"\n\nHeader (hex):\n"+hex(Arrays.copyOf(b,Math.min(64,b.length)));
    }
    static String binaryStructure(byte[] b,String name) {
        String n=name==null?"":name.toLowerCase(Locale.ROOT);StringBuilder s=new StringBuilder("File: ").append(name).append("\nBytes sampled: ").append(b.length).append("\n\n");
        if(starts(b,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)&&b.length>=26){s.append("PNG STRUCTURE\nSignature: 0x00000000 (8 bytes)\nIHDR length: ").append(u32be(b,8)).append("\nChunk type: ").append(ascii(b,12,4)).append("\nWidth: ").append(u32be(b,16)).append("\nHeight: ").append(u32be(b,20)).append("\nBit depth: ").append(b[24]&255).append("\nColor type: ").append(b[25]&255).append("\n");}
        else if(starts(b,0x4d,0x5a)&&b.length>=64){long p=u32(b,0x3c);s.append("PE/DOS STRUCTURE\nDOS magic: MZ\nPE header offset: 0x").append(Long.toHexString(p)).append("\n");if(p>=0&&p+24<=b.length&&b[(int)p]=='P'&&b[(int)p+1]=='E'){int q=(int)p+4;s.append("PE signature: PE\\0\\0\nMachine: 0x").append(Integer.toHexString(u16(b,q))).append("\nSections: ").append(u16(b,q+2)).append("\nOptional header size: ").append(u16(b,q+16)).append("\n");}else s.append("PE header is outside the sampled range or invalid.\n");}
        else if(starts(b,0x7f,0x45,0x4c,0x46)&&b.length>=20){s.append("ELF STRUCTURE\nClass: ").append(b[4]==1?"ELF32":b[4]==2?"ELF64":"unknown").append("\nEndian: ").append(b[5]==1?"little":b[5]==2?"big":"unknown").append("\nVersion: ").append(b[6]&255).append("\nOS ABI: ").append(b[7]&255).append("\nType: 0x").append(Integer.toHexString(u16(b,16))).append("\nMachine: 0x").append(Integer.toHexString(u16(b,18))).append("\n");}
        else if(starts(b,0x52,0x49,0x46,0x46)&&b.length>=12){s.append("RIFF STRUCTURE\nContainer: ").append(ascii(b,8,4)).append("\nDeclared RIFF size: ").append(u32(b,4)+8).append("\n");if(ascii(b,8,4).equals("WAVE")&&b.length>=44){s.append("WAVE format tag: 0x").append(Integer.toHexString(u16(b,20))).append("\nChannels: ").append(u16(b,22)).append("\nSample rate: ").append(u32(b,24)).append("\nByte rate: ").append(u32(b,28)).append("\nBits per sample: ").append(u16(b,34)).append("\n");}}
        else if(starts(b,0x42,0x4d)&&b.length>=26){s.append("BMP STRUCTURE\nDeclared size: ").append(u32(b,2)).append("\nPixel offset: ").append(u32(b,10)).append("\nDIB header size: ").append(u32(b,14)).append("\nWidth: ").append(u32(b,18)).append("\nHeight: ").append(u32(b,22)).append("\n");}
        else if(starts(b,0x25,0x50,0x44,0x46)){s.append("PDF STRUCTURE\nHeader: ").append(ascii(b,0,Math.min(8,b.length))).append("\nVersion bytes: ").append(ascii(b,5,Math.min(3,Math.max(0,b.length-5)))).append("\n");}
        else if(starts(b,0x50,0x4b,0x03,0x04)&&b.length>=30){s.append("ZIP LOCAL FILE HEADER\nVersion needed: ").append(u16(b,4)).append("\nFlags: 0x").append(Integer.toHexString(u16(b,6))).append("\nCompression: ").append(u16(b,8)).append("\nCRC32: 0x").append(Long.toHexString(u32(b,14))).append("\nCompressed size: ").append(u32(b,18)).append("\nUncompressed size: ").append(u32(b,22)).append("\nFilename length: ").append(u16(b,26)).append("\nExtra length: ").append(u16(b,28)).append("\n");}
        else if(starts(b,0x53,0x51,0x4c,0x69,0x74,0x65,0x20,0x66,0x6f,0x72,0x6d,0x61,0x74,0x20,0x33,0x00)&&b.length>=100){s.append("SQLITE HEADER\nPage size: ").append(u16be(b,16)==1?65536:u16be(b,16)).append("\nWrite version: ").append(b[18]&255).append("\nRead version: ").append(b[19]&255).append("\nReserved bytes: ").append(b[20]&255).append("\nPayload fractions: ").append(b[21]&255).append('/').append(b[22]&255).append('/').append(b[23]&255).append("\nSchema format: ").append(u32be(b,44)).append("\nText encoding: ").append(u32be(b,56)).append("\n");}
        else if(n.endsWith(".dex")&&b.length>=112&&starts(b,0x64,0x65,0x78,0x0a)){s.append("DEX HEADER\nMagic: ").append(ascii(b,0,8)).append("\nChecksum: 0x").append(Long.toHexString(u32(b,8))).append("\nFile size: ").append(u32(b,32)).append("\nHeader size: ").append(u32(b,36)).append("\nEndian tag: 0x").append(Long.toHexString(u32(b,40))).append("\nString IDs: size=").append(u32(b,56)).append(", offset=").append(u32(b,60)).append("\nClass defs: size=").append(u32(b,96)).append(", offset=").append(u32(b,100)).append("\n");}
        else s.append("No supported structured header was recognized. Use Hex Editor for raw byte inspection.\n");
        s.append("\nHeader bytes: ").append(hex(Arrays.copyOf(b,Math.min(96,b.length))));return s.toString();
    }
    static String regex(String pattern,String sample,String replacement) {
        if(pattern==null||pattern.length()>4096)throw new IllegalArgumentException("Pattern is empty or exceeds 4096 characters");if(sample==null||sample.length()>200_000)throw new IllegalArgumentException("Regex samples are limited to 200,000 characters to keep matching responsive.");if(unsafeRegex(pattern))throw new IllegalArgumentException("Potentially catastrophic nested quantifiers are blocked for responsiveness.");
        Pattern p=Pattern.compile(pattern,Pattern.MULTILINE);Matcher m=p.matcher(sample);StringBuilder out=new StringBuilder();int count=0;
        while(m.find()){if(++count>10000)throw new IllegalArgumentException("More than 10,000 matches; narrow the expression");out.append("Match ").append(count).append(" [").append(m.start()).append(".. ").append(m.end()).append("] line ").append(1+lineAt(sample,m.start())).append(": ").append(m.group()).append('\n');for(int g=1;g<=m.groupCount();g++)out.append("  group ").append(g).append(": ").append(m.group(g)).append('\n');}
        if(count==0)out.append("No matches.");if(replacement!=null)out.append("\nReplacement preview:\n").append(m.replaceAll(replacement));return "Matches: "+count+"\n\n"+out;
    }
    private static boolean unsafeRegex(String p){return Pattern.compile("\\([^)]*[+*][^)]*\\)[+*{]").matcher(p).find()||Pattern.compile("\\([^)]*\\|[^)]*\\)[+*{]").matcher(p).find()||Pattern.compile("(?:\\.\\*|\\.\\+)[+*{]").matcher(p).find();}
    static int lineAt(String s,int offset){int line=0;for(int i=0;i<Math.min(offset,s.length());i++)if(s.charAt(i)=='\n')line++;return line;}
    static String symbols(String src) {
        if(src.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("Source exceeds 2 MB");StringBuilder o=new StringBuilder();String[] lines=src.split("\\n",-1);
        Pattern p=Pattern.compile("^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|override|open|internal|export|async|default|const|let|var)\\s+)*(?:class|interface|enum|record|object|fun|function|def|struct|trait|type)\\s+([A-Za-z_$][\\w$]*)|^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|override|internal|async)\\s+)*(?:[\\w$<>?,.\\[\\]]+\\s+)+([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?:\\{|;|$)");
        int found=0;for(int i=0;i<lines.length;i++){Matcher m=p.matcher(lines[i]);if(m.find()){String name=m.group(1)!=null?m.group(1):m.group(2);o.append(String.format(Locale.ROOT,"L%-5d %s  %s%n",i+1,m.group(1)!=null?"TYPE":"CALLABLE",name));found++;}}
        Matcher pkg=Pattern.compile("(?m)^\\s*(?:package|namespace)\\s+([\\w.]+)").matcher(src);if(pkg.find())o.insert(0,"Package / namespace: "+pkg.group(1)+"\n");return found==0?o.append("No class or callable declarations recognized by the built-in symbol scanner.\n").toString():"Symbols found: "+found+"\n"+o;
    }
    static String dependencies(String src,String filename) {
        StringBuilder out=new StringBuilder("Dependency references in ").append(filename).append("\n\n");Set<String> refs=new TreeSet<>();Matcher m=Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$*][\\w$*]*)+)").matcher(src);while(m.find())refs.add("import  "+m.group(1));
        Matcher req=Pattern.compile("(?m)(?:implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)").matcher(src);while(req.find())refs.add("Gradle  "+req.group(1));
        Matcher py=Pattern.compile("(?m)^\\s*(?:from\\s+([\\w.]+)\\s+import|import\\s+([\\w.]+))").matcher(src);while(py.find())refs.add("Python  "+(py.group(1)!=null?py.group(1):py.group(2)));
        Matcher node=Pattern.compile("(?:require\\s*\\(\\s*|from\\s+)[\"']([^\"']+)[\"']").matcher(src);while(node.find())refs.add("Module  "+node.group(1));
        if(refs.isEmpty())out.append("No dependency declarations recognized.\n");else for(String r:refs)out.append(r).append('\n');out.append("\nResolution: declarations are extracted locally. External package versions and transitive dependency graphs require the project's lockfiles/build metadata and are not guessed.");return out.toString();
    }
    static String formatSource(String src) {
        if(src.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("Source exceeds 2 MB");StringBuilder out=new StringBuilder();int depth=0;boolean string=false,escape=false,lineComment=false,blockComment=false;char quote=0;boolean lineStart=true;int spaces=0;
        for(int i=0;i<src.length();i++){char c=src.charAt(i),next=i+1<src.length()?src.charAt(i+1):0;
            if(lineComment){if(c=='\n'){lineComment=false;out.append('\n');lineStart=true;}else out.append(c);continue;}
            if(blockComment){out.append(c);if(c=='*'&&next=='/'){out.append('/');i++;blockComment=false;}continue;}
            if(string){out.append(c);if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)string=false;continue;}
            if(c=='/'&&next=='/'){lineComment=true;if(lineStart){indent(out,depth);lineStart=false;}out.append("//");i++;continue;}
            if(c=='/'&&next=='*'){blockComment=true;if(lineStart){indent(out,depth);lineStart=false;}out.append("/*");i++;continue;}
            if(c=='\"'||c=='\''||c=='`'){if(lineStart){indent(out,depth);lineStart=false;}string=true;quote=c;out.append(c);continue;}
            if(c=='\r')continue;
            if(c=='\n'){while(out.length()>0&&(out.charAt(out.length()-1)==' '||out.charAt(out.length()-1)=='\t'))out.setLength(out.length()-1);if(out.length()==0||out.charAt(out.length()-1)!='\n')out.append('\n');lineStart=true;continue;}
            if(Character.isWhitespace(c)){if(!lineStart&&out.length()>0&&out.charAt(out.length()-1)!=' '&&out.charAt(out.length()-1)!='\n')out.append(' ');continue;}
            if(c=='}'){depth=Math.max(0,depth-1);if(!lineStart&&out.length()>0&&out.charAt(out.length()-1)!='\n')out.append('\n');indent(out,depth);out.append(c);lineStart=false;continue;}
            if(lineStart){indent(out,depth);lineStart=false;}
            out.append(c);
            if(c=='{'){depth++;out.append('\n');lineStart=true;}
            else if(c==';'){out.append('\n');lineStart=true;}
        }
        String result=out.toString().replaceAll("[ \\t]+\\n","\n").trim();return result.isEmpty()?"":result+"\n";
    }
    static String validateSource(String src) {
        if(src.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("Source exceeds 2 MB");ArrayDeque<Character> stack=new ArrayDeque<>();boolean str=false,esc=false,line=false,block=false;char quote=0;int lineNo=1;
        for(int i=0;i<src.length();i++){char c=src.charAt(i),n=i+1<src.length()?src.charAt(i+1):0;if(c=='\n')lineNo++;
            if(line){if(c=='\n')line=false;continue;}if(block){if(c=='*'&&n=='/'){block=false;i++;}continue;}if(str){if(esc)esc=false;else if(c=='\\')esc=true;else if(c==quote)str=false;continue;}
            if(c=='/'&&n=='/'){line=true;i++;continue;}if(c=='/'&&n=='*'){block=true;i++;continue;}if(c=='\"'||c=='\''||c=='`'){str=true;quote=c;continue;}
            if(c=='('||c=='['||c=='{')stack.push(c);else if(c==')'||c==']'||c=='}'){if(stack.isEmpty())return "Invalid: unmatched '"+c+"' at line "+lineNo;char open=stack.pop();if(!pair(open,c))return "Invalid: '"+c+"' at line "+lineNo+" does not match '"+open+"'.";}
        }
        if(str)return "Invalid: unterminated string literal.";if(block)return "Invalid: unterminated block comment.";if(!stack.isEmpty())return "Invalid: unclosed '"+stack.peek()+"' delimiter(s).";return "Structural validation passed: strings, comments and bracket delimiters are balanced. This is not a full compiler/type checker.";
    }
    static String diff(String left,String right) {
        String[] a=left.split("\\n",-1),b=right.split("\\n",-1);if(a.length>MAX_DIFF_LINES||b.length>MAX_DIFF_LINES)throw new IllegalArgumentException("Diff is limited to 2,000 lines per file to bound memory use.");long cells=(long)(a.length+1)*(b.length+1);if(cells>4_100_000L)throw new IllegalArgumentException("Diff exceeds the safe comparison matrix size.");int[][] dp=new int[a.length+1][b.length+1];for(int i=a.length-1;i>=0;i--)for(int j=b.length-1;j>=0;j--)dp[i][j]=a[i].equals(b[j])?1+dp[i+1][j+1]:Math.max(dp[i+1][j],dp[i][j+1]);StringBuilder out=new StringBuilder();int i=0,j=0,adds=0,dels=0;while(i<a.length||j<b.length){if(i<a.length&&j<b.length&&a[i].equals(b[j])){out.append("  ").append(a[i++]).append('\n');j++;}else if(j<b.length&&(i==a.length||dp[i][j+1]>=dp[i+1][j])){out.append("+ ").append(b[j++]).append('\n');adds++;}else{out.append("- ").append(a[i++]).append('\n');dels++;}}return "Added: "+adds+"    Removed: "+dels+"\n\n"+out;
    }
    static String fileRelationships(Map<String,String> files) {
        if(files.size()>MAX_RELATION_FILES)throw new IllegalArgumentException("Folder exceeds 10,000 files.");Map<String,String> basename=new HashMap<>();for(String path:files.keySet()){String fileName=new File(path).getName();basename.put(fileName,path);int dot=fileName.lastIndexOf('.');if(dot>0)basename.put(fileName.substring(0,dot),path);}
        Pattern p=Pattern.compile("(?m)^\\s*(?:import\\s+(?:static\\s+)?|from\\s+|require\\s*\\(\\s*(?:\\x22|')|#include\\s*(?:<|\\x22))([\\w./$-]+)");StringBuilder out=new StringBuilder("Indexed source files: ").append(files.size()).append("\n\n");int edges=0;
        for(Map.Entry<String,String> e:files.entrySet()){Matcher m=p.matcher(e.getValue());Set<String> links=new TreeSet<>();while(m.find()){String ref=m.group(1);String candidate=basename.get(new File(ref.replace('.','/')).getName());if(candidate==null)candidate=basename.get(new File(ref).getName());if(candidate!=null&&!candidate.equals(e.getKey()))links.add(candidate);}if(!links.isEmpty()){out.append(e.getKey()).append("\n");for(String link:links){out.append("  → ").append(link).append('\n');edges++;}}}
        out.append("\nResolved local relationships: ").append(edges).append("\nOnly references that resolve to a scanned local basename are listed. Dynamic/runtime links and external packages cannot be inferred from source text.");return out.toString();
    }
    private static void indent(StringBuilder b,int depth){for(int i=0;i<Math.min(depth,64);i++)b.append("    ");}
    private static boolean pair(char a,char b){return a=='('&&b==')'||a=='['&&b==']'||a=='{'&&b=='}';}
    private static boolean startsAt(byte[] b,int offset,int...v){if(offset<0||b.length-offset<v.length)return false;for(int i=0;i<v.length;i++)if((b[offset+i]&255)!=v[i])return false;return true;}
    private static boolean starts(byte[] b,int...v){if(b.length<v.length)return false;for(int i=0;i<v.length;i++)if((b[i]&255)!=v[i])return false;return true;}
    private static String ascii(byte[] b,int o,int n){if(o<0||n<0||o+n>b.length)return "";return new String(b,o,n,StandardCharsets.US_ASCII);}
    private static int u16be(byte[]b,int o){return o<0||o+2>b.length?0:((b[o]&255)<<8)|(b[o+1]&255);}
    private static long u32be(byte[]b,int o){return o<0||o+4>b.length?0:(((long)b[o]&255)<<24)|(((long)b[o+1]&255)<<16)|(((long)b[o+2]&255)<<8)|((long)b[o+3]&255);}
    private static int u16(byte[]b,int o){return o<0||o+2>b.length?0:(b[o]&255)|((b[o+1]&255)<<8);}
    private static long u32(byte[]b,int o){return o<0||o+4>b.length?0:((long)b[o]&255)|(((long)b[o+1]&255)<<8)|(((long)b[o+2]&255)<<16)|(((long)b[o+3]&255)<<24);}
}
