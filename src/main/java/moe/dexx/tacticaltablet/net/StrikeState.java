package moe.dexx.tacticaltablet.net;

import java.util.UUID;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeTimeline;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.core.BlockPos;

/** What a client needs to know about a strike. phaseStartTick is Level.getGameTime, which clients share. */
public record StrikeState(UUID id, UUID owner, StrikePhase phase, long phaseStartTick, int durationTicks,
                          BlockPos target, int radius, int salvos, StrikeType type, boolean modifiesWorld) {
    public static StrikeState of(Strike strike) {
        return new StrikeState(strike.id(), strike.owner(), strike.phase(), strike.phaseStartTick(),
                StrikeTimeline.durationTicks(strike.phase(), strike.params()), strike.target(),
                strike.params().radius(), strike.params().salvos(), strike.params().type(), strike.params().modifiesWorld());
    }
}
