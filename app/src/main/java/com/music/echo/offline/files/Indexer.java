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
    volatile long finished;
    static final class PublishedSnapshot {
        final boolean ready;
        final List<Entry> all;
        final List<Entry> recent;
        final List<List<Entry>> cats;
        final long[] sizes;
        final int[] counts;

        private PublishedSnapshot(boolean ready, List<Entry> all, List<Entry> recent,
                                  List<List<Entry>> cats, long[] sizes, int[] counts) {
            this.ready = ready;
            this.all = Collections.unmodifiableList(new ArrayList<>(all));
            this.recent = Collections.unmodifiableList(new ArrayList<>(recent));
            List<List<Entry>> categoryCopies = new ArrayList<>(cats.size());
            for (List<Entry> category : cats) categoryCopies.add(Collections.unmodifiableList(new ArrayList<>(category)));
            this.cats = Collections.unmodifiableList(categoryCopies);
            this.sizes = sizes.clone();
            this.counts = counts.clone();
        }

        static PublishedSnapshot empty() {
            List<List<Entry>> categories = new ArrayList<>();
            for (int i = 0; i < 7; i++) categories.add(Collections.emptyList());
            return new PublishedSnapshot(false, Collections.emptyList(), Collections.emptyList(),
                    categories, new long[7], new int[7]);
        }
    }

    private volatile PublishedSnapshot published = PublishedSnapshot.empty();
    PublishedSnapshot snapshot() { return published; }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<WeakReference<Runnable>> completionCallbacks = new ArrayList<>();

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
        Runnable worker = () -> {
            OperationCancellation cancellation = new OperationCancellation();
            OperationCancellation.Token operationToken = cancellation.begin();
            try {
                final List<Entry> scannedAll = new ArrayList<>();
                final List<List<Entry>> scannedCats = new ArrayList<>();
                for (int i = 0; i < 7; i++) scannedCats.add(new ArrayList<Entry>());
                final long[] scannedSizes = new long[7];
                final int[] scannedCounts = new int[7];
                StorageIndexEngine.Result scan = StorageIndexEngine.scan(Environment.getExternalStorageDirectory(), snapshot -> {
                    Entry entry = new Entry(snapshot.file, snapshot.directory, snapshot.modifiedAt, snapshot.length);
                    scannedAll.add(entry);
                    if (!entry.dir) {
                        int category = cat(entry.kind);
                        scannedCats.get(category).add(entry);
                        scannedSizes[category] += entry.len;
                        scannedCounts[category]++;
                    }
                });
                truncated = scan.truncated;
                java.util.Comparator<Entry> byDate = (x, y) -> Long.compare(y.mod, x.mod);
                for (List<Entry> list : scannedCats) Collections.sort(list, byDate);
                Collections.sort(scannedAll, byDate);
                List<Entry> rec = new ArrayList<>();
                for (Entry entry : scannedAll) {
                    if (!entry.dir && entry.kind != FileKinds.OTHER) { rec.add(entry); if (rec.size() >= 8) break; }
                }
                incomplete = scan.incomplete || scan.truncated;
                PublishedSnapshot next = new PublishedSnapshot(true, scannedAll, rec, scannedCats, scannedSizes, scannedCounts);
                published = next;
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
