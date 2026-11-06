package com.music.echo.offline.files;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class TaskCoordinatorTest {
    @Test public void closedScopeRejectsNewTasksButSharedLaneRemainsAvailable() throws Exception {
        TaskCoordinator.Scope first = new TaskCoordinator.Scope();
        assertEquals("done", first.submit(TaskCoordinator.Lane.IO, () -> "done").get(5, TimeUnit.SECONDS));
        first.close(false);
        try {
            first.submit(TaskCoordinator.Lane.IO, () -> "unexpected");
            fail("Closed task scope accepted new work");
        } catch (java.util.concurrent.RejectedExecutionException expected) { }
        TaskCoordinator.Scope second = new TaskCoordinator.Scope();
        try { assertEquals("shared", second.submit(TaskCoordinator.Lane.IO, () -> "shared").get(5, TimeUnit.SECONDS)); }
        finally { second.close(true); }
    }

    @Test public void gracefulCloseAllowsAcceptedWorkToFinish() throws Exception {
        TaskCoordinator.Scope scope = new TaskCoordinator.Scope();
        CountDownLatch started = new CountDownLatch(1);
        Future<String> future = scope.submit(TaskCoordinator.Lane.SERIAL, () -> {
            started.countDown();
            Thread.sleep(100L);
            return "finished";
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        scope.close(false);
        assertEquals("finished", future.get(5, TimeUnit.SECONDS));
    }

    @Test public void cancellingScopeInterruptsRunningWork() throws Exception {
        TaskCoordinator.Scope scope = new TaskCoordinator.Scope();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Future<?> future = scope.submit(TaskCoordinator.Lane.IO, () -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return null;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        scope.close(true);
        assertTrue(future.isCancelled());
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }
}
