package moe.dexx.tacticaltablet.destruction;

/** Time the destruction jobs may spend per tick; shrinks while the server is slow and recovers afterwards. */
public final class TickBudget {
    public static final double SLOW_MS = 60.0;
    public static final double RECOVER_MS = 45.0;
    public static final long MIN_NANOS = 2_000_000L;

    private final long fullNanos;
    private long currentNanos;

    public TickBudget(int budgetMs) {
        this.fullNanos = Math.max(MIN_NANOS, budgetMs * 1_000_000L);
        this.currentNanos = fullNanos;
    }

    public long update(double averageTickMs) {
        if (averageTickMs > SLOW_MS) {
            currentNanos = Math.max(MIN_NANOS, currentNanos / 2);
        } else if (averageTickMs < RECOVER_MS) {
            currentNanos = fullNanos;
        }
        return currentNanos;
    }

    public long currentNanos() {
        return currentNanos;
    }
}
