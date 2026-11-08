package com.music.echo.offline.files;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TaskSchedulerTest {
    @Test public void closedScopeRejectsNewTasksButSharedLaneRemainsAvailable() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(() -> { });
        TaskScheduler.Scope first = scheduler.newScope();
        assertEquals("done", first.submit(TaskScheduler.Lane.IO, () -> "done").get(5, TimeUnit.SECONDS));
        first.close(false);
        try { first.submit(TaskScheduler.Lane.IO, () -> "unexpected"); fail("Closed scope accepted work"); }
        catch (RejectedExecutionException expected) { }
        TaskScheduler.Scope second = scheduler.newScope();
        try { assertEquals("shared", second.submit(TaskScheduler.Lane.IO, () -> "shared").get(5, TimeUnit.SECONDS)); }
        finally { second.close(true); }
    }

    @Test public void scopedExecutorSupportsExecuteAndSubmit() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(() -> { });
        TaskScheduler.Scope scope = scheduler.newScope();
        try {
            TaskScheduler.ScopedExecutor executor = scope.executor(TaskScheduler.Lane.IO);
            assertEquals("submitted", executor.submit(() -> "submitted").get(5, TimeUnit.SECONDS));
            CountDownLatch executed = new CountDownLatch(1);
            executor.execute(executed::countDown);
            assertTrue(executed.await(5, TimeUnit.SECONDS));
        } finally { scope.close(true); }
    }

    @Test public void gracefulCloseAllowsAcceptedWorkToFinish() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(() -> { });
        TaskScheduler.Scope scope = scheduler.newScope();
        CountDownLatch started = new CountDownLatch(1);
        Future<String> future = scope.submit(TaskScheduler.Lane.SERIAL, () -> {
            started.countDown(); Thread.sleep(100L); return "finished";
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        scope.close(false);
        assertEquals("finished", future.get(5, TimeUnit.SECONDS));
    }

    @Test public void cancellingScopeInterruptsRunningWork() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(() -> { });
        TaskScheduler.Scope scope = scheduler.newScope();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        Future<?> future = scope.submit(TaskScheduler.Lane.IO, () -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { interrupted.countDown(); Thread.currentThread().interrupt(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS)); scope.close(true);
        assertTrue(future.isCancelled()); assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }

    @Test public void cancellingScopeImmediatelyReleasesQueuedLaneCapacity() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(() -> { });
        TaskScheduler.Scope first = scheduler.newScope();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> running = first.submit(TaskScheduler.Lane.SERIAL, () -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 48; i++) first.submit(TaskScheduler.Lane.SERIAL, () -> { });

        first.close(true);
        assertTrue(running.isCancelled());

        TaskScheduler.Scope second = scheduler.newScope();
        try {
            Future<?>[] accepted = new Future<?>[48];
            for (int i = 0; i < accepted.length; i++) accepted[i] = second.submit(TaskScheduler.Lane.SERIAL, () -> { });
            for (Future<?> task : accepted) task.get(5, TimeUnit.SECONDS);
        } finally {
            second.close(true);
        }
    }

    @Test public void boundedQueuesRejectOverloadAndInitializerRunsOnWorkers() throws Exception {
        AtomicInteger initialized = new AtomicInteger();
        TaskScheduler scheduler = new TaskScheduler(initialized::incrementAndGet);
        TaskScheduler.Scope scope = scheduler.newScope();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            scope.submit(TaskScheduler.Lane.SERIAL, () -> { started.countDown(); release.await(); return null; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 48; i++) scope.submit(TaskScheduler.Lane.SERIAL, () -> { });
            assertFalse(scheduler.tryExecute(TaskScheduler.Lane.SERIAL, () -> { }));
            assertTrue(initialized.get() > 0);
        } finally { release.countDown(); scope.close(true); }
    }
}
