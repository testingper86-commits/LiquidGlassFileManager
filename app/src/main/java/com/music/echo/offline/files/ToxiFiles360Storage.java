package com.music.echo.offline.files;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/** Private workspace with atomic replacement and bounded per-document recovery snapshots. */
final class ToxiFiles360Storage {
    interface Writer { void write(File temporary) throws Exception; }
    static final long MAX_IMPORT_BYTES=100L*1024*1024;
    private ToxiFiles360Storage(){}
    static File root(File filesDir){return child(filesDir,"toxifiles360");}
    static File documents(File filesDir){return child(root(filesDir),"documents");}
    static File scans(File filesDir){return child(root(filesDir),"scans");}
    static File historyRoot(File filesDir){return child(root(filesDir),"history");}
    static File child(File parent,String name){File f=new File(parent,name);if(!f.exists()&&!f.mkdirs()&&!f.isDirectory())throw new IllegalStateException("Cannot create private workspace");return f;}
    static String safeName(String raw){String n=raw==null?"document":raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();while(n.startsWith("."))n=n.substring(1);if(n.isEmpty())n="document";if(n.length()>120)n=n.substring(0,120);return n;}
    static File unique(File dir,String name){String clean=safeName(name);File f=new File(dir,clean);if(!f.exists())return f;int dot=clean.lastIndexOf('.');String stem=dot>0?clean.substring(0,dot):clean,ext=dot>0?clean.substring(dot):"";for(int i=2;i<10000;i++){f=new File(dir,stem+" ("+i+")"+ext);if(!f.exists())return f;}throw new IllegalStateException("Too many files with this name");}
    static void copy(InputStream in,File target,long max)throws IOException{if(in==null)throw new IOException("Input stream is unavailable");if(max<=0)max=MAX_IMPORT_BYTES;File tmp=new File(target.getParentFile(),target.getName()+".importing");long total=0;try(InputStream src=in;FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;while((n=src.read(b))!=-1){total+=n;if(total>max)throw new IOException("File exceeds the 100 MB import limit");out.write(b,0,n);}out.getFD().sync();}catch(Exception e){tmp.delete();if(e instanceof IOException)throw(IOException)e;throw new IOException(e);}move(tmp,target);}
    static void atomicWrite(File filesDir,File target,Writer writer)throws Exception{if(target==null||writer==null)throw new IOException("Invalid save target");File parent=target.getParentFile();if(parent==null||(!parent.exists()&&!parent.mkdirs()))throw new IOException("Cannot create destination directory");File tmp=File.createTempFile(".tox360-", ".tmp", parent);boolean committed=false;try{writer.write(tmp);if(!tmp.isFile())throw new IOException("The document engine did not produce a file");try(RandomAccessFile sync=new RandomAccessFile(tmp,"rw")){sync.getFD().sync();}if(target.exists())snapshot(filesDir,target);move(tmp,target);committed=true;}finally{if(!committed)tmp.delete();}}
    private static String historyKey(File source){try{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] h=d.digest(source.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(int i=0;i<16;i++)b.append(String.format(Locale.ROOT,"%02x",h[i]&255));return b.toString();}catch(Exception e){throw new IllegalStateException("Cannot identify document history",e);}}
    static void snapshot(File filesDir,File source)throws IOException{if(!source.isFile())return;File dir=child(historyRoot(filesDir),historyKey(source));String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.ROOT).format(new Date());File dst=new File(dir,stamp+"-"+safeName(source.getName())+".bak");Files.copy(source.toPath(),dst.toPath(),StandardCopyOption.REPLACE_EXISTING);File[] all=dir.listFiles();if(all!=null&&all.length>12){Arrays.sort(all,Comparator.comparingLong(File::lastModified));for(int i=0;i<all.length-12;i++)all[i].delete();}}
    static List<File> history(File filesDir,File source){File d=new File(historyRoot(filesDir),historyKey(source));File[] a=d.listFiles();ArrayList<File> out=new ArrayList<>();if(a!=null){out.addAll(Arrays.asList(a));out.sort((x,y)->Long.compare(y.lastModified(),x.lastModified()));}return out;}
    static void restore(File filesDir,File backup,File target)throws IOException{if(backup==null||!backup.isFile()||target==null)throw new IOException("Recovery snapshot is unavailable");if(target.exists())snapshot(filesDir,target);File tmp=File.createTempFile(".tox360-restore-", ".tmp",target.getParentFile());try{Files.copy(backup.toPath(),tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);move(tmp,target);}finally{tmp.delete();}}
    private static void move(File from,File to)throws IOException{try{Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
    static String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
}
