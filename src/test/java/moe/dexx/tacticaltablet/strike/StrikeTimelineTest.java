package moe.dexx.tacticaltablet.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrikeTimelineTest {
    private static StrikeParams params(int salvos, int countdown) {
        return new StrikeParams(true, 0, 64, 0, 50, 5, salvos, countdown, StrikeType.ORBITAL_LASER, true, true, true);
    }

    @Test
    void onlyThePhasesBeforeTheShotAreCancellable() {
        assertTrue(StrikePhase.COUNTDOWN.cancellable());
        assertTrue(StrikePhase.CHARGE.cancellable());
        assertFalse(StrikePhase.TRAVEL.cancellable());
        assertFalse(StrikePhase.IMPACT.cancellable());
        assertFalse(StrikePhase.DESTROYING.cancellable());
        assertFalse(StrikePhase.DONE.cancellable());
    }

    @Test
    void countdownLastsTheConfiguredSeconds() {
        assertEquals(60, StrikeTimeline.durationTicks(StrikePhase.COUNTDOWN, params(1, 3)));
        assertEquals(1200, StrikeTimeline.durationTicks(StrikePhase.COUNTDOWN, params(1, 60)));
    }

    @Test
    void chargeAndTravelAreFixed() {
        assertEquals(400, StrikeTimeline.durationTicks(StrikePhase.CHARGE, params(3, 10)));
        assertEquals(80, StrikeTimeline.durationTicks(StrikePhase.TRAVEL, params(3, 10)));
    }

    @Test
    void impactGrowsWithExtraSalvos() {
        assertEquals(120, StrikeTimeline.durationTicks(StrikePhase.IMPACT, params(1, 10)));
        assertEquals(136, StrikeTimeline.durationTicks(StrikePhase.IMPACT, params(3, 10)));
        assertEquals(192, StrikeTimeline.durationTicks(StrikePhase.IMPACT, params(10, 10)));
    }

    @Test
    void openEndedPhasesHaveNoDuration() {
        assertEquals(-1, StrikeTimeline.durationTicks(StrikePhase.DESTROYING, params(3, 10)));
        assertEquals(-1, StrikeTimeline.durationTicks(StrikePhase.DONE, params(3, 10)));
    }

    @Test
    void phasesFollowTheScenario() {
        assertEquals(StrikePhase.CHARGE, StrikeTimeline.next(StrikePhase.COUNTDOWN, false));
        assertEquals(StrikePhase.TRAVEL, StrikeTimeline.next(StrikePhase.CHARGE, false));
        assertEquals(StrikePhase.IMPACT, StrikeTimeline.next(StrikePhase.TRAVEL, false));
        assertEquals(StrikePhase.DESTROYING, StrikeTimeline.next(StrikePhase.IMPACT, true));
        assertEquals(StrikePhase.DONE, StrikeTimeline.next(StrikePhase.IMPACT, false));
        assertEquals(StrikePhase.DONE, StrikeTimeline.next(StrikePhase.DESTROYING, false));
        assertEquals(StrikePhase.DONE, StrikeTimeline.next(StrikePhase.DONE, true));
    }
}
