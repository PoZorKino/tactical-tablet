package moe.dexx.tacticaltablet.strike;

/**
 * Everything a player can configure about one strike. Validation returns the translation key of the first
 * violated rule, or null when the parameters are acceptable; out-of-range values are rejected, never clamped.
 */
public record StrikeParams(
        boolean hasTarget, int targetX, int targetY, int targetZ,
        int radius, int power, int salvos, int countdownSeconds,
        StrikeType type, boolean destroyBlocks, boolean destroyLiquids, boolean damageEntities) {

    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 1000;
    public static final int MIN_POWER = 1;
    public static final int MAX_POWER = 10;
    public static final int MIN_SALVOS = 1;
    public static final int MAX_SALVOS = 10;
    public static final int MIN_COUNTDOWN = 3;
    public static final int MAX_COUNTDOWN = 60;

    public static final String REJECT_NO_TARGET = "tactical_tablet.reject.no_target";
    public static final String REJECT_TARGET_HEIGHT = "tactical_tablet.reject.target_height";
    public static final String REJECT_RADIUS = "tactical_tablet.reject.radius";
    public static final String REJECT_POWER = "tactical_tablet.reject.power";
    public static final String REJECT_SALVOS = "tactical_tablet.reject.salvos";
    public static final String REJECT_COUNTDOWN = "tactical_tablet.reject.countdown";
    public static final String REJECT_TYPE = "tactical_tablet.reject.type";

    public static final StrikeParams DEFAULT =
            new StrikeParams(false, 0, 0, 0, 50, 5, 3, 10, StrikeType.ORBITAL_LASER, true, true, true);

    public static int effectiveMaxRadius(int serverMaxRadius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, serverMaxRadius));
    }

    public String validateSettings(int serverMaxRadius) {
        if (type == null) {
            return REJECT_TYPE;
        }
        if (radius < MIN_RADIUS || radius > effectiveMaxRadius(serverMaxRadius)) {
            return REJECT_RADIUS;
        }
        if (power < MIN_POWER || power > MAX_POWER) {
            return REJECT_POWER;
        }
        if (salvos < MIN_SALVOS || salvos > MAX_SALVOS) {
            return REJECT_SALVOS;
        }
        if (countdownSeconds < MIN_COUNTDOWN || countdownSeconds > MAX_COUNTDOWN) {
            return REJECT_COUNTDOWN;
        }
        return null;
    }

    public String validate(int serverMaxRadius, int minBuildHeight, int maxBuildHeight) {
        if (!hasTarget) {
            return REJECT_NO_TARGET;
        }
        if (targetY < minBuildHeight || targetY >= maxBuildHeight) {
            return REJECT_TARGET_HEIGHT;
        }
        return validateSettings(serverMaxRadius);
    }

    public boolean modifiesWorld() {
        return type == StrikeType.ORBITAL_LASER && destroyBlocks;
    }

    public StrikeParams withTarget(int x, int y, int z) {
        return new StrikeParams(true, x, y, z, radius, power, salvos, countdownSeconds,
                type, destroyBlocks, destroyLiquids, damageEntities);
    }

    public StrikeParams withRadius(int newRadius) {
        return new StrikeParams(hasTarget, targetX, targetY, targetZ, newRadius, power, salvos, countdownSeconds,
                type, destroyBlocks, destroyLiquids, damageEntities);
    }

    public StrikeParams withPower(int newPower) {
        return new StrikeParams(hasTarget, targetX, targetY, targetZ, radius, newPower, salvos, countdownSeconds,
                type, destroyBlocks, destroyLiquids, damageEntities);
    }

    public StrikeParams withType(StrikeType newType) {
        return new StrikeParams(hasTarget, targetX, targetY, targetZ, radius, power, salvos, countdownSeconds,
                newType, destroyBlocks, destroyLiquids, damageEntities);
    }
}
