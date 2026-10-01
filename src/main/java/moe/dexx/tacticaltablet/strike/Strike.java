package moe.dexx.tacticaltablet.strike;

import java.util.UUID;
import moe.dexx.tacticaltablet.destruction.DestructionJob;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One strike in progress. Mutated only by StrikeManager on the server thread. */
public final class Strike {
    private final UUID id;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos target;
    private final StrikeParams params;

    StrikePhase phase = StrikePhase.COUNTDOWN;
    long phaseStartTick;
    long lastProgressTick;
    DestructionJob job;

    Strike(UUID id, UUID owner, ResourceKey<Level> dimension, BlockPos target, StrikeParams params) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.target = target;
        this.params = params;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos target() {
        return target;
    }

    public StrikeParams params() {
        return params;
    }

    public StrikePhase phase() {
        return phase;
    }

    /** Game time (Level.getGameTime) at which the current phase began. */
    public long phaseStartTick() {
        return phaseStartTick;
    }

    /** The destruction job, or null before impact and for strikes that do not modify the world. */
    public DestructionJob job() {
        return job;
    }
}
