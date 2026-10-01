package moe.dexx.tacticaltablet.client.target;

import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.strike.PlayerChecks;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Target picking in the world. While active, using the tablet fixes the block under the crosshair as the target
 * instead of opening the screen. Only blocks in chunks the client has loaded can be hit.
 */
public final class AimMode {
    public static final double LONG_RANGE = 512.0;

    private static boolean active;
    private static boolean longRange;
    private static boolean attackReleased;

    private AimMode() {
    }

    public static void start(boolean useLongRange) {
        active = true;
        longRange = useLongRange;
        attackReleased = false;
    }

    public static void stop() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    /** @return the block under the crosshair within range, or null */
    public static BlockPos aimedBlock(Minecraft minecraft, float partialTick) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return null;
        }
        double range = longRange ? LONG_RANGE : minecraft.gameMode.getPickRange();
        HitResult hit = minecraft.player.pick(range, partialTick, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return ((BlockHitResult) hit).getBlockPos();
    }

    /** Fixes the aimed block as the target. Leaves aim mode only when a block was actually picked. */
    public static void pick(Minecraft minecraft) {
        BlockPos pos = aimedBlock(minecraft, 1.0F);
        if (pos == null || minecraft.player == null) {
            ClientStrikes.notify(Component.translatable("tactical_tablet.hud.aim.none"), true);
            return;
        }
        StrikeParams params = HeldTablet.params(minecraft.player);
        if (params == null) {
            stop();
            return;
        }
        HeldTablet.save(minecraft.player, params.withTarget(pos.getX(), pos.getY(), pos.getZ()));
        ClientStrikes.notify(Component.translatable("tactical_tablet.hud.target_set", pos.getX(), pos.getY(), pos.getZ()), false);
        stop();
    }

    /** Called every client tick: leaves aim mode when the player opens a screen, attacks or puts the tablet away. */
    public static void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft.player == null || minecraft.screen != null || PlayerChecks.heldTablet(minecraft.player).isEmpty()) {
            stop();
            return;
        }
        // The click that pressed the tablet's "aim" button may still be held when the screen closes;
        // only an attack that starts after it was released cancels aim mode.
        if (!minecraft.options.keyAttack.isDown()) {
            attackReleased = true;
        } else if (attackReleased) {
            stop();
        }
    }
}
