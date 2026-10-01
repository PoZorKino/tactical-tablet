package moe.dexx.tacticaltablet.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class StrikeCodecsTest {
    @Test
    void paramsSurviveARoundTrip() {
        StrikeParams params = new StrikeParams(true, -30_000_000, -64, 29_999_999, 1000, 10, 10, 60,
                StrikeType.VISUAL_ONLY, false, true, false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StrikeCodecs.writeParams(buf, params);
        assertEquals(params, StrikeCodecs.readParams(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void outOfRangeNumbersAreCarriedThroughForTheValidatorToReject() {
        StrikeParams hostile = new StrikeParams(true, 0, 64, 0, Integer.MAX_VALUE, -5, 0, 100_000,
                StrikeType.ORBITAL_LASER, true, true, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StrikeCodecs.writeParams(buf, hostile);
        StrikeParams read = StrikeCodecs.readParams(buf);
        assertEquals(hostile, read);
        assertEquals(StrikeParams.REJECT_RADIUS, read.validate(1000, -64, 320));
    }

    @Test
    void stateSurvivesARoundTrip() {
        StrikeState state = new StrikeState(UUID.randomUUID(), UUID.randomUUID(), StrikePhase.DESTROYING, 123_456_789_012L, -1,
                new BlockPos(-1234, 70, 5678), 1000, 7, StrikeType.ORBITAL_LASER, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StrikeCodecs.writeState(buf, state);
        assertEquals(state, StrikeCodecs.readState(buf));
        assertEquals(0, buf.readableBytes());
    }
}
