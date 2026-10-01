package moe.dexx.tacticaltablet.destruction;

import moe.dexx.tacticaltablet.TacticalTablet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocks a strike never removes. Extend it with a data pack. */
    public static final TagKey<Block> STRIKE_PROTECTED = TagKey.create(Registries.BLOCK, TacticalTablet.id("strike_protected"));

    private ModTags() {
    }
}
