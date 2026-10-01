package moe.dexx.tacticaltablet.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrikeParamsTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static StrikeParams valid() {
        return StrikeParams.DEFAULT.withTarget(10, 64, -20);
    }

    private static StrikeParams with(int radius, int power, int salvos, int countdown) {
        StrikeParams base = valid();
        return new StrikeParams(true, base.targetX(), base.targetY(), base.targetZ(), radius, power, salvos, countdown,
                base.type(), base.destroyBlocks(), base.destroyLiquids(), base.damageEntities());
    }

    @Test
    void defaultsHaveNoTargetAndAreRejected() {
        assertFalse(StrikeParams.DEFAULT.hasTarget());
        assertEquals(StrikeParams.REJECT_NO_TARGET, StrikeParams.DEFAULT.validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void validParamsPass() {
        assertNull(valid().validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void radiusBounds() {
        assertEquals(StrikeParams.REJECT_RADIUS, with(0, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(1, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(1000, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_RADIUS, with(1001, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void serverLimitLowersTheMaximum() {
        assertNull(with(500, 5, 3, 10).validate(500, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_RADIUS, with(501, 5, 3, 10).validate(500, MIN_Y, MAX_Y));
    }

    @Test
    void serverLimitCannotRaiseTheMaximumAboveOneThousand() {
        assertEquals(1000, StrikeParams.effectiveMaxRadius(5000));
        assertEquals(1, StrikeParams.effectiveMaxRadius(-3));
        assertEquals(StrikeParams.REJECT_RADIUS, with(1001, 5, 3, 10).validate(5000, MIN_Y, MAX_Y));
    }

    @Test
    void powerSalvosAndCountdownBounds() {
        assertEquals(StrikeParams.REJECT_POWER, with(50, 0, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_POWER, with(50, 11, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_SALVOS, with(50, 5, 0, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_SALVOS, with(50, 5, 11, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_COUNTDOWN, with(50, 5, 3, 2).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_COUNTDOWN, with(50, 5, 3, 61).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(50, 1, 1, 3).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(50, 10, 10, 60).validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void targetHeightMustBeInsideTheWorld() {
        assertEquals(StrikeParams.REJECT_TARGET_HEIGHT, StrikeParams.DEFAULT.withTarget(0, -65, 0).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_TARGET_HEIGHT, StrikeParams.DEFAULT.withTarget(0, 320, 0).validate(1000, MIN_Y, MAX_Y));
        assertNull(StrikeParams.DEFAULT.withTarget(0, -64, 0).validate(1000, MIN_Y, MAX_Y));
        assertNull(StrikeParams.DEFAULT.withTarget(0, 319, 0).validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void rejectsHostileValues() {
        assertEquals(StrikeParams.REJECT_RADIUS, with(Integer.MAX_VALUE, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_RADIUS, with(Integer.MIN_VALUE, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_POWER, with(50, Integer.MIN_VALUE, 3, 10).validate(1000, MIN_Y, MAX_Y));
        StrikeParams noType = new StrikeParams(true, 0, 64, 0, 50, 5, 3, 10, null, true, true, true);
        assertEquals(StrikeParams.REJECT_TYPE, noType.validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void settingsValidationIgnoresTheTarget() {
        assertNull(StrikeParams.DEFAULT.validateSettings(1000));
        assertEquals(StrikeParams.REJECT_RADIUS, StrikeParams.DEFAULT.withRadius(1001).validateSettings(1000));
    }

    @Test
    void onlyDestructiveOrbitalStrikesModifyTheWorld() {
        assertTrue(valid().modifiesWorld());
        assertFalse(valid().withType(StrikeType.VISUAL_ONLY).modifiesWorld());
        StrikeParams keepBlocks = new StrikeParams(true, 0, 64, 0, 50, 5, 3, 10, StrikeType.ORBITAL_LASER, false, true, true);
        assertFalse(keepBlocks.modifiesWorld());
    }

    @Test
    void withersChangeOnlyTheirField() {
        StrikeParams changed = valid().withRadius(77).withPower(9);
        assertEquals(77, changed.radius());
        assertEquals(9, changed.power());
        assertEquals(valid().targetX(), changed.targetX());
        assertEquals(valid().salvos(), changed.salvos());
    }
}
