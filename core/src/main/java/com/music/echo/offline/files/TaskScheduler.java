package com.music.echo.offline.files;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Platform-neutral bounded execution lanes with lifecycle-scoped cancellation. */
final class TaskScheduler {
    enum Lane { IO, SERIAL, NETWORK, INDEXING, THUMBNAIL }
    interface WorkerInitializer { void initialize(); }

    private final WorkerInitializer workerInitializer;
    private final ThreadPoolExecutor io;
    private final ThreadPoolExecutor serial;
    private final ThreadPoolExecutor network;
    private final ThreadPoolExecutor indexing;
    private final ThreadPoolExecutor thumbnail;

    TaskScheduler(WorkerInitializer workerInitializer) {
        this.workerInitializer = workerInitializer == null ? () -> { } : workerInitializer;
        io = pool("toxi-io", 3, 48);
        serial = pool("toxi-serial", 1, 48);
        network = pool("toxi-network", 1, 4);
        indexing = pool("toxi-index", 1, 2);
        thumbnail = pool("toxi-thumb", 2, 96);
    }

    private ThreadPoolExecutor pool(String name, int threads, int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(() -> {
                try { workerInitializer.initialize(); }
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

    private ThreadPoolExecutor pool(Lane lane) {
        if (lane == null) throw new NullPointerException("lane");
        switch (lane) {
            case IO: return io;
            case SERIAL: return serial;
            case NETWORK: return network;
            case INDEXING: return indexing;
            case THUMBNAIL: return thumbnail;
            default: throw new IllegalArgumentException("Unknown task lane");
        }
    }

    Scope newScope() { return new Scope(this); }

    void execute(Lane lane, Runnable task) {
        if (task == null) throw new NullPointerException("task");
        pool(lane).execute(task);
    }

    Future<?> submit(Lane lane, Runnable task) {
        if (task == null) throw new NullPointerException("task");
        return pool(lane).submit(task);
    }

    boolean tryExecute(Lane lane, Runnable task) {
        try { execute(lane, task); return true; }
        catch (RejectedExecutionException rejected) { return false; }
    }

    static final class ScopedExecutor implements Executor {
        private final Scope scope;
        private final Lane lane;
        private ScopedExecutor(Scope scope, Lane lane) { this.scope = scope; this.lane = lane; }
        @Override public void execute(Runnable task) { scope.execute(lane, task); }
        Future<?> submit(Runnable task) { return scope.submit(lane, task); }
        <T> Future<T> submit(Callable<T> task) { return scope.submit(lane, task); }
    }

    static final class Scope implements AutoCloseable {
        private final TaskScheduler scheduler;
        private final ConcurrentHashMap<FutureTask<?>, ThreadPoolExecutor> pending = new ConcurrentHashMap<>();
        private volatile boolean closed;
        private Scope(TaskScheduler scheduler) { this.scheduler = scheduler; }
        ScopedExecutor executor(Lane lane) { scheduler.pool(lane); return new ScopedExecutor(this, lane); }
        void execute(Lane lane, Runnable task) { submit(lane, task); }
        Future<?> submit(Lane lane, Runnable task) {
            if (task == null) throw new NullPointerException("task");
            return submit(lane, Executors.callable(task, null));
        }
        <T> Future<T> submit(Lane lane, Callable<T> task) {
            if (task == null) throw new NullPointerException("task");
            synchronized (this) {
                if (closed) throw new RejectedExecutionException("Task scope is closed");
                ThreadPoolExecutor executor = scheduler.pool(lane);
                FutureTask<T> future = new FutureTask<T>(task) {
                    @Override protected void done() { pending.remove(this); }
                };
                pending.put(future, executor);
                try { executor.execute(future); }
                catch (RuntimeException | Error failure) {
                    pending.remove(future);
                    future.cancel(false);
                    throw failure;
                }
                return future;
            }
        }
        void close(boolean cancelRunning) {
            synchronized (this) {
                closed = true;
                if (cancelRunning) {
                    for (java.util.Map.Entry<FutureTask<?>, ThreadPoolExecutor> entry : pending.entrySet()) {
                        FutureTask<?> task = entry.getKey();
                        task.cancel(true);
                        // FutureTask cancellation does not remove queued work from a ThreadPoolExecutor.
                        // Release that bounded queue capacity immediately; running tasks simply return false.
                        entry.getValue().remove(task);
                    }
                }
            }
        }
        @Override public void close() { close(true); }
    }
}
