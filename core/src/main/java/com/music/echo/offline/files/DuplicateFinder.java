package com.music.echo.offline.files;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/** Platform-neutral exact-duplicate detection over immutable file metadata snapshots. */
final class DuplicateFinder {
    private static final char[] HEX="0123456789abcdef".toCharArray();
    static final class Snapshot {
        final File file; final boolean directory; final long modifiedAt; final long length;
        Snapshot(File file,boolean directory,long modifiedAt,long length){this.file=file;this.directory=directory;this.modifiedAt=modifiedAt;this.length=length;}
    }
    static final class Group { final long size; final List<File> files=new ArrayList<>(); Group(long size){this.size=size;} }
    private DuplicateFinder() { }
    static List<Group> find(List<Snapshot> all)throws IOException{
        OperationCancellation.checkCurrent();if(all==null)throw new IOException("The storage index is unavailable");
        Map<Long,List<Snapshot>> bySize=new HashMap<>();
        for(Snapshot snapshot:all){OperationCancellation.checkCurrent();if(snapshot==null||snapshot.directory||snapshot.length<0)continue;if(snapshot.file==null)throw new IOException("The storage index contains an invalid file entry");bySize.computeIfAbsent(snapshot.length,k->new ArrayList<>()).add(snapshot);}
        List<Group> result=new ArrayList<>();
        for(Map.Entry<Long,List<Snapshot>> sizeGroup:bySize.entrySet()){
            if(sizeGroup.getValue().size()<2)continue;Map<String,Group> byHash=new HashMap<>();
            for(Snapshot snapshot:sizeGroup.getValue()){verifySnapshot(snapshot);String hash=sha256(snapshot.file);verifySnapshot(snapshot);byHash.computeIfAbsent(hash,k->new Group(sizeGroup.getKey())).files.add(snapshot.file);}
            for(Group group:byHash.values())if(group.files.size()>1)result.add(group);
        }
        Collections.sort(result,(a,b)->Long.compare(reclaimable(b),reclaimable(a)));return result;
    }
    private static void verifySnapshot(Snapshot snapshot)throws IOException{
        OperationCancellation.checkCurrent();File file=snapshot.file;final boolean link;
        try{link=Files.isSymbolicLink(file.toPath());}catch(SecurityException e){throw new IOException("Access denied to "+file.getName()+" during duplicate scan",e);}
        if(link||!file.isFile()||file.length()!=snapshot.length||file.lastModified()!=snapshot.modifiedAt)throw new IOException("File changed or became unavailable during duplicate scan: "+file.getName());
    }
    private static String sha256(File file)throws IOException{
        final MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 is unavailable",e);}
        try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1){OperationCancellation.checkCurrent();if(count>0)digest.update(buffer,0,count);}OperationCancellation.checkCurrent();}
        catch(IOException e){if("Operation cancelled".equals(e.getMessage()))throw e;throw new IOException("Unable to read "+file.getName()+" during duplicate scan",e);}
        catch(SecurityException e){throw new IOException("Access denied to "+file.getName()+" during duplicate scan",e);}
        StringBuilder out=new StringBuilder(64);for(byte value:digest.digest()){int v=value&255;out.append(HEX[v>>>4]).append(HEX[v&15]);}return out.toString();
    }
    private static long reclaimable(Group group){long extra=group.files.size()-1L;if(extra<=0)return 0L;return group.size>Long.MAX_VALUE/extra?Long.MAX_VALUE:group.size*extra;}
}
