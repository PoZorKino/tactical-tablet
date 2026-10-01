package moe.dexx.tacticaltablet.destruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraterProfileTest {
    @Test
    void maxDepthFollowsTheFormula() {
        assertEquals(1, CraterProfile.maxDepth(1, 5));
        assertEquals(2, CraterProfile.maxDepth(3, 5));
        assertEquals(30, CraterProfile.maxDepth(50, 5));
        assertEquals(20, CraterProfile.maxDepth(1000, 1));
        assertEquals(128, CraterProfile.maxDepth(1000, 10));
    }

    @Test
    void smallCraterIsExact() {
        CraterProfile profile = new CraterProfile(3, 5, 12345L);
        assertEquals(2, profile.depthAt(0, 0));
        assertEquals(2, profile.depthAt(1, 0));
        assertEquals(2, profile.depthAt(1, 1));
        assertEquals(1, profile.depthAt(2, 0));
        assertEquals(1, profile.depthAt(2, 1));
        assertEquals(0, profile.depthAt(2, 2));
        assertEquals(0, profile.depthAt(3, 0));
        assertEquals(0, profile.depthAt(3, 1));
        assertEquals(0, profile.depthAt(4, 0));
    }

    @Test
    void smallCraterIsSymmetric() {
        CraterProfile profile = new CraterProfile(3, 5, 99L);
        assertEquals(profile.depthAt(2, 1), profile.depthAt(-2, -1));
        assertEquals(profile.depthAt(2, 1), profile.depthAt(1, -2));
    }

    @Test
    void radiusOneRemovesOnlyTheCentreBlock() {
        CraterProfile profile = new CraterProfile(1, 10, 7L);
        assertEquals(1, profile.depthAt(0, 0));
        assertEquals(0, profile.depthAt(1, 0));
        assertEquals(0, profile.depthAt(0, -1));
        assertEquals(0, profile.depthAt(1, 1));
    }

    @Test
    void nothingOutsideTheRadiusIsEverTouched() {
        CraterProfile profile = new CraterProfile(1000, 10, 42L);
        for (int d = 1001; d <= 1100; d++) {
            assertEquals(0, profile.depthAt(d, 0));
            assertEquals(0, profile.depthAt(0, -d));
        }
        assertEquals(0, profile.depthAt(708, 708));
        assertEquals(0, profile.depthAt(-1000, 1));
    }

    @Test
    void largeCraterStaysWithinOneBlockOfTheParabola() {
        CraterProfile profile = new CraterProfile(1000, 10, 42L);
        assertEquals(128, profile.maxDepth());
        for (int dx = -1000; dx <= 1000; dx += 37) {
            for (int dz = -1000; dz <= 1000; dz += 41) {
                long distanceSq = (long) dx * dx + (long) dz * dz;
                if (distanceSq > 1_000_000L) {
                    continue;
                }
                long expected = Math.round(128 * (1.0 - distanceSq / 1_000_000.0));
                int actual = profile.depthAt(dx, dz);
                assertTrue(Math.abs(actual - expected) <= 1, "dx=" + dx + " dz=" + dz + " actual=" + actual);
                assertTrue(actual >= 0);
            }
        }
    }

    @Test
    void sameSeedGivesTheSameCrater() {
        CraterProfile a = new CraterProfile(200, 7, 555L);
        CraterProfile b = new CraterProfile(200, 7, 555L);
        for (int dx = -200; dx <= 200; dx += 13) {
            assertEquals(a.depthAt(dx, 17), b.depthAt(dx, 17));
            assertEquals(a.hash(dx, 3, 1), b.hash(dx, 3, 1));
        }
    }

    @Test
    void hashIsNeverNegative() {
        CraterProfile profile = new CraterProfile(50, 5, -1L);
        for (int i = -500; i <= 500; i++) {
            assertTrue(profile.hash(i, -i * 31, i & 3) >= 0);
        }
    }

    @Test
    void scorchedZoneIsTheInnerThirtyFivePercent() {
        CraterProfile profile = new CraterProfile(100, 5, 1L);
        assertTrue(profile.scorched(0, 0));
        assertTrue(profile.scorched(34, 0));
        assertFalse(profile.scorched(35, 0));
        assertFalse(profile.scorched(30, 30));
        assertFalse(profile.scorched(100, 0));
    }

    @Test
    void floorIsClampedToWorldBottom() {
        assertEquals(59, CraterProfile.floorY(64, 5, -64));
        assertEquals(-64, CraterProfile.floorY(-62, 5, -64));
        assertEquals(-64, CraterProfile.floorY(-64, 1, -64));
    }
}
