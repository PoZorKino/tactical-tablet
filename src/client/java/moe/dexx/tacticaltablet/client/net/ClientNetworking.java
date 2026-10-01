package moe.dexx.tacticaltablet.client.net;

import java.util.UUID;
import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.net.ModPackets;
import moe.dexx.tacticaltablet.net.StrikeCodecs;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** Client side of the strike protocol; the wire format lives in StrikeCodecs. */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.STRIKE_STATE, (client, handler, buf, sender) -> {
            StrikeState state = StrikeCodecs.readState(buf);
            client.execute(() -> ClientStrikes.onState(state));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.STRIKE_ABORT, (client, handler, buf, sender) -> {
            UUID id = buf.readUUID();
            String reason = buf.readUtf();
            client.execute(() -> ClientStrikes.onAbort(id, reason));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.STRIKE_PROGRESS, (client, handler, buf, sender) -> {
            UUID id = buf.readUUID();
            int done = buf.readVarInt();
            int total = buf.readVarInt();
            client.execute(() -> ClientStrikes.onProgress(id, done, total));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.LAUNCH_REJECTED, (client, handler, buf, sender) -> {
            String reason = buf.readUtf();
            client.execute(() -> ClientStrikes.onRejected(reason));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientStrikes::clear));
    }

    public static void sendSettings(StrikeParams params) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        StrikeCodecs.writeParams(buf, params);
        ClientPlayNetworking.send(ModPackets.UPDATE_SETTINGS, buf);
    }

    public static void sendLaunch(StrikeParams params) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        StrikeCodecs.writeParams(buf, params);
        ClientPlayNetworking.send(ModPackets.LAUNCH_REQUEST, buf);
    }

    public static void sendCancel(boolean resourceFailure) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(resourceFailure);
        ClientPlayNetworking.send(ModPackets.CANCEL_REQUEST, buf);
    }

    public static void sendEmergencyStop() {
        ClientPlayNetworking.send(ModPackets.EMERGENCY_STOP, PacketByteBufs.create());
    }
}
