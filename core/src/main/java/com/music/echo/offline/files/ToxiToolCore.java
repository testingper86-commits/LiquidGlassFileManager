package com.music.echo.offline.files;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Bounded, offline primitives shared by ToxiFiles' seven integrated tools. */
final class ToxiToolCore {
    static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;
    static final long MAX_HEX_BYTES = 8L * 1024 * 1024;
    static final long MAX_ZIP_TOTAL_BYTES = 512L * 1024 * 1024;
    static final long MAX_ZIP_ENTRY_BYTES = 128L * 1024 * 1024;
    static final int MAX_COMPARE_ENTRIES = 20000;
    static final int MAX_ZIP_ENTRIES = 20000;
    private static final int BUFFER = 65536;
    private ToxiToolCore() { }

    static byte[] readBounded(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[BUFFER]; long total = 0; int n;
        while ((n = in.read(b)) != -1) { total += n; if (total > limit) throw new IOException("File exceeds the supported " + limit + " byte limit"); out.write(b, 0, n); }
        return out.toByteArray();
    }
    static Map<String,String> hashes(InputStream source) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        CRC32 crc = new CRC32();
        try (InputStream in=source) { byte[] b=new byte[BUFFER]; int n; while((n=in.read(b))!=-1){sha256.update(b,0,n);sha512.update(b,0,n);crc.update(b,0,n);} }
        Map<String,String> r=new LinkedHashMap<>();r.put("SHA-256",hex(sha256.digest()));r.put("SHA-512",hex(sha512.digest()));r.put("CRC32",String.format(Locale.US,"%08x",crc.getValue()));return r;
    }
    static Map<String,String> hashes(File file) throws Exception { return hashes(new BufferedInputStream(new FileInputStream(file))); }
    static String hex(byte[] bytes) { StringBuilder s=new StringBuilder(bytes.length*2);for(byte b:bytes)s.append(String.format(Locale.US,"%02x",b&255));return s.toString(); }
    static String formatHex(byte[] bytes, int offset, int length) {
        if(offset<0||length<0||offset>bytes.length-length)throw new IllegalArgumentException("Invalid byte range");StringBuilder out=new StringBuilder();
        for(int p=offset;p<offset+length;p+=16){out.append(String.format(Locale.US,"%08X  ",p));int end=Math.min(p+16,offset+length);for(int i=p;i<p+16;i++){if(i<end)out.append(String.format(Locale.US,"%02X ",bytes[i]&255));else out.append("   ");if(i-p==7)out.append(' ');}out.append(" |");for(int i=p;i<end;i++){int c=bytes[i]&255;out.append(c>=32&&c<127?(char)c:'.');}out.append("|\n");}return out.toString();
    }
    static byte[] parseHex(String text) {
        if (text == null || text.length() > MAX_HEX_BYTES * 4L) throw new IllegalArgumentException("Hex input exceeds the supported text limit");
        StringBuilder digits = new StringBuilder();
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = lineStart;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n' && text.charAt(lineEnd) != '\r'
                    && text.charAt(lineEnd) != '\u0085' && text.charAt(lineEnd) != '\u2028' && text.charAt(lineEnd) != '\u2029') lineEnd++;
            String line = text.substring(lineStart, lineEnd);
            int ascii = line.indexOf('|');
            if (ascii >= 0) line = line.substring(0, ascii);
            int prefix = 0;
            while (prefix < line.length() && Character.isWhitespace(line.charAt(prefix))) prefix++;
            int offsetEnd = prefix;
            while (offsetEnd < line.length() && offsetEnd - prefix < 8 && Character.digit(line.charAt(offsetEnd), 16) >= 0) offsetEnd++;
            if (offsetEnd - prefix == 8 && offsetEnd < line.length() && Character.isWhitespace(line.charAt(offsetEnd))) {
                while (offsetEnd < line.length() && Character.isWhitespace(line.charAt(offsetEnd))) offsetEnd++;
                line = line.substring(offsetEnd);
            }
            int comment = line.length();
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '#' || (line.charAt(i) == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/')) {
                    comment = i;
                    break;
                }
            }
            for (int i = 0; i < comment; i++) {
                char c = line.charAt(i);
                if (c == '0' && i + 1 < comment && (line.charAt(i + 1) == 'x' || line.charAt(i + 1) == 'X')) {
                    i++;
                    continue;
                }
                if (Character.isWhitespace(c) || c == ',' || c == ':' || c == ';' || c == '_' || c == '-') continue;
                if (Character.digit(c, 16) < 0) throw new IllegalArgumentException("Only hexadecimal byte pairs are allowed");
                digits.append(c);
                if (digits.length() > MAX_HEX_BYTES * 2L) throw new IllegalArgumentException("Hex input exceeds the supported byte limit");
            }
            if (lineEnd == text.length()) break;
            lineStart = lineEnd + 1;
            if (text.charAt(lineEnd) == '\r' && lineStart < text.length() && text.charAt(lineStart) == '\n') lineStart++;
        }
        if ((digits.length() & 1) != 0) throw new IllegalArgumentException("Hex input must contain complete byte pairs");
        byte[] out = new byte[digits.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
    static String decode(byte[] bytes, String charsetName) throws Exception {
        Charset cs=Charset.forName(charsetName);CharsetDecoder d=cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);return d.decode(ByteBuffer.wrap(bytes)).toString();
    }
    static byte[] encode(String value, String charsetName) throws Exception {
        CharsetEncoder e=Charset.forName(charsetName).newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);ByteBuffer b=e.encode(java.nio.CharBuffer.wrap(value));byte[] out=new byte[b.remaining()];b.get(out);return out;
    }
    static void atomicWrite(File target, byte[] bytes) throws IOException {
        File parent=target.getAbsoluteFile().getParentFile();if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Destination folder is unavailable");File temp=File.createTempFile(".toxifiles-", ".tmp", parent);boolean moved=false;
        try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}try{Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException ex){Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}moved=true;}finally{if(!moved)temp.delete();}
    }
    static List<String> compareFolders(File left, File right) throws IOException {
        if(!left.isDirectory()||!right.isDirectory())throw new IOException("Both selected locations must be folders");Map<String,File>a=walk(left),b=walk(right);TreeSet<String> keys=new TreeSet<>();keys.addAll(a.keySet());keys.addAll(b.keySet());List<String> out=new ArrayList<>();
        for(String k:keys){File x=a.get(k),y=b.get(k);if(x==null)out.add("RIGHT ONLY  "+k);else if(y==null)out.add("LEFT ONLY   "+k);else if(x.length()!=y.length())out.add("SIZE DIFF   "+k+" ("+x.length()+" vs "+y.length()+")");else if(!Arrays.equals(digest(x),digest(y)))out.add("CONTENT DIFF "+k);else out.add("IDENTICAL   "+k);}return out;
    }
    private static Map<String,File> walk(File root)throws IOException{Map<String,File> m=new HashMap<>();ArrayDeque<File> q=new ArrayDeque<>();q.add(root);while(!q.isEmpty()){File d=q.removeFirst();File[] fs=d.listFiles();if(fs==null)throw new IOException("Cannot read folder: "+d);Arrays.sort(fs,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));for(File f:fs){if(Files.isSymbolicLink(f.toPath()))continue;String rel=root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/');m.put(rel,f);if(m.size()>MAX_COMPARE_ENTRIES)throw new IOException("Folder comparison exceeds "+MAX_COMPARE_ENTRIES+" entries");if(f.isDirectory())q.addLast(f);}}return m;}
    private static byte[] digest(File f)throws IOException{try{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[]b=new byte[BUFFER];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}return md.digest();}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static List<ZipEntry> listZip(File archive)throws IOException{List<ZipEntry> out=new ArrayList<>();try(ZipFile z=new ZipFile(archive)){Enumeration<? extends ZipEntry> e=z.entries();while(e.hasMoreElements()){out.add(e.nextElement());if(out.size()>MAX_ZIP_ENTRIES)throw new IOException("Archive contains too many entries");}}return out;}
    static String validateZipName(String name)throws IOException{
        boolean directory=name!=null&&(name.endsWith("/")||name.endsWith("\\"));
        return validateZipName(name,directory);
    }
    static String validateZipName(String name,boolean directory)throws IOException{
        String normalized=ArchivePath.normalize(name,directory);
        if(normalized.length()>240)throw new IOException("Archive entry name is too long");
        return directory?normalized+"/":normalized;
    }
    /** Rebuilds an archive atomically. rename/delete maps are keyed by original entry names; additions are copied from regular files. */
    static void rewriteZip(File archive, Map<String,String> renames, Set<String> deletes, Map<String,File> additions)throws IOException{
        File parent=archive.getAbsoluteFile().getParentFile();File temp=File.createTempFile(".toxizip-", ".tmp", parent);boolean ok=false;long total=0;Set<String> names=new HashSet<>();
        try(ZipFile src=new ZipFile(archive);ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))){
            Enumeration<? extends ZipEntry> entries=src.entries();int count=0;while(entries.hasMoreElements()){ZipEntry old=entries.nextElement();if(++count>MAX_ZIP_ENTRIES)throw new IOException("Archive contains too many entries");String original=old.getName();if(deletes.contains(original))continue;String name=validateZipName(renames.containsKey(original)?renames.get(original):original,old.isDirectory());if(!names.add(name))throw new IOException("Duplicate archive path: "+name);ZipEntry ne=new ZipEntry(name);ne.setTime(old.getTime());if(old.isDirectory()){out.putNextEntry(ne);out.closeEntry();continue;}if(old.getSize()>MAX_ZIP_ENTRY_BYTES)throw new IOException("Entry exceeds size limit: "+original);out.putNextEntry(ne);try(InputStream in=src.getInputStream(old)){byte[]b=new byte[BUFFER];int n;long entry=0;while((n=in.read(b))!=-1){entry+=n;total+=n;if(entry>MAX_ZIP_ENTRY_BYTES||total>MAX_ZIP_TOTAL_BYTES)throw new IOException("Expanded archive exceeds safe edit limits");out.write(b,0,n);}}out.closeEntry();}
            for(Map.Entry<String,File> add:additions.entrySet()){String name=validateZipName(add.getKey());File f=add.getValue();if(!f.isFile()||f.length()>MAX_ZIP_ENTRY_BYTES)throw new IOException("Added file is missing or too large: "+name);if(!names.add(name))throw new IOException("Duplicate archive path: "+name);out.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(f)){byte[]b=new byte[BUFFER];int n;while((n=in.read(b))!=-1){total+=n;if(total>MAX_ZIP_TOTAL_BYTES)throw new IOException("Archive exceeds safe edit limits");out.write(b,0,n);}}out.closeEntry();}
        }catch(Throwable e){temp.delete();if(e instanceof IOException)throw(IOException)e;throw new IOException("Archive update failed",e);}
        try{Files.move(temp.toPath(),archive.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);ok=true;}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp.toPath(),archive.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);ok=true;}finally{if(!ok)temp.delete();}
    }
}
