package moe.dexx.tacticaltablet.strike;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.DestructionJob;
import moe.dexx.tacticaltablet.destruction.TickBudget;
import moe.dexx.tacticaltablet.destruction.TickTimeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Owns every strike on the server: phases, limits, cancellation and the destruction jobs. Server thread only. */
public final class StrikeManager {
    public static final String REJECT_ALREADY_ACTIVE = "tactical_tablet.reject.already_active";
    public static final String REJECT_SERVER_BUSY = "tactical_tablet.reject.server_busy";
    public static final String REJECT_TARGET_BORDER = "tactical_tablet.reject.target_border";

    private static final int PROGRESS_INTERVAL_TICKS = 20;

    public interface Listener {
        Listener NONE = new Listener() {
            @Override
            public void onState(Strike strike) {
            }

            @Override
            public void onAbort(Strike strike, String reasonKey) {
            }

            @Override
            public void onProgress(Strike strike, int done, int total) {
            }
        };

        /** A strike was created or entered a new phase. */
        void onState(Strike strike);

        /** A strike was cancelled before the shot. */
        void onAbort(Strike strike, String reasonKey);

        void onProgress(Strike strike, int done, int total);
    }

    public interface OwnerProbe {
        /** @return an AbortReason key when the owner can no longer run this strike, otherwise null */
        String abortReason(MinecraftServer server, Strike strike);
    }

    private final MinecraftServer server;
    private final ServerConfig config;
    private final OwnerProbe probe;
    private final Listener listener;
    private final TickTimeTracker tickTimes;
    private final TickBudget budget;
    private final Map<UUID, Strike> strikes = new LinkedHashMap<>();
    private StrikeSummary lastSummary;

    public StrikeManager(MinecraftServer server, ServerConfig config, OwnerProbe probe, Listener listener, TickTimeTracker tickTimes) {
        this.server = server;
        this.config = config;
        this.probe = probe;
        this.listener = listener;
        this.tickTimes = tickTimes;
        this.budget = new TickBudget(config.tickBudgetMs);
    }

    public String launch(UUID owner, ServerLevel level, StrikeParams params) {
        return launch(owner, level, params, StrikePhase.COUNTDOWN);
    }

