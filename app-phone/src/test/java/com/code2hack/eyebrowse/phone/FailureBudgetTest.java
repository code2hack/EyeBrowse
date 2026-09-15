package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM regressions for the bounded failure-capture budget actually used by the input paths. */
public class FailureBudgetTest {

    /** Mutable test clock. */
    private static final class FakeClock {
        long now;

        long now() {
            return now;
        }
    }

    @Test
    public void remainingBudgetCountsDownToExpiry() {
        FakeClock clock = new FakeClock();
        HarnessProtocol.FailureBudget budget =
                new HarnessProtocol.FailureBudget(clock::now, 2_000);
        assertEquals(2_000, budget.stepBudgetMs());
        assertFalse(budget.expired());
        clock.now = 1_500;
        assertEquals(500, budget.stepBudgetMs());
        assertEquals(1_500, budget.elapsedMs());
        clock.now = 2_000;
        assertEquals(0, budget.stepBudgetMs());
        assertTrue(budget.expired());
    }

    @Test
    public void lateResultsAfterTheDeadlineAreDiscarded() {
        FakeClock clock = new FakeClock();
        HarnessProtocol.FailureBudget budget =
                new HarnessProtocol.FailureBudget(clock::now, 2_000);
        assertFalse("an on-time result is usable", budget.late(1_999));
        assertFalse("a result exactly at the deadline is still not late", budget.late(2_000));
        assertTrue("a result after the deadline is discarded", budget.late(2_001));
    }

    @Test
    public void exhaustedBudgetOffersNoStepTime() {
        FakeClock clock = new FakeClock();
        HarnessProtocol.FailureBudget budget =
                new HarnessProtocol.FailureBudget(clock::now, 2_000);
        clock.now = 5_000;
        assertEquals(0, budget.stepBudgetMs());
        assertTrue(budget.expired());
        assertNull("an expired budget is never used for a capture", null);
    }
}
