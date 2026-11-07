package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.ref.WeakReference;
import java.util.Iterator;

/** Android adapter: categorizes core filesystem snapshots and publishes them on the UI thread. */
final class Indexer {
    static final Indexer I = new Indexer();
    static final String[] CAT_NAMES = {"Images", "Videos", "Audio", "Documents", "Archives", "Apps", "Other"};

    volatile boolean ready, running, dirty, truncated, incomplete;
    volatile String lastError = "";
    long finished;
    List<Entry> all = new ArrayList<>();
    List<Entry> recent = new ArrayList<>();
    List<List<Entry>> cats = new ArrayList<>();
    long[] sizes = new long[7];
    int[] counts = new int[7];

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<WeakReference<Runnable>> completionCallbacks = new ArrayList<>();
    private List<Entry> tmpAll;
    private List<List<Entry>> tmpCats;
    private long[] tmpSizes;
    private int[] tmpCounts;
    private boolean tmpIncomplete;

    static int cat(int kind) {
        switch (kind) {
            case FileKinds.IMAGE: return 0;
            case FileKinds.VIDEO: return 1;
            case FileKinds.AUDIO: return 2;
            case FileKinds.PDF: case FileKinds.TEXT: return 3;
            case FileKinds.ARCHIVE: return 4;
            case FileKinds.APK: return 5;
            default: return 6;
        }
    }

    synchronized void start(final Runnable done) {
        registerCompletionCallback(done);
        if (running) return;
        running = true;
        dirty = false;
        truncated = false;
        tmpIncomplete = false;
        Runnable worker = () -> {
            OperationCancellation cancellation = new OperationCancellation();
            OperationCancellation.Token operationToken = cancellation.begin();
            try {
                tmpAll = new ArrayList<>();
                tmpCats = new ArrayList<>();
                for (int i = 0; i < 7; i++) tmpCats.add(new ArrayList<Entry>());
                tmpSizes = new long[7];
                tmpCounts = new int[7];
                StorageIndexEngine.Result scan = StorageIndexEngine.scan(Environment.getExternalStorageDirectory(), snapshot -> {
                    Entry entry = new Entry(snapshot.file, snapshot.directory, snapshot.modifiedAt, snapshot.length);
                    tmpAll.add(entry);
                    if (!entry.dir) {
                        int category = cat(entry.kind);
                        tmpCats.get(category).add(entry);
                        tmpSizes[category] += entry.len;
                        tmpCounts[category]++;
                    }
                });
                truncated = scan.truncated;
                tmpIncomplete = scan.incomplete;
                java.util.Comparator<Entry> byDate = (x, y) -> Long.compare(y.mod, x.mod);
                for (List<Entry> list : tmpCats) Collections.sort(list, byDate);
                Collections.sort(tmpAll, byDate);
                List<Entry> rec = new ArrayList<>();
                for (Entry entry : tmpAll) {
                    if (!entry.dir && entry.kind != FileKinds.OTHER) { rec.add(entry); if (rec.size() >= 8) break; }
                }
                all = tmpAll; cats = tmpCats; sizes = tmpSizes; counts = tmpCounts; recent = rec;
                incomplete = tmpIncomplete || truncated;
                ready = true;
                lastError = "";
                finished = System.currentTimeMillis();
            } catch (Exception failure) {
                incomplete = true;
                lastError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            } finally {
                cancellation.finish(operationToken);
                postCompletionCallbacks();
            }
        };
        try {
            TaskCoordinator.execute(TaskCoordinator.Lane.INDEXING, worker);
        } catch (RuntimeException startError) {
            lastError = startError.getMessage() == null ? startError.getClass().getSimpleName() : startError.getMessage();
            postCompletionCallbacks();
        }
    }

    private synchronized void registerCompletionCallback(Runnable callback) {
        if (callback == null) return;
        for (Iterator<WeakReference<Runnable>> it = completionCallbacks.iterator(); it.hasNext();) {
            Runnable existing = it.next().get();
            if (existing == null) it.remove();
            else if (existing == callback) return;
        }
        completionCallbacks.add(new WeakReference<>(callback));
    }

    private void postCompletionCallbacks() {
        List<Runnable> callbacks = new ArrayList<>();
        synchronized (this) {
            running = false;
            for (WeakReference<Runnable> reference : completionCallbacks) {
                Runnable callback = reference.get();
                if (callback != null) callbacks.add(callback);
            }
            completionCallbacks.clear();
        }
        for (Runnable callback : callbacks) ui.post(callback);
    }
}
