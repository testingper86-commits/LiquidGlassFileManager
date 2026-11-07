package com.music.echo.offline.files;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/** Android execution policy adapter for the platform-neutral core scheduler. */
final class TaskCoordinator {
    enum Lane { IO, SERIAL, NETWORK, INDEXING, THUMBNAIL }
    private static final TaskScheduler SCHEDULER = new TaskScheduler(() ->
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND));
    private TaskCoordinator() { }

    private static TaskScheduler.Lane coreLane(Lane lane) {
        if (lane == null) throw new NullPointerException("lane");
        switch (lane) {
            case IO: return TaskScheduler.Lane.IO;
            case SERIAL: return TaskScheduler.Lane.SERIAL;
            case NETWORK: return TaskScheduler.Lane.NETWORK;
            case INDEXING: return TaskScheduler.Lane.INDEXING;
            case THUMBNAIL: return TaskScheduler.Lane.THUMBNAIL;
            default: throw new IllegalArgumentException("Unknown task lane");
        }
    }

    static void execute(Lane lane, Runnable task) { SCHEDULER.execute(coreLane(lane), task); }
    static Future<?> submit(Lane lane, Runnable task) { return SCHEDULER.submit(coreLane(lane), task); }
    static boolean tryExecute(Lane lane, Runnable task) { return SCHEDULER.tryExecute(coreLane(lane), task); }

    static final class ScopedExecutor implements Executor {
        private final TaskScheduler.ScopedExecutor delegate;
        private ScopedExecutor(TaskScheduler.ScopedExecutor delegate) { this.delegate = delegate; }
        @Override public void execute(Runnable task) { delegate.execute(task); }
        Future<?> submit(Runnable task) { return delegate.submit(task); }
        <T> Future<T> submit(Callable<T> task) { return delegate.submit(task); }
    }

    static final class Scope implements AutoCloseable {
        private final TaskScheduler.Scope delegate = SCHEDULER.newScope();
        ScopedExecutor executor(Lane lane) { return new ScopedExecutor(delegate.executor(coreLane(lane))); }
        void execute(Lane lane, Runnable task) { delegate.execute(coreLane(lane), task); }
        Future<?> submit(Lane lane, Runnable task) { return delegate.submit(coreLane(lane), task); }
        <T> Future<T> submit(Lane lane, Callable<T> task) { return delegate.submit(coreLane(lane), task); }
        void close(boolean cancelRunning) { delegate.close(cancelRunning); }
        @Override public void close() { delegate.close(); }
    }
}
