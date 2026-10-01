package moe.dexx.tacticaltablet.bench;

import java.util.Locale;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.command.TabletCommands;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeSummary;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Unattended measurement for a dedicated server: with -Dtactical_tablet.benchmark=RADIUS,POWER the mod fires an
 * immediate strike at the world spawn after a short warm-up, logs one "[bench]" line when it is over and stops the server.
 */
public final class BenchmarkHook {
    public static final String PROPERTY = "tactical_tablet.benchmark";
    /** The first ticks after startup are slow on their own; let them pass before measuring. */
    private static final int WARMUP_TICKS = 200;
    private static final int SETTLE_TICKS = 100;
    private static final int[][] LIGHT_SAMPLES = {{0, 0}, {16, 0}, {0, 16}, {-16, -16}, {32, 0}};

    private static int radius;
    private static int power;
    private static BlockPos target;
    private static int warmupTicks;
    private static boolean started;
    private static boolean finished;
    private static int settleTicks = -1;
    private static double peakAverageTickMs;

    private BenchmarkHook() {
    }

    public static void register() {
        String spec = System.getProperty(PROPERTY);
        if (spec == null || spec.isBlank()) {
            return;
        }
        String[] parts = spec.split(",");
        radius = Integer.parseInt(parts[0].trim());
        power = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : StrikeParams.DEFAULT.power();
        ServerTickEvents.END_SERVER_TICK.register(BenchmarkHook::tick);
    }

    private static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ()) - 1;
        target = new BlockPos(spawn.getX(), Math.max(level.getMinBuildHeight(), surface), spawn.getZ());
        StrikeParams params = StrikeParams.DEFAULT.withTarget(target.getX(), target.getY(), target.getZ())
                .withRadius(radius).withPower(power);
        String reject = TacticalTablet.manager().launch(TabletCommands.CONSOLE_OWNER, level, params, StrikePhase.IMPACT);
        if (reject != null) {
            TacticalTablet.LOGGER.error("[bench] launch rejected: {}", reject);
            finished = true;
            server.halt(false);
            return;
        }
        TacticalTablet.tickTimes().resetMax();
        started = true;
        TacticalTablet.LOGGER.info("[bench] started radius={} power={} target={}", radius, power, target.toShortString());
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        if (!started) {
            if (++warmupTicks == WARMUP_TICKS) {
                start(server);
            }
            return;
        }
        StrikeManager manager = TacticalTablet.manager();
        if (settleTicks < 0) {
            peakAverageTickMs = Math.max(peakAverageTickMs, TacticalTablet.tickTimes().averageMs());
            if (manager.strikeOf(TabletCommands.CONSOLE_OWNER) == null) {
                settleTicks = 0;
            }
            return;
        }
        if (++settleTicks < SETTLE_TICKS) {
            return;
        }
        finished = true;
        report(server, manager.lastSummary());
        server.halt(false);
    }

    private static void report(MinecraftServer server, StrikeSummary summary) {
        ServerLevel level = server.overworld();
        int lit = 0;
        int sampled = 0;
        for (int[] offset : LIGHT_SAMPLES) {
            if (Math.abs(offset[0]) > radius / 2 || Math.abs(offset[1]) > radius / 2) {
                continue;
            }
            int x = target.getX() + offset[0];
            int z = target.getZ() + offset[1];
            BlockPos above = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            sampled++;
            if (level.getBrightness(LightLayer.SKY, above) == 15) {
                lit++;
            }
        }
        TacticalTablet.LOGGER.info(String.format(Locale.ROOT,
                "[bench] radius=%d power=%d chunks=%d/%d blocks=%d wallMs=%d peakAvgTickMs=%.1f maxTickMs=%.1f skyLight=%d/%d",
                radius, power, summary.chunksDone(), summary.chunksTotal(), summary.blocksRemoved(), summary.wallMillis(),
                peakAverageTickMs, TacticalTablet.tickTimes().maxMs(), lit, sampled));
    }
}
