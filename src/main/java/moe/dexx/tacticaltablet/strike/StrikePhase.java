package moe.dexx.tacticaltablet.strike;

public enum StrikePhase {
    COUNTDOWN(true),
    CHARGE(true),
    /** The shot has been fired: from here on the strike cannot be cancelled. */
    TRAVEL(false),
    IMPACT(false),
    DESTROYING(false),
    DONE(false);

    private final boolean cancellable;

    StrikePhase(boolean cancellable) {
        this.cancellable = cancellable;
    }

    public boolean cancellable() {
        return cancellable;
    }
}
