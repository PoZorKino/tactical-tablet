package moe.dexx.tacticaltablet.strike;

import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerChecks {
    public static final String REJECT_NO_TABLET = "tactical_tablet.reject.no_tablet";
    public static final String REJECT_GAMEMODE = "tactical_tablet.reject.gamemode";

    private PlayerChecks() {
    }

    /** @return the tablet the player holds (main hand first), or ItemStack.EMPTY */
    public static ItemStack heldTablet(Player player) {
        if (player.getMainHandItem().is(ModItems.TACTICAL_TABLET)) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(ModItems.TACTICAL_TABLET)) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /** @return the translation key of the reason this player may not launch, or null */
    public static String launchReject(ServerPlayer player, ServerConfig config) {
        if (heldTablet(player).isEmpty()) {
            return REJECT_NO_TABLET;
        }
        if (!config.allows(player.gameMode.getGameModeForPlayer().getName())) {
            return REJECT_GAMEMODE;
        }
        return null;
    }
}
