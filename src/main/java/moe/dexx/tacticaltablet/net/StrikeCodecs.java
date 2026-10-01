package moe.dexx.tacticaltablet.net;

import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.network.FriendlyByteBuf;

/** Wire format shared by the server and the client. Numbers are sent as-is; the server validates them. */
public final class StrikeCodecs {
    private StrikeCodecs() {
    }

    public static void writeParams(FriendlyByteBuf buf, StrikeParams params) {
        buf.writeBoolean(params.hasTarget());
        buf.writeInt(params.targetX());
        buf.writeInt(params.targetY());
        buf.writeInt(params.targetZ());
        buf.writeInt(params.radius());
        buf.writeInt(params.power());
        buf.writeInt(params.salvos());
        buf.writeInt(params.countdownSeconds());
        buf.writeEnum(params.type());
        buf.writeBoolean(params.destroyBlocks());
        buf.writeBoolean(params.destroyLiquids());
        buf.writeBoolean(params.damageEntities());
    }

    public static StrikeParams readParams(FriendlyByteBuf buf) {
        return new StrikeParams(
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readEnum(StrikeType.class),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void writeState(FriendlyByteBuf buf, StrikeState state) {
        buf.writeUUID(state.id());
        buf.writeUUID(state.owner());
        buf.writeEnum(state.phase());
        buf.writeLong(state.phaseStartTick());
        buf.writeInt(state.durationTicks());
        buf.writeBlockPos(state.target());
        buf.writeInt(state.radius());
        buf.writeInt(state.salvos());
        buf.writeEnum(state.type());
        buf.writeBoolean(state.modifiesWorld());
    }

    public static StrikeState readState(FriendlyByteBuf buf) {
        return new StrikeState(buf.readUUID(), buf.readUUID(), buf.readEnum(StrikePhase.class), buf.readLong(), buf.readInt(),
                buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readEnum(StrikeType.class), buf.readBoolean());
    }
}
