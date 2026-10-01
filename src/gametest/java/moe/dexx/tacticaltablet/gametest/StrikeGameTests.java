package moe.dexx.tacticaltablet.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.TickTimeTracker;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.strike.AbortReason;
import moe.dexx.tacticaltablet.strike.PlayerOwnerProbe;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class StrikeGameTests implements FabricGameTest {
    private static final class Recorder implements StrikeManager.Listener {
        final List<StrikePhase> phases = new ArrayList<>();
        final List<String> aborts = new ArrayList<>();

        @Override
        public void onState(Strike strike) {
            phases.add(strike.phase());
        }

        @Override
        public void onAbort(Strike strike, String reasonKey) {
            aborts.add(reasonKey);
        }

        @Override
        public void onProgress(Strike strike, int done, int total) {
        }
    }

    private static final StrikeManager.OwnerProbe ALWAYS_AVAILABLE = (server, strike) -> null;

    private static StrikeManager manager(GameTestHelper helper, ServerConfig config, StrikeManager.OwnerProbe probe, Recorder recorder) {
        return new StrikeManager(helper.getLevel().getServer(), config, probe, recorder, new TickTimeTracker());
    }

    private static StrikeParams quick(GameTestHelper helper, StrikeType type) {
        BlockPos target = TestArena.target(helper);
        return new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 3, 5, 1, 3, type, true, true, true);
    }

    private static void assertLaunched(GameTestHelper helper, String reject) {
        helper.assertTrue(reject == null, "the launch was rejected: " + reject);
    }

    private static void whenStrikeIsOver(GameTestHelper helper, StrikeManager manager, UUID owner, Runnable assertions) {
        helper.succeedWhen(() -> {
            if (manager.strikeOf(owner) != null) {
                throw new GameTestAssertException("the strike is still active");
            }
            assertions.run();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void visualOnlyStrikeLeavesTheWorldUntouched(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.VISUAL_ONLY)));
        helper.onEachTick(manager::tick);
        whenStrikeIsOver(helper, manager, owner, () -> {
            List<StrikePhase> expected = List.of(StrikePhase.COUNTDOWN, StrikePhase.CHARGE, StrikePhase.TRAVEL,
                    StrikePhase.IMPACT, StrikePhase.DONE);
            helper.assertTrue(recorder.phases.equals(expected), "phases were " + recorder.phases);
            helper.assertTrue(recorder.aborts.isEmpty(), "unexpected aborts " + recorder.aborts);
            TestArena.assertSlabIntact(helper);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void orbitalStrikeDigsTheCraterOnlyAtImpact(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(500, () -> TestArena.assertSlabIntact(helper));
        whenStrikeIsOver(helper, manager, owner, () -> {
            helper.assertTrue(recorder.phases.contains(StrikePhase.IMPACT), "phases were " + recorder.phases);
            helper.assertTrue(recorder.phases.get(recorder.phases.size() - 1) == StrikePhase.DONE, "phases were " + recorder.phases);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertTrue(manager.lastSummary() != null && manager.lastSummary().blocksRemoved() > 0, "no summary was recorded");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void cancelBeforeTheShotLeavesTheWorldUntouched(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(manager.strikeOf(owner).phase() == StrikePhase.CHARGE, "expected the charge phase");
            helper.assertTrue(manager.cancel(owner, AbortReason.CANCELLED), "cancel before the shot must succeed");
            helper.assertTrue(recorder.aborts.equals(List.of(AbortReason.CANCELLED)), "aborts were " + recorder.aborts);
            helper.assertTrue(manager.strikeOf(owner) == null, "a cancelled strike must be gone");
        });
        helper.runAtTickTime(700, () -> {
            TestArena.assertSlabIntact(helper);
            helper.assertFalse(recorder.phases.contains(StrikePhase.TRAVEL), "a cancelled strike must never fire");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void cancelAfterTheShotIsRefused(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(480, () -> {
            helper.assertTrue(manager.strikeOf(owner).phase() == StrikePhase.TRAVEL, "expected the travel phase");
            helper.assertFalse(manager.cancel(owner, AbortReason.CANCELLED), "cancel after the shot must be refused");
            helper.assertFalse(manager.emergencyStop(owner), "there is no destruction to stop during travel");
            helper.assertTrue(manager.strikeOf(owner) != null, "the strike must still be active");
        });
        whenStrikeIsOver(helper, manager, owner, () -> {
            helper.assertTrue(recorder.aborts.isEmpty(), "unexpected aborts " + recorder.aborts);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void secondLaunchAndServerLimitAreRejected(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        config.maxConcurrentStrikes = 1;
        StrikeManager manager = manager(helper, config, ALWAYS_AVAILABLE, new Recorder());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StrikeParams params = quick(helper, StrikeType.VISUAL_ONLY);
        assertLaunched(helper, manager.launch(first, helper.getLevel(), params));
        helper.assertTrue(StrikeManager.REJECT_ALREADY_ACTIVE.equals(manager.launch(first, helper.getLevel(), params)),
                "a second launch by the same owner must be rejected");
        helper.assertTrue(StrikeManager.REJECT_SERVER_BUSY.equals(manager.launch(second, helper.getLevel(), params)),
                "a launch above the server limit must be rejected");
        helper.assertTrue(manager.active().size() == 1, "exactly one strike must be active");
        helper.assertTrue(manager.cancel(first, AbortReason.CANCELLED), "cancel must succeed");
        assertLaunched(helper, manager.launch(second, helper.getLevel(), params));
        helper.assertTrue(manager.cancel(second, AbortReason.CANCELLED), "cancel must succeed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void radiusAboveTheLimitIsRejected(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        StrikeManager manager = manager(helper, config, ALWAYS_AVAILABLE, new Recorder());
        UUID owner = UUID.randomUUID();
        StrikeParams params = quick(helper, StrikeType.VISUAL_ONLY);
        helper.assertTrue(StrikeParams.REJECT_RADIUS.equals(manager.launch(owner, helper.getLevel(), params.withRadius(1001))),
                "radius 1001 must be rejected");
        config.maxRadius = 100;
        helper.assertTrue(StrikeParams.REJECT_RADIUS.equals(manager.launch(owner, helper.getLevel(), params.withRadius(101))),
                "a radius above the server limit must be rejected");
        helper.assertTrue(manager.active().isEmpty(), "rejected launches must not create strikes");
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), params.withRadius(100)));
        helper.assertTrue(manager.cancel(owner, AbortReason.CANCELLED), "cancel must succeed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void targetOutsideWorldBorderRejected(GameTestHelper helper) {
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, new Recorder());
        StrikeParams params = quick(helper, StrikeType.VISUAL_ONLY).withTarget(40_000_000, 64, 0);
        helper.assertTrue(StrikeManager.REJECT_TARGET_BORDER.equals(manager.launch(UUID.randomUUID(), helper.getLevel(), params)),
                "a target outside the world border must be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void ownerLeavingBeforeTheShotAbortsTheStrike(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        AtomicReference<String> problem = new AtomicReference<>();
        StrikeManager manager = manager(helper, new ServerConfig(), (server, strike) -> problem.get(), recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(100, () -> problem.set(AbortReason.PLAYER_LEFT));
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(recorder.aborts.equals(List.of(AbortReason.PLAYER_LEFT)), "aborts were " + recorder.aborts);
            helper.assertTrue(manager.strikeOf(owner) == null, "an aborted strike must be gone");
        });
        helper.runAtTickTime(700, () -> {
            TestArena.assertSlabIntact(helper);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void ownerLeavingAfterTheShotDoesNotStopTheStrike(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        AtomicReference<String> problem = new AtomicReference<>();
        StrikeManager manager = manager(helper, new ServerConfig(), (server, strike) -> problem.get(), recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(480, () -> problem.set(AbortReason.PLAYER_LEFT));
        whenStrikeIsOver(helper, manager, owner, () -> {
            helper.assertTrue(recorder.aborts.isEmpty(), "a fired strike must not abort: " + recorder.aborts);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void operatorStopDuringTravelPreventsDestruction(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(480, () -> {
            helper.assertTrue(manager.forceStop(owner), "an operator stop must find the strike");
            helper.assertTrue(manager.strikeOf(owner) == null, "a stopped strike must be gone");
            helper.assertFalse(manager.forceStop(owner), "there is nothing left to stop");
        });
        helper.runAtTickTime(700, () -> {
            TestArena.assertSlabIntact(helper);
            helper.assertTrue(recorder.phases.get(recorder.phases.size() - 1) == StrikePhase.DONE, "phases were " + recorder.phases);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void immediateStrikeStartsAtImpact(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER), StrikePhase.IMPACT));
        manager.tick();
        helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        helper.assertTrue(recorder.phases.get(0) == StrikePhase.IMPACT, "phases were " + recorder.phases);
        helper.assertTrue(manager.forceStopAll() == 1, "one strike must be stopped");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void shutdownAbortsPendingStrikes(GameTestHelper helper) {
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.assertFalse(manager.emergencyStop(owner), "there is no destruction to stop during the countdown");
        manager.shutdown();
        helper.assertTrue(recorder.aborts.equals(List.of(AbortReason.SERVER_STOPPING)), "aborts were " + recorder.aborts);
        helper.assertTrue(manager.active().isEmpty(), "shutdown must clear every strike");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void probeReportsOwnerProblems(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        PlayerOwnerProbe probe = new PlayerOwnerProbe(config);
        StrikeManager manager = manager(helper, config, probe, new Recorder());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.TACTICAL_TABLET));
            assertLaunched(helper, manager.launch(player.getUUID(), helper.getLevel(), quick(helper, StrikeType.VISUAL_ONLY)));
            Strike strike = manager.strikeOf(player.getUUID());
            helper.assertTrue(probe.abortReason(helper.getLevel().getServer(), strike) == null, "a healthy owner must pass");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(AbortReason.GAMEMODE.equals(probe.abortReason(helper.getLevel().getServer(), strike)),
                    "a spectator owner must abort the strike");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        Strike orphan = manager.strikeOf(player.getUUID());
        helper.assertTrue(AbortReason.PLAYER_LEFT.equals(probe.abortReason(helper.getLevel().getServer(), orphan)),
                "a missing owner must abort the strike");
        helper.succeed();
    }
}
