package moe.dexx.tacticaltablet.destruction;

/**
 * Shape of the crater: a paraboloid that is deepest at the target and reaches zero at the radius.
 * Offsets are block columns relative to the target column.
 */
public final class CraterProfile {
    /** Craters shallower than this get no edge noise, so tiny strikes are exact. */
    private static final int NOISE_MIN_DEPTH = 4;

    private final long radiusSq;
    private final int maxDepth;
    private final long seed;
    private final boolean noisy;

    public CraterProfile(int radius, int power, long seed) {
        this.radiusSq = (long) radius * radius;
        this.maxDepth = maxDepth(radius, power);
        this.seed = seed;
        this.noisy = this.maxDepth >= NOISE_MIN_DEPTH;
    }

    public static int maxDepth(int radius, int power) {
        return Math.max(1, Math.min((int) Math.round(0.6 * radius), 8 + 12 * power));
    }

    public static int floorY(int ground, int depth, int minY) {
        return Math.max(minY, ground - depth);
    }

    public int maxDepth() {
        return maxDepth;
    }

    /** @return how many blocks to strip below the column's ground level; 0 means leave the column alone */
    public int depthAt(int dx, int dz) {
        long distanceSq = (long) dx * dx + (long) dz * dz;
        if (distanceSq > radiusSq) {
            return 0;
        }
        int depth = (int) Math.round(maxDepth * (1.0 - (double) distanceSq / (double) radiusSq));
        if (noisy) {
            depth += hash(dx, dz, 0) % 3 - 1;
        }
        return Math.max(0, depth);
    }

    /** @return true for columns inside 35% of the radius, where the crater floor is melted */
    public boolean scorched(int dx, int dz) {
        long distanceSq = (long) dx * dx + (long) dz * dz;
        return distanceSq * 10_000L < 1_225L * radiusSq;
    }

    /** Deterministic non-negative hash of a column, the strike seed and a salt. */
    public int hash(int dx, int dz, int salt) {
        long h = seed ^ (dx * 0x9E3779B97F4A7C15L) ^ (dz * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) (h & 0x7FFFFFFFL);
    }
}
