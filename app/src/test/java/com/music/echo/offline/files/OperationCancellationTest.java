package com.music.echo.offline.files;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class OperationCancellationTest {
    @Test public void cancellationIsIsolatedBetweenActivityControllers() {
        OperationCancellation first = new OperationCancellation();
        OperationCancellation second = new OperationCancellation();
        OperationCancellation.Token firstToken = first.begin();
        OperationCancellation.Token secondToken = second.begin();
        try {
            first.cancelActive();
            assertTrue(firstToken.isCancelled());
            assertFalse(secondToken.isCancelled());
        } finally {
            first.finish(firstToken);
            second.finish(secondToken);
        }
    }

    @Test public void nestedOperationScopesPreserveAndObserveParentCancellation() throws Exception {
        OperationCancellation parent = new OperationCancellation();
        OperationCancellation child = new OperationCancellation();
        OperationCancellation.Token parentToken = parent.begin();
        OperationCancellation.Token childToken = child.begin();
        try {
            assertFalse(OperationCancellation.isCancelled());
            parent.cancelActive();
            assertTrue("nested work must observe cancellation from its parent scope", OperationCancellation.isCancelled());
            child.finish(childToken);
            assertTrue("finishing nested work must restore the cancelled parent context", OperationCancellation.isCancelled());
            try {
                OperationCancellation.checkCurrent();
                fail("Cancelled parent context was lost after nested work finished");
            } catch (IOException expected) {
                assertEquals("Operation cancelled", expected.getMessage());
            }
        } finally {
            child.finish(childToken);
            parent.finish(parentToken);
        }
        assertFalse(OperationCancellation.isCancelled());
    }

    @Test public void cancellationOnlyAffectsOperationsThatAreStillActive() {
        OperationCancellation controller = new OperationCancellation();
        OperationCancellation.Token completed = controller.begin();
        controller.finish(completed);
        controller.cancelActive();
        assertFalse(completed.isCancelled());
    }

    @Test public void checkCancelledUsesTheCurrentWorkerOperation() throws Exception {
        OperationCancellation controller = new OperationCancellation();
        OperationCancellation.Token token = controller.begin();
        try {
            assertFalse(OperationCancellation.isCancelled());
            OperationCancellation.checkCurrent();
            controller.cancelActive();
            assertTrue(OperationCancellation.isCancelled());
            try {
                OperationCancellation.checkCurrent();
                fail("Cancelled operation continued without an exception");
            } catch (IOException expected) {
                assertEquals("Operation cancelled", expected.getMessage());
            }
        } finally {
            controller.finish(token);
        }
    }

    @Test public void workerCancellationIsThreadLocalAndVisibleToThatWorker() throws Exception {
        OperationCancellation controller = new OperationCancellation();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch continueWorker = new CountDownLatch(1);
        AtomicReference<OperationCancellation.Token> tokenRef = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            OperationCancellation.Token token = controller.begin();
            tokenRef.set(token);
            entered.countDown();
            try {
                if (!continueWorker.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker timed out");
                assertTrue(OperationCancellation.isCancelled());
                OperationCancellation.checkCurrent();
                throw new AssertionError("cancelled worker passed checkCurrent");
            } catch (IOException expected) {
                assertEquals("Operation cancelled", expected.getMessage());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                controller.finish(token);
            }
        }, "toxifiles-cancellation-test");
        worker.start();
        assertTrue("worker did not start", entered.await(5, TimeUnit.SECONDS));
        assertFalse("worker ThreadLocal leaked into test thread", OperationCancellation.isCancelled());
        controller.cancelActive();
        continueWorker.countDown();
        worker.join(5000);
        assertFalse("worker did not terminate", worker.isAlive());
        assertNull(failure.get());
        assertNotNull(tokenRef.get());
        assertTrue(tokenRef.get().isCancelled());
        assertFalse(OperationCancellation.isCancelled());
    }

    @Test public void repeatedBeginCancelFinishCyclesDoNotLeakActiveTokens() {
        OperationCancellation controller = new OperationCancellation();
        for (int i = 0; i < 5000; i++) {
            OperationCancellation.Token token = controller.begin();
            if ((i & 1) == 0) controller.cancelActive();
            controller.finish(token);
            assertEquals("token cancellation mismatch at cycle " + i, (i & 1) == 0, token.isCancelled());
            assertFalse(OperationCancellation.isCancelled());
        }
        OperationCancellation.Token finalToken = controller.begin();
        controller.cancelActive();
        assertTrue(finalToken.isCancelled());
        controller.finish(finalToken);
    }
}
