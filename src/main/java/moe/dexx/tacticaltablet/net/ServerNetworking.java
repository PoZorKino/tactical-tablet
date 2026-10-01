package moe.dexx.tacticaltablet.net;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.strike.AbortReason;
import moe.dexx.tacticaltablet.strike.PlayerChecks;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server side of the protocol. Every handler repeats the full validation: the client is never trusted. */
public final class ServerNetworking {
    public static final String REJECT_CANNOT_CANCEL = "tactical_tablet.reject.cannot_cancel";
    public static final String REJECT_NOTHING_TO_STOP = "tactical_tablet.reject.nothing_to_stop";

    private ServerNetworking() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.UPDATE_SETTINGS, (server, player, handler, buf, sender) -> {
            StrikeParams params = StrikeCodecs.readParams(buf);
            server.execute(() -> handleUpdateSettings(player, params));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.LAUNCH_REQUEST, (server, player, handler, buf, sender) -> {
            StrikeParams params = StrikeCodecs.readParams(buf);
            server.execute(() -> handleLaunch(player, params));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.CANCEL_REQUEST, (server, player, handler, buf, sender) -> {
            boolean resourceFailure = buf.readBoolean();
            server.execute(() -> handleCancel(player, resourceFailure));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.EMERGENCY_STOP, (server, player, handler, buf, sender) ->
                server.execute(() -> handleEmergencyStop(player)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendActiveStrikes(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendActiveStrikes(player));
    }

    public static void handleUpdateSettings(ServerPlayer player, StrikeParams params) {
        ItemStack tablet = PlayerChecks.heldTablet(player);
        if (tablet.isEmpty()) {
            sendRejected(player, PlayerChecks.REJECT_NO_TABLET);
            return;
        }
        String reject = params.validateSettings(TacticalTablet.config().maxRadius);
        if (reject != null) {
            sendRejected(player, reject);
            return;
        }
        TabletSettings.write(tablet, params);
    }

    public static void handleLaunch(ServerPlayer player, StrikeParams params) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        String reject = PlayerChecks.launchReject(player, TacticalTablet.config());
        if (reject == null) {
            reject = manager.launch(player.getUUID(), player.serverLevel(), params);
        }
        if (reject != null) {
            sendRejected(player, reject);
            return;
        }
        TabletSettings.write(PlayerChecks.heldTablet(player), params);
    }

    public static void handleCancel(ServerPlayer player, boolean resourceFailure) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        String reason = resourceFailure ? AbortReason.RESOURCES : AbortReason.CANCELLED;
        if (!manager.cancel(player.getUUID(), reason)) {
            sendRejected(player, REJECT_CANNOT_CANCEL);
        }
    }

    public static void handleEmergencyStop(ServerPlayer player) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        if (!manager.emergencyStop(player.getUUID())) {
            sendRejected(player, REJECT_NOTHING_TO_STOP);
        }
    }

    public static StrikeManager.Listener listener(MinecraftServer server) {
        return new StrikeManager.Listener() {
            @Override
            public void onState(Strike strike) {
                broadcast(server, strike, ModPackets.STRIKE_STATE, buf -> StrikeCodecs.writeState(buf, StrikeState.of(strike)));
            }

            @Override
            public void onAbort(Strike strike, String reasonKey) {
                broadcast(server, strike, ModPackets.STRIKE_ABORT, buf -> {
                    buf.writeUUID(strike.id());
                    buf.writeUtf(reasonKey);
                });
            }

            @Override
            public void onProgress(Strike strike, int done, int total) {
                ServerPlayer owner = server.getPlayerList().getPlayer(strike.owner());
                if (owner != null) {
                    send(owner, ModPackets.STRIKE_PROGRESS, buf -> {
                        buf.writeUUID(strike.id());
                        buf.writeVarInt(done);
                        buf.writeVarInt(total);
                    });
                }
            }
        };
    }

    /** Everyone in the strike's dimension sees it; the owner is told wherever they are. */
    private static void broadcast(MinecraftServer server, Strike strike, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        ServerLevel level = server.getLevel(strike.dimension());
        if (level != null) {
            recipients.addAll(level.players());
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(strike.owner());
        if (owner != null) {
            recipients.add(owner);
        }
        for (ServerPlayer recipient : recipients) {
            send(recipient, channel, writer);
        }
    }

    private static void sendActiveStrikes(ServerPlayer player) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        for (Strike strike : manager.active()) {
            if (strike.dimension() == player.level().dimension() || strike.owner().equals(player.getUUID())) {
                send(player, ModPackets.STRIKE_STATE, buf -> StrikeCodecs.writeState(buf, StrikeState.of(strike)));
            }
        }
    }

    private static void sendRejected(ServerPlayer player, String reasonKey) {
        send(player, ModPackets.LAUNCH_REJECTED, buf -> buf.writeUtf(reasonKey));
    }

    private static void send(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writer.accept(buf);
        ServerPlayNetworking.send(player, channel, buf);
    }
}
