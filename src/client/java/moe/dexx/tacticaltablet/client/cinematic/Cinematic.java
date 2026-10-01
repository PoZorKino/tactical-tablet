package moe.dexx.tacticaltablet.client.cinematic;

import java.util.UUID;
import moe.dexx.tacticaltablet.client.TacticalTabletClient;
import moe.dexx.tacticaltablet.client.gui.TabletScreen;
import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.CinematicTimeline;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the launch cinematic for the player's own strike. Everything shown is derived from the server's strike
 * state, so the shot leaves the cannon and hits the ground exactly when the server says so.
 */
public final class Cinematic {
    /** Where the camera is and where it looks, in world space. */
    public record Pose(Vec3 position, float yaw, float pitch) {
    }

    private record View(Vec3 position, Vec3 direction) {
    }

    private static UUID activeId;
    /** The strike whose cinematic already ended or was skipped; it is not started again. */
    private static UUID finishedId;
    private static boolean savedHideGui;
    private static double smoothSeconds;
    private static long smoothNanos;

    private Cinematic() {
    }

    public static boolean isActive() {
        return activeId != null;
    }

    /** @return the strike the running cinematic belongs to, or null */
    public static StrikeState state() {
        if (activeId == null) {
            return null;
        }
        StrikeState own = ClientStrikes.own();
        return own != null && own.id().equals(activeId) ? own : null;
    }

    public static void tick(Minecraft minecraft) {
        StrikeState own = ClientStrikes.own();
        LocalPlayer player = minecraft.player;
        if (activeId != null) {
            boolean gone = own == null || !own.id().equals(activeId) || player == null;
            // Taking damage ends the cinematic so the player can react.
            if (gone || !player.isAlive() || player.hurtTime > 0 || segmentNow(own) == Segment.OVER) {
                stop(minecraft);
            }
            return;
        }
        if (own == null || player == null || own.id().equals(finishedId) || !TacticalTabletClient.config().cinematicEnabled) {
            return;
        }
        if (minecraft.screen != null && !(minecraft.screen instanceof TabletScreen)) {
            return;
        }
        if (segmentNow(own) == Segment.OVER) {
            return;
        }
        activeId = own.id();
        smoothNanos = 0L;
        savedHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        minecraft.setScreen(new CinematicScreen());
    }

    /** Ends the cinematic early; the strike itself goes on. */
    public static void skip() {
        stop(Minecraft.getInstance());
    }

    /** The cinematic screen was closed by something else, so the camera goes back without touching the screen. */
    static void screenRemoved() {
        release(Minecraft.getInstance());
    }

    private static void stop(Minecraft minecraft) {
        if (release(minecraft) && minecraft.screen instanceof CinematicScreen) {
            minecraft.setScreen(null);
        }
    }

    private static boolean release(Minecraft minecraft) {
        if (activeId == null) {
            return false;
        }
        finishedId = activeId;
        activeId = null;
        minecraft.options.hideGui = savedHideGui;
        return true;
    }

    private static Segment segmentNow(StrikeState state) {
        return CinematicTimeline.segment(rawSeconds(state, 0.0F), state.salvos());
    }

