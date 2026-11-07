package com.music.echo.offline.files;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TreeTraversalBudgetTest {
    @Test public void allowsTheConfiguredEntryLimit() throws Exception {
        TreeTraversalBudget budget = new TreeTraversalBudget(2, 3);
        budget.enter(0);
        budget.enter(3);
        assertEquals(2, budget.visitedEntries());
    }

    @Test public void rejectsTheFirstEntryBeyondTheLimit() throws Exception {
        TreeTraversalBudget budget = new TreeTraversalBudget(2, 3);
        budget.enter(0);
        budget.enter(1);
        assertThrows(IOException.class, () -> budget.enter(2));
        assertEquals(2, budget.visitedEntries());
    }

    @Test public void rejectsExcessiveDirectoryDepth() throws Exception {
        TreeTraversalBudget budget = new TreeTraversalBudget(10, 2);
        budget.enter(0);
        budget.enter(1);
        budget.enter(2);
        assertThrows(IOException.class, () -> budget.enter(3));
        assertEquals(3, budget.visitedEntries());
    }

    @Test public void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new TreeTraversalBudget(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TreeTraversalBudget(1, -1));
    }

}
