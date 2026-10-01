package moe.dexx.tacticaltablet.item;

import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Strike settings stored on the tablet stack. Reading tolerates any NBT a player can produce with /give:
 * wrong types and missing keys fall back to the defaults, numbers are brought into range.
 */
public final class TabletSettings {
    public static final String ROOT = "TabletSettings";

    private TabletSettings() {
    }

    public static StrikeParams read(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT);
        if (tag == null) {
            return StrikeParams.DEFAULT;
        }
        StrikeParams defaults = StrikeParams.DEFAULT;
        return new StrikeParams(
                readBoolean(tag, "HasTarget", false),
                tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"),
                readInt(tag, "Radius", defaults.radius(), StrikeParams.MIN_RADIUS, StrikeParams.MAX_RADIUS),
                readInt(tag, "Power", defaults.power(), StrikeParams.MIN_POWER, StrikeParams.MAX_POWER),
                readInt(tag, "Salvos", defaults.salvos(), StrikeParams.MIN_SALVOS, StrikeParams.MAX_SALVOS),
                readInt(tag, "Countdown", defaults.countdownSeconds(), StrikeParams.MIN_COUNTDOWN, StrikeParams.MAX_COUNTDOWN),
                readType(tag, defaults.type()),
                readBoolean(tag, "DestroyBlocks", defaults.destroyBlocks()),
                readBoolean(tag, "DestroyLiquids", defaults.destroyLiquids()),
                readBoolean(tag, "DamageEntities", defaults.damageEntities()));
    }

    public static void write(ItemStack stack, StrikeParams params) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT);
        tag.putBoolean("HasTarget", params.hasTarget());
        tag.putInt("X", params.targetX());
        tag.putInt("Y", params.targetY());
        tag.putInt("Z", params.targetZ());
        tag.putInt("Radius", params.radius());
        tag.putInt("Power", params.power());
        tag.putInt("Salvos", params.salvos());
        tag.putInt("Countdown", params.countdownSeconds());
        tag.putString("Type", params.type().name());
        tag.putBoolean("DestroyBlocks", params.destroyBlocks());
        tag.putBoolean("DestroyLiquids", params.destroyLiquids());
        tag.putBoolean("DamageEntities", params.damageEntities());
    }

    private static int readInt(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    private static boolean readBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    private static StrikeType readType(CompoundTag tag, StrikeType fallback) {
        if (!tag.contains("Type", Tag.TAG_STRING)) {
            return fallback;
        }
        try {
            return StrikeType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