    private static double rawSeconds(StrikeState state, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return -1.0;
        }
        double ticks = minecraft.level.getGameTime() - state.phaseStartTick() + partialTick;
        if (state.durationTicks() >= 0) {
            ticks = Math.min(ticks, state.durationTicks());
        }
        return CinematicTimeline.seconds(state.phase(), Math.max(0.0, ticks));
    }

    /**
     * Seconds on the cinematic timeline for the frame being drawn, or -1 when no cinematic runs. The value follows
     * the wall clock and is pulled gently towards the server's time, so a late time packet does not jerk the camera.
     */
    public static double seconds(float partialTick) {
        StrikeState state = state();
        if (state == null) {
            return -1.0;
        }
        double target = rawSeconds(state, partialTick);
        if (target < 0.0) {
            return target;
        }
        long now = System.nanoTime();
        if (smoothNanos == 0L || Math.abs(smoothSeconds - target) > 0.4) {
            smoothSeconds = target;
        } else {
            smoothSeconds += (now - smoothNanos) / 1.0e9;
            smoothSeconds += (target - smoothSeconds) * 0.08;
        }
        smoothNanos = now;
        return smoothSeconds;
    }

    /** @return the camera pose for the shots filmed inside the world, or null when the camera is left alone */
    public static Pose worldPose(float partialTick) {
        StrikeState state = state();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (state == null || player == null) {
            return null;
        }
        double seconds = seconds(partialTick);
        int salvos = state.salvos();
        Segment segment = CinematicTimeline.segment(seconds, salvos);
        if (segment == Segment.OVER || segment.inSpace()) {
            return null;
        }
        double progress = CinematicTimeline.progress(seconds, salvos);

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 target = Vec3.atCenterOf(state.target());
        Vec3 flat = new Vec3(target.x - eye.x, 0.0, target.z - eye.z);
        double distance = flat.length();
        if (distance < 1.0) {
            flat = new Vec3(look.x, 0.0, look.z);
        }
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
        Vec3 pulledBack = eye.add(flat.scale(-14.0)).add(0.0, 9.0, 0.0);

        View view = switch (segment) {
            case PULL_AWAY -> {
                double eased = ease(progress);
                Vec3 position = eye.lerp(pulledBack, eased);
                yield new View(position, blend(look, target.subtract(position), eased));
            }
            case ASCENT -> {
                Vec3 position = pulledBack.add(0.0, 600.0 * progress * progress, 0.0);
                Vec3 skyward = new Vec3(flat.x * 0.15, 1.0, flat.z * 0.15);
                yield new View(position, blend(target.subtract(pulledBack), skyward, ease(progress)));
            }
            case IMPACT -> impactView(minecraft, state, eye, flat, distance, target, progress);
            case RETURN -> {
                double eased = ease(progress);
                View from = impactView(minecraft, state, eye, flat, distance, target, 1.0);
                yield new View(from.position().lerp(eye, eased), blend(from.direction(), look, eased));
            }
            default -> null;
        };
        if (view == null) {
            return null;
        }
        Vec3 direction = view.direction();
        float yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * Mth.RAD_TO_DEG);
        return new Pose(view.position(), yaw, pitch);
    }

    /** Side view of the target while the beams hit. */
    private static View impactView(Minecraft minecraft, StrikeState state, Vec3 eye, Vec3 flat, double distance,
                                   Vec3 target, double progress) {
        int loaded = minecraft.options.getEffectiveRenderDistance() * 16;
        if (distance > loaded - 24) {
            // The client has no terrain around the target, so watch the beams from above the player instead.
            Vec3 position = eye.add(flat.scale(-10.0)).add(0.0, 24.0, 0.0);
            Vec3 focus = target.add(0.0, Math.max(40.0, state.radius() * 0.5), 0.0);
            return new View(position, focus.subtract(position).normalize());
        }
        double range = Math.min(state.radius() * 1.5 + 30.0, 420.0);
        // On the player's side of the target, circling slowly.
        Vec3 side = flat.scale(-1.0).yRot((float) ((progress - 0.5) * 0.5));
        Vec3 position = target.add(side.scale(range)).add(0.0, range * 0.42 + 8.0, 0.0);
        Vec3 focus = target.add(0.0, state.radius() * 0.1, 0.0);
        return new View(position, focus.subtract(position).normalize());
    }

    /**
     * Colour laid over the whole picture at the given moment, as ARGB: black between shots, white around the shot
     * and the first hit.
     */
    public static int fade(double seconds, int salvos) {
        double black = 0.0;
        double white = 0.0;
        double progress = CinematicTimeline.progress(seconds, salvos);
        switch (CinematicTimeline.segment(seconds, salvos)) {
            case ASCENT -> black = (progress - 0.6) / 0.4;
            case JUPITER -> black = Math.max(1.0 - (seconds - CinematicTimeline.ASCENT_END) / 0.5,
                    1.0 - (CinematicTimeline.JUPITER_END - seconds) / 0.3);
            case SATURN -> black = Math.max(1.0 - (seconds - CinematicTimeline.JUPITER_END) / 0.3,
                    1.0 - (CinematicTimeline.SATURN_END - seconds) / 0.3);
            case CANNON -> black = 1.0 - (seconds - CinematicTimeline.SATURN_END) / 0.3;
            // The cut from the cannon beyond Saturn to the home planet is hidden in a flash of the beam.
            case FIRE -> white = Math.max(0.9 * (1.0 - (seconds - CinematicTimeline.CANNON_END) / 0.45),
                    1.0 - (CinematicTimeline.FIRE_END - seconds) / 0.25);
            case DESCENT -> white = Math.max(1.0 - (seconds - CinematicTimeline.FIRE_END) / 0.35,
                    1.0 - (CinematicTimeline.DESCENT_END - seconds) / 0.6);
            case IMPACT -> white = 1.0 - (seconds - CinematicTimeline.DESCENT_END) / 0.7;
            default -> {
            }
        }
        if (white > 0.0) {
            return (int) (Mth.clamp(white, 0.0, 1.0) * 255.0) << 24 | 0xFFFFFF;
        }
        return (int) (Mth.clamp(black, 0.0, 1.0) * 255.0) << 24;
    }

    private static double ease(double value) {
        double clamped = Mth.clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    /** Direction that turns from one vector to the other as amount goes from 0 to 1. */
    private static Vec3 blend(Vec3 from, Vec3 to, double amount) {
        Vec3 a = from.normalize();
        Vec3 b = to.normalize();
        Vec3 mixed = a.scale(1.0 - amount).add(b.scale(amount));
        return mixed.lengthSqr() < 1.0e-6 ? b : mixed.normalize();
    }
}
