package moe.dexx.tacticaltablet.destruction;

/** Rolling average of the last 20 server tick durations, plus the worst tick since the last reset. */
public final class TickTimeTracker {
    private static final int WINDOW = 20;

    private final long[] samples = new long[WINDOW];
    private int count;
    private int index;
    private long sum;
    private long max;

    public void record(long nanos) {
        if (count == WINDOW) {
            sum -= samples[index];
        } else {
            count++;
        }
        samples[index] = nanos;
        sum += nanos;
        index = (index + 1) % WINDOW;
        max = Math.max(max, nanos);
    }

    public double averageMs() {
        return count == 0 ? 0.0 : sum / (double) count / 1_000_000.0;
    }

    public double maxMs() {
        return max / 1_000_000.0;
    }

    public void resetMax() {
        max = 0;
    }
}
