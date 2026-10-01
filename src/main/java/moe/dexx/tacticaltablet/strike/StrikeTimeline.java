package moe.dexx.tacticaltablet.strike;

public final class StrikeTimeline {
    public static final int CHARGE_TICKS = 400;
    public static final int TRAVEL_TICKS = 80;
    public static final int IMPACT_BASE_TICKS = 120;
    public static final int IMPACT_TICKS_PER_EXTRA_SALVO = 8;

    private StrikeTimeline() {
    }

    /** @return the fixed length of the phase in ticks, or -1 when the phase lasts until its work is done */
    public static int durationTicks(StrikePhase phase, StrikeParams params) {
        return switch (phase) {
            case COUNTDOWN -> params.countdownSeconds() * 20;
            case CHARGE -> CHARGE_TICKS;
            case TRAVEL -> TRAVEL_TICKS;
            case IMPACT -> IMPACT_BASE_TICKS + IMPACT_TICKS_PER_EXTRA_SALVO * (params.salvos() - 1);
            case DESTROYING, DONE -> -1;
        };
    }

    /** @param jobRunning whether a destruction job is still working when the phase ends */
    public static StrikePhase next(StrikePhase phase, boolean jobRunning) {
        return switch (phase) {
            case COUNTDOWN -> StrikePhase.CHARGE;
            case CHARGE -> StrikePhase.TRAVEL;
            case TRAVEL -> StrikePhase.IMPACT;
            case IMPACT -> jobRunning ? StrikePhase.DESTROYING : StrikePhase.DONE;
            case DESTROYING, DONE -> StrikePhase.DONE;
        };
    }
}
