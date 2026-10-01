package moe.dexx.tacticaltablet.client.target;

import moe.dexx.tacticaltablet.client.net.ClientNetworking;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.strike.PlayerChecks;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The tablet the local player is holding and its settings, cached so they can be read every frame. */
public final class HeldTablet {
    private static CompoundTag cachedTag;
    private static StrikeParams cachedParams = StrikeParams.DEFAULT;

    private HeldTablet() {
    }

    /** @return the held tablet's settings, or null when the player holds no tablet */
    public static StrikeParams params(Player player) {
        ItemStack stack = PlayerChecks.heldTablet(player);
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getTagElement(TabletSettings.ROOT);
        if (tag == null) {
            return StrikeParams.DEFAULT;
        }
        if (tag != cachedTag) {
            cachedTag = tag;
            cachedParams = TabletSettings.read(stack);
        }
        return cachedParams;
    }

    /** Stores the settings on the held tablet: locally at once, and on the server, which is authoritative. */
    public static void save(Player player, StrikeParams params) {
        ItemStack stack = PlayerChecks.heldTablet(player);
        if (stack.isEmpty()) {
            return;
        }
        TabletSettings.write(stack, params);
        cachedTag = null;
        ClientNetworking.sendSettings(params);
    }
}
