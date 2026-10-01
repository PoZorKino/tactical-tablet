package moe.dexx.tacticaltablet.gametest;

import java.util.UUID;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.DestructionJob;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class DestructionGameTests implements FabricGameTest {
    private static final long FIVE_SECONDS = 5_000_000_000L;

    private static StrikeParams params(BlockPos target, boolean destroyLiquids, boolean damageEntities) {
        return new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 3, 5, 1, 3,
                StrikeType.ORBITAL_LASER, true, destroyLiquids, damageEntities);
    }

    private static DestructionJob job(GameTestHelper helper, boolean destroyLiquids, boolean damageEntities) {
        BlockPos target = TestArena.target(helper);
        return new DestructionJob(helper.getLevel(), UUID.randomUUID(), target,
                params(target, destroyLiquids, damageEntities), new ServerConfig());
    }

    /** Ticks the job every game tick until it reports completion, then runs the assertions. */
    private static void whenFinished(GameTestHelper helper, DestructionJob job, Runnable assertions) {
        helper.succeedWhen(() -> {
            if (!job.tick(System.nanoTime() + FIVE_SECONDS)) {
                throw new GameTestAssertException("the destruction job is still running");
            }
            assertions.run();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void craterMatchesTheProfile(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            // Centre and its four neighbours: two blocks removed, a melted floor block underneath.
            for (int[] offset : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = 4 + offset[0];
                int z = 4 + offset[1];
                helper.assertBlockPresent(Blocks.AIR, x, 4, z);
                helper.assertBlock(new BlockPos(x, 3, z), block -> block == Blocks.AIR || block == Blocks.FIRE,
                        "expected air or fire above the melted floor");
                helper.assertBlock(new BlockPos(x, 2, z), TestArena::isMelted, "expected a melted floor block");
                helper.assertBlockPresent(Blocks.STONE, x, 1, z);
            }
            // Diagonal neighbour: depth 2, outside the melted zone.
            helper.assertBlockPresent(Blocks.AIR, 5, 4, 5);
            helper.assertBlockPresent(Blocks.AIR, 5, 3, 5);
            helper.assertBlockPresent(Blocks.STONE, 5, 2, 5);
            // Distance 2 and (2,1): depth 1.
            helper.assertBlockPresent(Blocks.AIR, 6, 4, 4);
            helper.assertBlockPresent(Blocks.STONE, 6, 3, 4);
            helper.assertBlockPresent(Blocks.AIR, 6, 4, 5);
            helper.assertBlockPresent(Blocks.STONE, 6, 3, 5);
            // (2,2), distance 3 and the far corner: untouched.
            helper.assertBlockPresent(Blocks.STONE, 6, 4, 6);
            helper.assertBlockPresent(Blocks.STONE, 7, 4, 4);
            helper.assertBlockPresent(Blocks.STONE, 1, 4, 4);
            helper.assertBlockPresent(Blocks.STONE, 0, 4, 0);
            helper.assertTrue(job.chunksDone() == job.chunksTotal(), "not every chunk was processed");
            helper.assertTrue(job.blocksRemoved() > 0, "no blocks were counted as removed");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void protectedBlocksSurvive(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        helper.setBlock(4, 4, 4, Blocks.BEDROCK);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.BEDROCK, 4, 4, 4);
            helper.assertBlockPresent(Blocks.AIR, 4, 3, 4);
            helper.assertBlockPresent(Blocks.AIR, 5, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liquidsAreKeptWhenDisabled(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockState waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        helper.setBlock(4, 5, 4, waterlogged);
        DestructionJob job = job(helper, false, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.OAK_SLAB, 4, 5, 4);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liquidsAreRemovedWhenEnabled(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockState waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        helper.setBlock(4, 5, 4, waterlogged);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.AIR, 4, 5, 4);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void entitiesInsideTheZoneVanishWithoutDrops(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 5, 4));
        ItemEntity inside = helper.spawnItem(Items.DIAMOND, 4.5F, 5.0F, 5.5F);
        ItemEntity outside = helper.spawnItem(Items.EMERALD, 0.5F, 5.0F, 0.5F);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertTrue(pig.isRemoved(), "the pig inside the zone must vanish");
            helper.assertTrue(inside.isRemoved(), "the item inside the zone must vanish");
            helper.assertFalse(outside.isRemoved(), "the item outside the radius must stay");
            helper.assertItemEntityNotPresent(Items.PORKCHOP, new BlockPos(4, 4, 4), 8.0);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void entitiesAreSparedWhenDisabled(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 5, 4));
        DestructionJob job = job(helper, true, false);
        whenFinished(helper, job, () -> helper.assertFalse(pig.isRemoved(), "entities must be spared when damage is off"));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void containersDoNotSpillTheirContents(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        helper.setBlock(4, 4, 4, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 4, 4)))
                .setItem(0, new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT, 64));
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertItemEntityNotPresent(Items.GOLD_INGOT, new BlockPos(4, 4, 4), 8.0);
            helper.assertItemEntityNotPresent(Items.CHEST, new BlockPos(4, 4, 4), 8.0);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void skyLightReachesTheCraterFloor(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        DestructionJob job = job(helper, true, true);
        helper.assertTrue(job.tick(System.nanoTime() + FIVE_SECONDS), "a four-chunk job must finish in one tick");
        BlockPos floorAir = helper.absolutePos(new BlockPos(4, 3, 4));
        helper.runAfterDelay(40, () -> {
            int light = helper.getLevel().getBrightness(LightLayer.SKY, floorAir);
            helper.assertTrue(light == 15, "sky light above the crater floor is " + light + ", expected 15");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void overlappingJobsBothFinish(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        DestructionJob first = job(helper, true, true);
        DestructionJob second = job(helper, true, true);
        helper.succeedWhen(() -> {
            boolean firstDone = first.tick(System.nanoTime() + FIVE_SECONDS);
            boolean secondDone = second.tick(System.nanoTime() + FIVE_SECONDS);
            if (!firstDone || !secondDone) {
                throw new GameTestAssertException("a job is still running");
            }
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertTrue(first.pendingChunks() == 0 && second.pendingChunks() == 0, "a job still holds chunks");
            helper.assertTrue(first.chunksDone() == first.chunksTotal(), "the first job skipped chunks");
            helper.assertTrue(second.chunksDone() == second.chunksTotal(), "the second job skipped chunks");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stopReleasesEverything(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        // Far from every test structure and never generated, so the first tick only requests chunks.
        BlockPos target = TestArena.target(helper).offset(100_000, 0, 0);
        StrikeParams params = new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 200, 5, 1, 3,
                StrikeType.ORBITAL_LASER, true, true, true);
        DestructionJob job = new DestructionJob(helper.getLevel(), UUID.randomUUID(), target, params, config);
        helper.assertFalse(job.tick(System.nanoTime()), "a 200-block job cannot finish in one tick");
        helper.assertTrue(job.pendingChunks() == config.maxForcedChunks, "the window must be filled up to the limit");
        job.stop();
        helper.assertTrue(job.pendingChunks() == 0, "stop() must release every chunk");
        helper.assertTrue(job.isFinished(), "a stopped job must report completion");
        helper.assertTrue(job.tick(System.nanoTime() + FIVE_SECONDS), "ticking a stopped job must be a no-op");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ungeneratedChunksAreSkippedWhenGenerationIsOff(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        config.generateMissingChunks = false;
        BlockPos target = TestArena.target(helper).offset(200_000, 0, 0);
        StrikeParams params = params(target, true, true);
        DestructionJob job = new DestructionJob(helper.getLevel(), UUID.randomUUID(), target, params, config);
        whenFinished(helper, job, () -> {
            helper.assertTrue(job.blocksRemoved() == 0, "nothing may be removed in chunks that were never generated");
            helper.assertTrue(job.chunksDone() == job.chunksTotal(), "skipped chunks must still be counted as done");
        });
    }
}
