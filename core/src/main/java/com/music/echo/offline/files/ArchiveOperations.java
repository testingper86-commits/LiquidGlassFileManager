package com.music.echo.offline.files;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Streaming ZIP creation, bounded listing and zip-slip-safe extraction. */
final class ArchiveOperations {
    static final int MAX_ARCHIVE_ENTRIES = 20_000;
    static final int MAX_LIST_ENTRIES = 400;
    static final long MAX_ENTRY_BYTES = 2L * 1024L * 1024L * 1024L;
    static final long MAX_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L;
    interface ProgressListener { void onProgress(String message); }
    static final class EntryInfo {
        final String name; final long size; final boolean directory;
        EntryInfo(String name,long size,boolean directory){this.name=name;this.size=size;this.directory=directory;}
    }
    private static final class BuildState { final Set<String> entries=new HashSet<>(); int count; }
    private ArchiveOperations() { }

    static File createZip(List<File> sources,File destinationDirectory)throws IOException{
        OperationCancellation.checkCurrent();
        if(sources==null||sources.isEmpty())throw new IOException("Select at least one item to archive");
        for(File source:sources){
            if(source==null)throw new IOException("Archive source is missing");
            BasicFileAttributes attributes=Files.readAttributes(source.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(attributes.isSymbolicLink())throw new IOException("Symbolic links cannot be archived");
            if(!attributes.isDirectory()&&!attributes.isRegularFile())throw new IOException("Unsupported archive source: "+source.getName());
        }
        if(destinationDirectory==null||!destinationDirectory.isDirectory())throw new IOException("Archive destination is unavailable");
        File parent=destinationDirectory.getCanonicalFile();
        BasicFileAttributes firstAttributes=Files.readAttributes(sources.get(0).toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        String base=sources.size()==1?sources.get(0).getName():"Archive";
        if(sources.size()==1&&!firstAttributes.isDirectory()){int dot=base.lastIndexOf('.');if(dot>0)base=base.substring(0,dot);}
        File output=FileOperationEngine.createUniqueFile(parent,base+".zip");BuildState state=new BuildState();
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output,false)))){
            Set<String> rootNames=new HashSet<>();Path skip=output.toPath().toAbsolutePath().normalize();
            for(File source:sources){OperationCancellation.checkCurrent();boolean directory=source.isDirectory();String name=uniqueRootName(source.getName(),directory,rootNames);addZip(zip,source.toPath(),name,skip,state);}
        }catch(IOException failure){cleanupArchive(output,failure);throw failure;}
        catch(RuntimeException failure){cleanupArchive(output,failure);throw failure;}
        catch(Error failure){cleanupArchive(output,failure);throw failure;}
        return output;
    }

    private static String uniqueRootName(String original,boolean directory,Set<String> names)throws IOException{
        String candidate=original,key=ArchivePath.normalize(candidate,directory);if(names.add(key))return candidate;
        String base=original,extension="";int dot=original.lastIndexOf('.');
        if(!directory&&dot>0){base=original.substring(0,dot);extension=original.substring(dot);}
        for(int suffix=2;suffix<Integer.MAX_VALUE;suffix++){candidate=base+" ("+suffix+")"+extension;key=ArchivePath.normalize(candidate,directory);if(names.add(key))return candidate;}
        throw new IOException("Unable to create unique archive entry names");
    }

    private static void addZip(ZipOutputStream zip,Path source,String name,Path skip,BuildState state)throws IOException{
        OperationCancellation.checkCurrent();if(source.toAbsolutePath().normalize().equals(skip))return;
        BasicFileAttributes attributes=Files.readAttributes(source,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attributes.isSymbolicLink())return;boolean directory=attributes.isDirectory();
        if(!directory&&!attributes.isRegularFile())throw new IOException("Unsupported archive source: "+source.getFileName());
        String normalized=ArchivePath.normalize(name,directory),entryName=directory?normalized+"/":normalized;
        if(!state.entries.add(normalized))throw new IOException("Selected items contain duplicate archive paths");
        if(++state.count>MAX_ARCHIVE_ENTRIES)throw new IOException("Archive has too many entries");
        zip.putNextEntry(new ZipEntry(entryName));
        if(directory){zip.closeEntry();try(DirectoryStream<Path> children=Files.newDirectoryStream(source)){
            for(Path child:children){OperationCancellation.checkCurrent();Path leaf=child.getFileName();if(leaf==null)throw new IOException("Invalid source child path");addZip(zip,child,normalized+"/"+leaf,skip,state);}}
            return;
        }
        try(InputStream input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[65536];int count;
            while((count=input.read(buffer))!=-1){OperationCancellation.checkCurrent();if(count>0)zip.write(buffer,0,count);}}
        zip.closeEntry();
    }

    static List<EntryInfo> listZip(File archive,int requestedLimit)throws IOException{
        if(archive==null||requestedLimit<1)throw new IOException("Invalid archive listing request");
        if(Files.isSymbolicLink(archive.toPath())||!archive.isFile())throw new IOException("Archive file is unavailable");
        int limit=Math.min(requestedLimit,MAX_LIST_ENTRIES);List<EntryInfo> result=new ArrayList<>();
        try(ZipFile zip=new ZipFile(archive)){if(zip.size()>MAX_ARCHIVE_ENTRIES)throw new IOException("Archive has too many entries");
            Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()&&result.size()<limit){ZipEntry entry=entries.nextElement();result.add(new EntryInfo(entry.getName(),entry.getSize(),entry.isDirectory()));}}
        return Collections.unmodifiableList(result);
    }

    static File extractZip(File archive,File parentDirectory,ProgressListener listener)throws IOException{
        OperationCancellation.checkCurrent();
        if(archive==null||parentDirectory==null||Files.isSymbolicLink(archive.toPath())||!archive.isFile())throw new IOException("Archive file or destination folder is unavailable");
        String base=archive.getName();int dot=base.lastIndexOf('.');if(dot>0)base=base.substring(0,dot);
        File destination=FileOperationEngine.createUniqueDirectory(parentDirectory,base);
        try{
            Path root=destination.toPath().toRealPath();long initialFreeSpace=destination.getUsableSpace(),expanded=0L;Set<String> extractedPaths=new HashSet<>();
            try(ZipFile zip=new ZipFile(archive)){int total=zip.size();if(total>MAX_ARCHIVE_ENTRIES)throw new IOException("Archive has too many entries");
                Enumeration<? extends ZipEntry> entries=zip.entries();int index=0;
                while(entries.hasMoreElements()){
                    OperationCancellation.checkCurrent();ZipEntry entry=entries.nextElement();index++;String rawName=entry.getName();
                    if(rawName==null||rawName.isEmpty())throw new IOException("Archive contains an empty entry path");
                    String normalized=ArchivePath.normalize(rawName,entry.isDirectory());if(!extractedPaths.add(normalized))throw new IOException("Archive contains duplicate entry paths");
                    Path output=root.resolve(normalized).normalize();if(output.equals(root)||!output.startsWith(root))throw new IOException("Archive entry escapes extraction folder");
                    if(listener!=null)listener.onProgress("Extracting "+index+" of "+total+": "+rawName);OperationCancellation.checkCurrent();
                    if(entry.isDirectory()){ensureDirectories(root,output);continue;}
                    if(entry.getSize()>MAX_ENTRY_BYTES)throw new IOException("Archive entry exceeds 2 GB limit");
                    ensureDirectories(root,output.getParent());if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Archive destination already exists or is unsafe");
                    Files.createFile(output);long entryBytes=0L;
                    try(InputStream input=zip.getInputStream(entry);OutputStream out=Files.newOutputStream(output,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                        byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1){OperationCancellation.checkCurrent();if(count<=0)continue;
                            if(entryBytes>MAX_ENTRY_BYTES-count||expanded>MAX_TOTAL_BYTES-count)throw new IOException("Archive expanded-size limit exceeded");
                            entryBytes+=count;expanded+=count;if(initialFreeSpace>0&&expanded>initialFreeSpace)throw new IOException("Not enough free space to extract archive");out.write(buffer,0,count);}}
                    catch(IOException|RuntimeException failure){Files.deleteIfExists(output);throw failure;}
                }
            }
            return destination;
        }catch(IOException failure){cleanupArchive(destination,failure);throw failure;}
        catch(RuntimeException failure){cleanupArchive(destination,failure);throw failure;}
        catch(Error failure){cleanupArchive(destination,failure);throw failure;}
    }

    private static void ensureDirectories(Path root,Path directory)throws IOException{
        if(directory==null||!directory.startsWith(root))throw new IOException("Archive parent escapes extraction folder");Path current=root;
        for(Path component:root.relativize(directory)){current=current.resolve(component);
            if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)){BasicFileAttributes attributes=Files.readAttributes(current,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(attributes.isSymbolicLink()||!attributes.isDirectory())throw new IOException("Archive path conflicts with an existing file or symbolic link");}
            else Files.createDirectory(current);
        }
    }

    private static void cleanupArchive(File path,Throwable failure){if(!FileOperationEngine.deleteTree(path))failure.addSuppressed(new IOException("Unable to remove incomplete archive output: "+path.getName()));}
}
