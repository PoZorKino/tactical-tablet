package moe.dexx.tacticaltablet.destruction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TickBudgetTest {
    @Test
    void trackerAveragesTheLastTwentyTicks() {
        TickTimeTracker tracker = new TickTimeTracker();
        assertEquals(0.0, tracker.averageMs(), 1e-9);
        tracker.record(10_000_000L);
        tracker.record(30_000_000L);
        assertEquals(20.0, tracker.averageMs(), 1e-9);
        for (int i = 0; i < 20; i++) {
            tracker.record(50_000_000L);
        }
        assertEquals(50.0, tracker.averageMs(), 1e-9);
    }

    @Test
    void trackerRemembersTheWorstTickUntilReset() {
        TickTimeTracker tracker = new TickTimeTracker();
        tracker.record(10_000_000L);
        tracker.record(90_000_000L);
        tracker.record(20_000_000L);
        assertEquals(90.0, tracker.maxMs(), 1e-9);
        tracker.resetMax();
        assertEquals(0.0, tracker.maxMs(), 1e-9);
    }

    @Test
    void budgetStartsFull() {
        assertEquals(15_000_000L, new TickBudget(15).currentNanos());
    }

    @Test
    void budgetHalvesWhileTheServerIsSlowDownToTheFloor() {
        TickBudget budget = new TickBudget(16);
        assertEquals(8_000_000L, budget.update(61.0));
        assertEquals(4_000_000L, budget.update(75.0));
        assertEquals(2_000_000L, budget.update(75.0));
        assertEquals(2_000_000L, budget.update(200.0));
    }

    @Test
    void budgetHoldsBetweenTheThresholdsAndRecoversBelowTheLowerOne() {
        TickBudget budget = new TickBudget(16);
        budget.update(61.0);
        assertEquals(8_000_000L, budget.update(50.0));
        assertEquals(8_000_000L, budget.update(45.0));
        assertEquals(16_000_000L, budget.update(44.9));
    }

    @Test
    void budgetNeverGoesBelowTheFloorEvenWhenConfiguredLower() {
        assertEquals(2_000_000L, new TickBudget(0).currentNanos());
    }
}
