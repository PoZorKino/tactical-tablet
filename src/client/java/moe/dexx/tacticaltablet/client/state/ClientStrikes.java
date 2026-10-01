package moe.dexx.tacticaltablet.client.state;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** What this client knows about strikes in progress, fed by the server packets. Client thread only. */
public final class ClientStrikes {
    private static final long NOTICE_MILLIS = 5000L;

    private static final Map<UUID, StrikeState> STRIKES = new LinkedHashMap<>();
    private static UUID progressId;
    private static int progressDone;
    private static int progressTotal;
    private static Component notice;
    private static boolean noticeIsError;
    private static long noticeUntil;
    private static long lastRejectMillis;

    private ClientStrikes() {
    }

    public static void onState(StrikeState state) {
        if (state.phase() == StrikePhase.DONE) {
            StrikeState previous = STRIKES.remove(state.id());
            if (previous != null && isOwn(previous)) {
                notify(Component.translatable("tactical_tablet.hud.complete"), false);
            }
            return;
        }
        STRIKES.put(state.id(), state);
    }

    public static void onAbort(UUID id, String reasonKey) {
        StrikeState removed = STRIKES.remove(id);
        if (removed != null && isOwn(removed)) {
            notify(Component.translatable(reasonKey), true);
        }
    }

    public static void onRejected(String reasonKey) {
        lastRejectMillis = System.currentTimeMillis();
        notify(Component.translatable(reasonKey), true);
    }

    public static void onProgress(UUID id, int done, int total) {
        progressId = id;
        progressDone = done;
        progressTotal = total;
    }

    public static void clear() {
        STRIKES.clear();
        progressId = null;
        notice = null;
    }

    public static Collection<StrikeState> all() {
        return Collections.unmodifiableCollection(STRIKES.values());
    }

    /** @return the strike launched by the local player, or null */
    public static StrikeState own() {
        for (StrikeState state : STRIKES.values()) {
            if (isOwn(state)) {
                return state;
            }
        }
        return null;
    }

    /** @return destruction progress of the given strike in percent, 0 when unknown */
    public static int progressPercent(StrikeState state) {
        if (!state.id().equals(progressId) || progressTotal <= 0) {
            return 0;
        }
        return (int) (100L * progressDone / progressTotal);
    }

    public static int progressDone(StrikeState state) {
        return state.id().equals(progressId) ? progressDone : 0;
    }

    public static int progressTotal(StrikeState state) {
        return state.id().equals(progressId) ? progressTotal : 0;
    }

    /** Ticks left in the strike's current phase, never negative; 0 for open-ended phases. */
    public static long ticksLeft(StrikeState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || state.durationTicks() < 0) {
            return 0;
        }
        return Math.max(0, state.phaseStartTick() + state.durationTicks() - minecraft.level.getGameTime());
    }

    public static void notify(Component message, boolean error) {
        notice = message;
        noticeIsError = error;
        noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS;
    }

    /** @return the message to show right now, or null */
    public static Component notice() {
        return notice != null && System.currentTimeMillis() < noticeUntil ? notice : null;
    }

    public static boolean noticeIsError() {
        return noticeIsError;
    }

    /** Wall-clock time of the last server rejection; lets the screen flash its button once per rejection. */
    public static long lastRejectMillis() {
        return lastRejectMillis;
    }

    private static boolean isOwn(StrikeState state) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && state.owner().equals(minecraft.player.getUUID());
    }
}
