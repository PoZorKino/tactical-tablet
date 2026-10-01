package moe.dexx.tacticaltablet.destruction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkWaveOrderTest {
    private static long nearestDistanceSq(long packed, int centerX, int centerZ) {
        int chunkX = ChunkWaveOrder.chunkX(packed);
        int chunkZ = ChunkWaveOrder.chunkZ(packed);
        long dx = Math.max(chunkX * 16, Math.min(centerX, chunkX * 16 + 15)) - centerX;
        long dz = Math.max(chunkZ * 16, Math.min(centerZ, chunkZ * 16 + 15)) - centerZ;
        return dx * dx + dz * dz;
    }

    @Test
    void packingMatchesTheVanillaLayoutAndSurvivesNegatives() {
        assertEquals(0L, ChunkWaveOrder.pack(0, 0));
        assertEquals(((long) 5 & 0xFFFFFFFFL) | (((long) -7 & 0xFFFFFFFFL) << 32), ChunkWaveOrder.pack(5, -7));
        long packed = ChunkWaveOrder.pack(-123456, 654321);
        assertEquals(-123456, ChunkWaveOrder.chunkX(packed));
        assertEquals(654321, ChunkWaveOrder.chunkZ(packed));
    }

    @Test
    void radiusOneInsideAChunkTouchesOnlyThatChunk() {
        assertArrayEquals(new long[] {ChunkWaveOrder.pack(0, 0)}, ChunkWaveOrder.compute(8, 8, 1));
    }

    @Test
    void radiusOneOnAChunkCornerTouchesThreeChunksButNotTheDiagonal() {
        long[] order = ChunkWaveOrder.compute(0, 0, 1);
        assertEquals(3, order.length);
        assertEquals(ChunkWaveOrder.pack(0, 0), order[0]);
        Set<Long> rest = Set.of(order[1], order[2]);
        assertTrue(rest.contains(ChunkWaveOrder.pack(-1, 0)));
        assertTrue(rest.contains(ChunkWaveOrder.pack(0, -1)));
    }

    @Test
    void negativeCoordinatesUseFloorDivision() {
        long[] order = ChunkWaveOrder.compute(-1, -1, 1);
        assertEquals(ChunkWaveOrder.pack(-1, -1), order[0]);
        assertEquals(3, order.length);
    }

    @Test
    void chunksComeInNonDecreasingDistanceFromTheCentre() {
        long[] order = ChunkWaveOrder.compute(37, -211, 300);
        long previous = -1;
        for (long packed : order) {
            long distance = nearestDistanceSq(packed, 37, -211);
            assertTrue(distance >= previous);
            assertTrue(distance <= 300L * 300L);
            previous = distance;
        }
    }

    @Test
    void maximumRadiusCoversAboutTwelveThousandUniqueChunks() {
        long[] order = ChunkWaveOrder.compute(0, 0, 1000);
        assertTrue(order.length > 12_000 && order.length < 13_200, "count=" + order.length);
        Set<Long> unique = new HashSet<>();
        for (long packed : order) {
            assertTrue(unique.add(packed));
        }
        assertEquals(ChunkWaveOrder.pack(0, 0), order[0]);
    }

    @Test
    void everyChunkWithAColumnInRangeIsIncluded() {
        long[] order = ChunkWaveOrder.compute(100, 100, 40);
        Set<Long> included = new HashSet<>();
        for (long packed : order) {
            included.add(packed);
        }
        for (int x = 60; x <= 140; x++) {
            for (int z = 60; z <= 140; z++) {
                long dx = x - 100;
                long dz = z - 100;
                if (dx * dx + dz * dz <= 1600) {
                    assertTrue(included.contains(ChunkWaveOrder.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16))));
                }
            }
        }
    }
}