    /** @return the translation key of the reason the launch was refused, or null when the strike started */
    public String launch(UUID owner, ServerLevel level, StrikeParams params, StrikePhase initialPhase) {
        String reject = params.validate(config.maxRadius, level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (reject != null) {
            return reject;
        }
        BlockPos target = new BlockPos(params.targetX(), params.targetY(), params.targetZ());
        if (!level.getWorldBorder().isWithinBounds(target)) {
            return REJECT_TARGET_BORDER;
        }
        if (strikes.containsKey(owner)) {
            return REJECT_ALREADY_ACTIVE;
        }
        if (strikes.size() >= config.maxConcurrentStrikes) {
            return REJECT_SERVER_BUSY;
        }
        Strike strike = new Strike(UUID.randomUUID(), owner, level.dimension(), target, params);
        strikes.put(owner, strike);
        enter(strike, level, initialPhase, level.getGameTime());
        return null;
    }

    /** Cancels a strike that has not fired yet. */
    public boolean cancel(UUID owner, String reasonKey) {
        Strike strike = strikes.get(owner);
        if (strike == null || !strike.phase.cancellable()) {
            return false;
        }
        strikes.remove(owner);
        listener.onAbort(strike, reasonKey);
        return true;
    }

    /** Lets the owner halt a destruction job that is still running. What is already destroyed stays destroyed. */
    public boolean emergencyStop(UUID owner) {
        Strike strike = strikes.get(owner);
        if (strike == null || strike.job == null || strike.job.isFinished()) {
            return false;
        }
        strikes.remove(owner);
        complete(strike);
        return true;
    }

    /** Operator stop: works in any phase. */
    public boolean forceStop(UUID owner) {
        Strike strike = strikes.remove(owner);
        if (strike == null) {
            return false;
        }
        if (strike.phase.cancellable()) {
            listener.onAbort(strike, AbortReason.STOPPED_BY_OPERATOR);
        } else {
            complete(strike);
        }
        return true;
    }

    public int forceStopAll() {
        List<UUID> owners = new ArrayList<>(strikes.keySet());
        int stopped = 0;
        for (UUID owner : owners) {
            if (forceStop(owner)) {
                stopped++;
            }
        }
        return stopped;
    }

    /** Server is stopping: strikes that have not fired are aborted, running jobs are dropped. */
    public void shutdown() {
        for (Strike strike : new ArrayList<>(strikes.values())) {
            if (strike.phase.cancellable()) {
                listener.onAbort(strike, AbortReason.SERVER_STOPPING);
            } else {
                complete(strike);
            }
        }
        strikes.clear();
    }

    public Strike strikeOf(UUID owner) {
        return strikes.get(owner);
    }

    public Collection<Strike> active() {
        return Collections.unmodifiableCollection(strikes.values());
    }

    public StrikeSummary lastSummary() {
        return lastSummary;
    }

    public void tick() {
        if (strikes.isEmpty()) {
            return;
        }
        long budgetNanos = budget.update(tickTimes.averageMs());
        int runningJobs = 0;
        for (Strike strike : strikes.values()) {
            if (strike.job != null && !strike.job.isFinished()) {
                runningJobs++;
            }
        }
        long share = budgetNanos / Math.max(1, runningJobs);

        Iterator<Strike> iterator = strikes.values().iterator();
        while (iterator.hasNext()) {
            Strike strike = iterator.next();
            ServerLevel level = server.getLevel(strike.dimension());
            if (strike.phase.cancellable()) {
                String reason = level == null ? AbortReason.TARGET_UNAVAILABLE : abortReason(level, strike);
                if (reason != null) {
                    iterator.remove();
                    listener.onAbort(strike, reason);
                    continue;
                }
            }
            if (level == null) {
                iterator.remove();
                complete(strike);
                continue;
            }
            advance(strike, level);
            if (strike.job != null && !strike.job.isFinished()) {
                strike.job.tick(System.nanoTime() + share);
                long now = level.getGameTime();
                if (now - strike.lastProgressTick >= PROGRESS_INTERVAL_TICKS) {
                    strike.lastProgressTick = now;
                    listener.onProgress(strike, strike.job.chunksDone(), strike.job.chunksTotal());
                }
            }
            if (strike.phase == StrikePhase.DESTROYING && strike.job.isFinished()) {
                enter(strike, level, StrikePhase.DONE, level.getGameTime());
            }
            if (strike.phase == StrikePhase.DONE) {
                iterator.remove();
            }
        }
    }

    private String abortReason(ServerLevel level, Strike strike) {
        if (!level.getWorldBorder().isWithinBounds(strike.target())) {
            return AbortReason.TARGET_UNAVAILABLE;
        }
        return probe.abortReason(server, strike);
    }

    private void advance(Strike strike, ServerLevel level) {
        long now = level.getGameTime();
        int duration = StrikeTimeline.durationTicks(strike.phase, strike.params());
        while (duration >= 0 && now - strike.phaseStartTick >= duration) {
            boolean jobRunning = strike.job != null && !strike.job.isFinished();
            StrikePhase next = StrikeTimeline.next(strike.phase, jobRunning);
            enter(strike, level, next, strike.phaseStartTick + duration);
            duration = StrikeTimeline.durationTicks(strike.phase, strike.params());
        }
    }

    private void enter(Strike strike, ServerLevel level, StrikePhase phase, long startTick) {
        strike.phase = phase;
        strike.phaseStartTick = startTick;
        if (phase == StrikePhase.IMPACT && strike.params().modifiesWorld() && strike.job == null) {
            strike.job = new DestructionJob(level, strike.id(), strike.target(), strike.params(), config);
        }
        if (phase == StrikePhase.DONE) {
            closeJob(strike);
        }
        listener.onState(strike);
    }

    /** Ends a strike that is no longer in the map: stops its job and announces DONE. */
    private void complete(Strike strike) {
        strike.phase = StrikePhase.DONE;
        closeJob(strike);
        listener.onState(strike);
    }

    private void closeJob(Strike strike) {
        if (strike.job == null) {
            return;
        }
        strike.job.stop();
        long wallMillis = strike.job.elapsedNanos() / 1_000_000L;
        lastSummary = new StrikeSummary(strike.owner(), strike.job.chunksDone(), strike.job.chunksTotal(),
                strike.job.blocksRemoved(), wallMillis);
        TacticalTablet.LOGGER.info("Strike by {} finished: {}/{} chunks, {} blocks removed, {} ms",
                strike.owner(), lastSummary.chunksDone(), lastSummary.chunksTotal(), lastSummary.blocksRemoved(), wallMillis);
    }
}
