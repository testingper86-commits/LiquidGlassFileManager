package com.music.echo.offline.files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/** App adapter from indexed UI entries to the platform-neutral duplicate engine. */
final class DupFinder {
    static final class Group { long size; final List<File> files=new ArrayList<>(); }
    private DupFinder() { }
    static List<Group> find(List<Entry> entries)throws IOException{
        if(entries==null)throw new IOException("The storage index is unavailable");
        List<DuplicateFinder.Snapshot> snapshots=new ArrayList<>(entries.size());
        for(Entry entry:entries)snapshots.add(entry==null?null:new DuplicateFinder.Snapshot(entry.f,entry.dir,entry.mod,entry.len));
        List<DuplicateFinder.Group> found=DuplicateFinder.find(snapshots);List<Group> result=new ArrayList<>(found.size());
        for(DuplicateFinder.Group source:found){Group group=new Group();group.size=source.size;group.files.addAll(source.files);result.add(group);}return result;
    }
}
