package com.music.echo.offline.files;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Private document storage with bounded imports and atomic replacement. */
final class OfficeDocumentStorage {
    interface Writer { void write(File temporary) throws Exception; }
    static final long MAX_IMPORT_BYTES=100L*1024*1024;
    private OfficeDocumentStorage(){}
    static File root(File filesDir){return child(filesDir,"office-documents");}
    static File documents(File filesDir){
        File destination=child(root(filesDir),"documents");
        File legacy=new File(new File(filesDir,"toxifiles360"),"documents");
        if(legacy.isDirectory()){
            File[] existing=legacy.listFiles();
            if(existing!=null)for(File source:existing){
                if(!source.isFile()||java.nio.file.Files.isSymbolicLink(source.toPath()))continue;
                File target=unique(destination,source.getName());
                try{
                    java.nio.file.Files.copy(source.toPath(),target.toPath());
                    if(java.nio.file.Files.size(target.toPath())==java.nio.file.Files.size(source.toPath()))source.delete();
                    else target.delete();
                }catch(IOException failure){target.delete();}
            }
            File remaining=legacy.listFiles();if(remaining!=null&&remaining.length==0)legacy.delete();
        }
        return destination;
    }
    static File child(File parent,String name){File f=new File(parent,name);if(!f.exists()&&!f.mkdirs()&&!f.isDirectory())throw new IllegalStateException("Cannot create private workspace");return f;}
    static String safeName(String raw){String n=raw==null?"document":raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();while(n.startsWith("."))n=n.substring(1);if(n.isEmpty())n="document";if(n.length()>120)n=n.substring(0,120);return n;}
    static File unique(File dir,String name){String clean=safeName(name);File f=new File(dir,clean);if(!f.exists())return f;int dot=clean.lastIndexOf('.');String stem=dot>0?clean.substring(0,dot):clean,ext=dot>0?clean.substring(dot):"";for(int i=2;i<10000;i++){f=new File(dir,stem+" ("+i+")"+ext);if(!f.exists())return f;}throw new IllegalStateException("Too many files with this name");}
    static void copy(InputStream in,File target,long max)throws IOException{if(in==null)throw new IOException("Input stream is unavailable");if(max<=0)max=MAX_IMPORT_BYTES;File tmp=new File(target.getParentFile(),target.getName()+".importing");long total=0;try(InputStream src=in;FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;while((n=src.read(b))!=-1){total+=n;if(total>max)throw new IOException("File exceeds the 100 MB import limit");out.write(b,0,n);}out.getFD().sync();}catch(Exception e){tmp.delete();if(e instanceof IOException)throw(IOException)e;throw new IOException(e);}move(tmp,target);}
    static void atomicWrite(File filesDir,File target,Writer writer)throws Exception{if(target==null||writer==null)throw new IOException("Invalid save target");File parent=target.getParentFile();if(parent==null||(!parent.exists()&&!parent.mkdirs()))throw new IOException("Cannot create destination directory");File tmp=File.createTempFile(".office-", ".tmp", parent);boolean committed=false;try{writer.write(tmp);if(!tmp.isFile())throw new IOException("The document engine did not produce a file");try(RandomAccessFile sync=new RandomAccessFile(tmp,"rw")){sync.getFD().sync();}move(tmp,target);committed=true;}finally{if(!committed)tmp.delete();}}
    private static void move(File from,File to)throws IOException{try{Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
}
