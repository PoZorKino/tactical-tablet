package moe.dexx.tacticaltablet.destruction;

import java.util.Arrays;

/** Lists the chunks a strike touches, nearest to the target first, so destruction spreads as a wave. */
public final class ChunkWaveOrder {
    private ChunkWaveOrder() {
    }

    /** Same layout as ChunkPos.asLong. */
    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int chunkX(long packed) {
        return (int) packed;
    }

    public static int chunkZ(long packed) {
        return (int) (packed >> 32);
    }

    public static long[] compute(int centerBlockX, int centerBlockZ, int radius) {
        int minChunkX = Math.floorDiv(centerBlockX - radius, 16);
        int maxChunkX = Math.floorDiv(centerBlockX + radius, 16);
        int minChunkZ = Math.floorDiv(centerBlockZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerBlockZ + radius, 16);
        int width = maxChunkX - minChunkX + 1;
        int depth = maxChunkZ - minChunkZ + 1;
        long radiusSq = (long) radius * radius;

        // Sort key: distance in the high bits, grid index in the low 24 bits (the grid has at most 127 * 127 cells).
        long[] keys = new long[width * depth];
        int count = 0;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long dx = Math.max(chunkX * 16, Math.min(centerBlockX, chunkX * 16 + 15)) - centerBlockX;
                long dz = Math.max(chunkZ * 16, Math.min(centerBlockZ, chunkZ * 16 + 15)) - centerBlockZ;
                long distanceSq = dx * dx + dz * dz;
                if (distanceSq > radiusSq) {
                    continue;
                }
                int index = (chunkZ - minChunkZ) * width + (chunkX - minChunkX);
                keys[count++] = (distanceSq << 24) | index;
            }
        }
        long[] sorted = Arrays.copyOf(keys, count);
        Arrays.sort(sorted);

        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            int index = (int) (sorted[i] & 0xFFFFFFL);
            result[i] = pack(minChunkX + index % width, minChunkZ + index / width);
        }
        return result;
    }
}
