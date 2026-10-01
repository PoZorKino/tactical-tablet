package moe.dexx.tacticaltablet.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** An 8x8 stone slab four blocks thick (relative y 1..4) with the strike target on top of its centre. */
final class TestArena {
    static final int BOTTOM = 1;
    static final int TOP = 4;
    static final int CENTER = 4;

    private TestArena() {
    }

    static void buildSlab(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = BOTTOM; y <= TOP; y++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
    }

    static BlockPos target(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(CENTER, TOP, CENTER));
    }

    static void assertSlabIntact(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = BOTTOM; y <= TOP; y++) {
                    helper.assertBlockPresent(Blocks.STONE, x, y, z);
                }
            }
        }
    }

    static boolean isMelted(Block block) {
        return block == Blocks.BLACKSTONE || block == Blocks.BASALT || block == Blocks.MAGMA_BLOCK;
    }
}
