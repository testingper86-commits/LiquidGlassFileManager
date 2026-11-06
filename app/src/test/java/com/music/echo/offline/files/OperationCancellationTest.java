package com.music.echo.offline.files;

import org.junit.Test;
import java.io.IOException;
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
}
