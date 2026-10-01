package moe.dexx.tacticaltablet.strike;

import moe.dexx.tacticaltablet.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Checks every tick that the player who launched a strike can still own it. */
public final class PlayerOwnerProbe implements StrikeManager.OwnerProbe {
    private final ServerConfig config;

    public PlayerOwnerProbe(ServerConfig config) {
        this.config = config;
    }

    @Override
    public String abortReason(MinecraftServer server, Strike strike) {
        ServerPlayer player = server.getPlayerList().getPlayer(strike.owner());
        if (player == null) {
            return AbortReason.PLAYER_LEFT;
        }
        if (!player.isAlive() || player.level().dimension() != strike.dimension()) {
            return AbortReason.PLAYER_UNAVAILABLE;
        }
        if (!config.allows(player.gameMode.getGameModeForPlayer().getName())) {
            return AbortReason.GAMEMODE;
        }
        return null;
    }
}
