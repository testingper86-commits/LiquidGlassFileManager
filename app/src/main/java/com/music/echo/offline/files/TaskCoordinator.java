package com.music.echo.offline.files;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** App-wide bounded background execution with lifecycle-scoped cancellation. */
final class TaskCoordinator {
    enum Lane { IO, SERIAL, NETWORK, INDEXING, THUMBNAIL }

    private static final ThreadPoolExecutor IO = pool("toxi-io", 3, 48);
    private static final ThreadPoolExecutor SERIAL = pool("toxi-serial", 1, 48);
    private static final ThreadPoolExecutor NETWORK = pool("toxi-network", 1, 4);
    private static final ThreadPoolExecutor INDEXING = pool("toxi-index", 1, 2);
    private static final ThreadPoolExecutor THUMBNAIL = pool("toxi-thumb", 2, 96);

    private TaskCoordinator() { }

    private static ThreadPoolExecutor pool(String name, int threads, int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(() -> {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); }
                catch (RuntimeException ignored) { }
                task.run();
            }, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ThreadPoolExecutor pool(Lane lane) {
        switch (lane) {
            case IO: return IO;
            case SERIAL: return SERIAL;
            case NETWORK: return NETWORK;
            case INDEXING: return INDEXING;
            case THUMBNAIL: return THUMBNAIL;
            default: throw new IllegalArgumentException("Unknown task lane");
        }
    }

    static void execute(Lane lane, Runnable task) {
        if (task == null) throw new NullPointerException("task");
        pool(lane).execute(task);
    }

    static boolean tryExecute(Lane lane, Runnable task) {
        try { execute(lane, task); return true; }
        catch (RejectedExecutionException rejected) { return false; }
    }

    static final class Scope implements AutoCloseable {
        private final Set<FutureTask<?>> pending = ConcurrentHashMap.newKeySet();
        private volatile boolean closed;

        Executor executor(Lane lane) { return task -> execute(lane, task); }

        void execute(Lane lane, Runnable task) { submit(lane, task); }

        Future<?> submit(Lane lane, Runnable task) { return submit(lane, java.util.concurrent.Executors.callable(task, null)); }

        <T> Future<T> submit(Lane lane, Callable<T> task) {
            if (closed) throw new RejectedExecutionException("Task scope is closed");
            FutureTask<T> future = new FutureTask<T>(task) {
                @Override protected void done() { pending.remove(this); }
            };
            pending.add(future);
            if (closed) {
                future.cancel(true);
                throw new RejectedExecutionException("Task scope is closed");
            }
            try { pool(lane).execute(future); }
            catch (RuntimeException failure) { pending.remove(future); future.cancel(false); throw failure; }
            return future;
        }

        void close(boolean cancelRunning) {
            closed = true;
            if (cancelRunning) for (FutureTask<?> task : pending) task.cancel(true);
        }

        @Override public void close() { close(true); }
    }
}
