package moe.dexx.tacticaltablet.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import org.junit.jupiter.api.Test;

class CinematicTimelineTest {
    @Test
    void secondsAreCountedFromTheStartOfTheCharge() {
        assertEquals(-1.0, CinematicTimeline.seconds(StrikePhase.COUNTDOWN, 30), 1e-9);
        assertEquals(0.0, CinematicTimeline.seconds(StrikePhase.CHARGE, 0), 1e-9);
        assertEquals(7.5, CinematicTimeline.seconds(StrikePhase.CHARGE, 150), 1e-9);
        assertEquals(20.0, CinematicTimeline.seconds(StrikePhase.TRAVEL, 0), 1e-9);
        assertEquals(24.0, CinematicTimeline.seconds(StrikePhase.IMPACT, 0), 1e-9);
        assertEquals(25.0, CinematicTimeline.seconds(StrikePhase.IMPACT, 20), 1e-9);
        assertEquals(-1.0, CinematicTimeline.seconds(StrikePhase.DESTROYING, 5), 1e-9);
        assertEquals(-1.0, CinematicTimeline.seconds(StrikePhase.DONE, 0), 1e-9);
    }

    @Test
    void theShotLeavesTheCannonExactlyWhenTheServerEntersTravel() {
        assertEquals(StrikeTimeline.CHARGE_TICKS / 20.0, CinematicTimeline.CANNON_END, 1e-9);
        assertEquals(Segment.CANNON, CinematicTimeline.segment(19.99, 1));
        assertEquals(Segment.FIRE, CinematicTimeline.segment(20.0, 1));
    }

    @Test
    void segmentsFollowTheScenarioInOrder() {
        assertEquals(Segment.PULL_AWAY, CinematicTimeline.segment(0.0, 1));
        assertEquals(Segment.PULL_AWAY, CinematicTimeline.segment(2.99, 1));
        assertEquals(Segment.ASCENT, CinematicTimeline.segment(3.0, 1));
        assertEquals(Segment.JUPITER, CinematicTimeline.segment(6.0, 1));
        assertEquals(Segment.SATURN, CinematicTimeline.segment(10.0, 1));
        assertEquals(Segment.CANNON, CinematicTimeline.segment(14.0, 1));
        assertEquals(Segment.DESCENT, CinematicTimeline.segment(21.5, 1));
        assertEquals(Segment.IMPACT, CinematicTimeline.segment(24.0, 1));
        assertEquals(Segment.IMPACT, CinematicTimeline.segment(27.99, 1));
        assertEquals(Segment.RETURN, CinematicTimeline.segment(28.0, 1));
        assertEquals(Segment.OVER, CinematicTimeline.segment(30.0, 1));
        assertEquals(Segment.OVER, CinematicTimeline.segment(-1.0, 1));
    }

    @Test
    void extraSalvosLengthenTheImpactButNotTheReturn() {
        assertEquals(6.0, CinematicTimeline.impactSeconds(1), 1e-9);
        assertEquals(9.6, CinematicTimeline.impactSeconds(10), 1e-9);
        assertEquals(33.6, CinematicTimeline.endSeconds(10), 1e-9);
        assertEquals(Segment.IMPACT, CinematicTimeline.segment(31.5, 10));
        assertEquals(Segment.RETURN, CinematicTimeline.segment(31.7, 10));
        assertEquals(Segment.OVER, CinematicTimeline.segment(33.6, 10));
    }

    @Test
    void onlyTheMiddleShotsAreInSpace() {
        assertFalse(Segment.PULL_AWAY.inSpace());
        assertFalse(Segment.ASCENT.inSpace());
        assertTrue(Segment.JUPITER.inSpace());
        assertTrue(Segment.SATURN.inSpace());
        assertTrue(Segment.CANNON.inSpace());
        assertTrue(Segment.FIRE.inSpace());
        assertTrue(Segment.DESCENT.inSpace());
        assertFalse(Segment.IMPACT.inSpace());
        assertFalse(Segment.RETURN.inSpace());
    }

    @Test
    void progressRunsFromZeroToOneInsideEachSegment() {
        assertEquals(0.0, CinematicTimeline.progress(3.0, 1), 1e-9);
        assertEquals(0.5, CinematicTimeline.progress(4.5, 1), 1e-9);
        assertEquals(0.5, CinematicTimeline.progress(8.0, 1), 1e-9);
        assertEquals(0.5, CinematicTimeline.progress(17.0, 1), 1e-9);
        assertEquals(0.5, CinematicTimeline.progress(29.0, 1), 1e-9);
        assertEquals(1.0, CinematicTimeline.progress(99.0, 1), 1e-9);
        for (double t = 0.0; t < 30.0; t += 0.05) {
            double progress = CinematicTimeline.progress(t, 1);
            assertTrue(progress >= 0.0 && progress <= 1.0, "t=" + t + " progress=" + progress);
        }
    }

    @Test
    void salvosHitAtTheStartOfTheImpactAndThenAtAFixedInterval() {
        assertEquals(24.0, CinematicTimeline.salvoHitSeconds(0), 1e-9);
        assertEquals(24.4, CinematicTimeline.salvoHitSeconds(1), 1e-9);
        assertEquals(27.6, CinematicTimeline.salvoHitSeconds(9), 1e-9);
    }
}
