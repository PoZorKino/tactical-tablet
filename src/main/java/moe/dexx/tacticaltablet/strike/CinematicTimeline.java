package moe.dexx.tacticaltablet.strike;

/**
 * Maps the server's strike phases onto the shots of the launch cinematic. Time is measured in seconds from the
 * start of the CHARGE phase, so every client derives the same shot from the same server state.
 */
public final class CinematicTimeline {
    public enum Segment {
        /** Camera leaves the player and pulls away from the target. */
        PULL_AWAY(false),
        /** Camera climbs out of the world. */
        ASCENT(false),
        JUPITER(true),
        SATURN(true),
        /** The cannon is revealed and charges. */
        CANNON(true),
        /** The salvo leaves the cannon. */
        FIRE(true),
        /** Camera follows the beams down to the planet. */
        DESCENT(true),
        /** Side view of the target while the beams hit. */
        IMPACT(false),
        /** Camera flies back to the player. */
        RETURN(false),
        OVER(false);

        private final boolean inSpace;

        Segment(boolean inSpace) {
            this.inSpace = inSpace;
        }

        public boolean inSpace() {
            return inSpace;
        }
    }

    public static final double PULL_AWAY_END = 3.0;
    public static final double ASCENT_END = 6.0;
    public static final double JUPITER_END = 10.0;
    public static final double SATURN_END = 14.0;
    public static final double CANNON_END = StrikeTimeline.CHARGE_TICKS / 20.0;
    public static final double FIRE_END = CANNON_END + 1.5;
    public static final double DESCENT_END = CANNON_END + StrikeTimeline.TRAVEL_TICKS / 20.0;
    public static final double RETURN_SECONDS = 2.0;
    /** Seconds between two salvos hitting the target. */
    public static final double SALVO_INTERVAL = StrikeTimeline.IMPACT_TICKS_PER_EXTRA_SALVO / 20.0;

    private CinematicTimeline() {
    }

    /** @return seconds since the CHARGE phase began, or -1 when the cinematic is not running in this phase */
    public static double seconds(StrikePhase phase, double ticksIntoPhase) {
        return switch (phase) {
            case CHARGE -> ticksIntoPhase / 20.0;
            case TRAVEL -> CANNON_END + ticksIntoPhase / 20.0;
            case IMPACT -> DESCENT_END + ticksIntoPhase / 20.0;
            case COUNTDOWN, DESTROYING, DONE -> -1.0;
        };
    }

    /** Length of the IMPACT phase in seconds for the given number of salvos. */
    public static double impactSeconds(int salvos) {
        return (StrikeTimeline.IMPACT_BASE_TICKS + StrikeTimeline.IMPACT_TICKS_PER_EXTRA_SALVO * (salvos - 1)) / 20.0;
    }

    /** Second at which the cinematic ends. */
    public static double endSeconds(int salvos) {
        return DESCENT_END + impactSeconds(salvos);
    }

    public static Segment segment(double seconds, int salvos) {
        if (seconds < 0.0 || seconds >= endSeconds(salvos)) {
            return Segment.OVER;
        }
        if (seconds < PULL_AWAY_END) {
            return Segment.PULL_AWAY;
        }
        if (seconds < ASCENT_END) {
            return Segment.ASCENT;
        }
        if (seconds < JUPITER_END) {
            return Segment.JUPITER;
        }
        if (seconds < SATURN_END) {
            return Segment.SATURN;
        }
        if (seconds < CANNON_END) {
            return Segment.CANNON;
        }
        if (seconds < FIRE_END) {
            return Segment.FIRE;
        }
        if (seconds < DESCENT_END) {
            return Segment.DESCENT;
        }
        if (seconds < endSeconds(salvos) - RETURN_SECONDS) {
            return Segment.IMPACT;
        }
        return Segment.RETURN;
    }

    /** @return how far the current segment has progressed, from 0 at its start to 1 at its end */
    public static double progress(double seconds, int salvos) {
        double end = endSeconds(salvos);
        return switch (segment(seconds, salvos)) {
            case PULL_AWAY -> seconds / PULL_AWAY_END;
            case ASCENT -> (seconds - PULL_AWAY_END) / (ASCENT_END - PULL_AWAY_END);
            case JUPITER -> (seconds - ASCENT_END) / (JUPITER_END - ASCENT_END);
            case SATURN -> (seconds - JUPITER_END) / (SATURN_END - JUPITER_END);
            case CANNON -> (seconds - SATURN_END) / (CANNON_END - SATURN_END);
            case FIRE -> (seconds - CANNON_END) / (FIRE_END - CANNON_END);
            case DESCENT -> (seconds - FIRE_END) / (DESCENT_END - FIRE_END);
            case IMPACT -> (seconds - DESCENT_END) / (end - RETURN_SECONDS - DESCENT_END);
            case RETURN -> (seconds - (end - RETURN_SECONDS)) / RETURN_SECONDS;
            case OVER -> 1.0;
        };
    }

    /** Second at which salvo number index (0-based) hits the target. */
    public static double salvoHitSeconds(int index) {
        return DESCENT_END + index * SALVO_INTERVAL;
    }
}
