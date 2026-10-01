package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.SPARK_COUNT;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.beam;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.box;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.disc;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.glow;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.shade;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.sparks;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.tube;

import com.mojang.blaze3d.vertex.BufferBuilder;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The orbital cannon beside Saturn: charging, firing, and the beam that follows. The cannon points along local -Z. */
final class LaserShots {
    static final float MUZZLE_Z = -72.0F;

    private LaserShots() {
    }

    /** Camera in the cannon's own frame: circling it while it charges, then beside the barrel for the shot. */
    static void camera(Segment segment, float p, float time, Vector3f eye, Vector3f center) {
        if (segment == Segment.CANNON) {
            float eased = Mth.clamp(p * p * (3.0F - 2.0F * p), 0.0F, 1.0F);
            float angle = Mth.lerp(eased, 0.61F, 1.5F);
            float range = Mth.lerp(eased, 150.0F, 92.0F);
            eye.set(Mth.sin(angle) * range, Mth.lerp(eased, 42.0F, 14.0F), Mth.cos(angle) * range - 10.0F);
            center.set(0.0F, 0.0F, -14.0F);
        } else {
            float shake = 1.6F * (1.0F - p);
            eye.set(46.0F + shake * Mth.sin(time * 61.0F), 16.0F + shake * Mth.cos(time * 47.0F), -18.0F);
            center.set(0.0F, -4.0F, -130.0F);
        }
    }

    static void solid(BufferBuilder buffer, Stage stage, float charge, float time) {
        cannon(buffer, stage.device, Stage.SUN_LOCAL, charge, time);
    }

    static void glows(BufferBuilder buffer, Stage stage, Segment segment, float p, float charge, float time) {
        chargeGlow(buffer, stage.device, stage.rightLocal, stage.upLocal, charge, time);
        if (segment == Segment.FIRE) {
            float pulse = 0.78F + 0.22F * Mth.sin(time * 40.0F);
            Vector3f from = new Vector3f(0.0F, 0.0F, MUZZLE_Z);
            Vector3f to = new Vector3f(0.0F, 0.0F, -6000.0F);
            beam(buffer, stage.device, stage.eyeLocal, from, to, 16.0F, 0.3F, 0.6F, 1.0F, 0.28F * pulse);
            beam(buffer, stage.device, stage.eyeLocal, from, to, 7.0F, 0.45F, 0.85F, 1.0F, 0.65F * pulse);
            beam(buffer, stage.device, stage.eyeLocal, from, to, 2.4F, 1.0F, 1.0F, 1.0F, 1.0F);
            glow(buffer, stage.device, stage.rightLocal, stage.upLocal, 0.0F, 0.0F, MUZZLE_Z - 2.0F, 14.0F + 46.0F * (1.0F - p), 0.6F, 0.9F, 1.0F, 0.9F);
        }
    }

    /** The cannon lies along the Z axis with its muzzle towards negative Z, where the planet is. */
    static void cannon(BufferBuilder buffer, Matrix4f view, Vector3f sun, float charge, float time) {
        // Reactor block at the rear.
        tube(buffer, view, sun, 34.0F, 11.0F, 58.0F, 11.0F, 24, 0.30F, 0.32F, 0.37F, 0.0F, 1.0F);
        tube(buffer, view, sun, 58.0F, 11.0F, 70.0F, 4.0F, 24, 0.24F, 0.26F, 0.30F, 0.0F, 1.0F);
        disc(buffer, view, sun, 70.0F, 0.0F, 4.0F, 24, 1.0F, 0.2F, 0.22F, 0.25F, 0.0F);
        disc(buffer, view, sun, 34.0F, 5.0F, 11.0F, 24, -1.0F, 0.26F, 0.28F, 0.32F, 0.0F);
        // Barrel and muzzle.
        tube(buffer, view, sun, -62.0F, 5.0F, 34.0F, 5.0F, 24, 0.56F, 0.58F, 0.63F, 0.0F, 1.0F);
        tube(buffer, view, sun, MUZZLE_Z, 8.0F, -62.0F, 5.5F, 24, 0.42F, 0.44F, 0.5F, 0.0F, 1.0F);
        disc(buffer, view, sun, MUZZLE_Z, 3.5F, 8.0F, 24, -1.0F, 0.2F, 0.21F, 0.24F, 0.0F);
        disc(buffer, view, sun, MUZZLE_Z + 0.4F, 0.0F, 3.5F, 24, -1.0F,
                0.1F + 0.5F * charge, 0.15F + 0.75F * charge, 0.2F + 0.8F * charge, 1.0F);
        // Clamp rings, each with a coil that lights up as the charge travels down the barrel.
        for (int i = 0; i < 4; i++) {
            float z = 16.0F - i * 22.0F;
            tube(buffer, view, sun, z - 1.6F, 7.6F, z + 1.6F, 7.6F, 24, 0.36F, 0.38F, 0.43F, 0.0F, 1.0F);
            disc(buffer, view, sun, z - 1.6F, 5.0F, 7.6F, 24, -1.0F, 0.3F, 0.32F, 0.36F, 0.0F);
            disc(buffer, view, sun, z + 1.6F, 5.0F, 7.6F, 24, 1.0F, 0.3F, 0.32F, 0.36F, 0.0F);
            float wave = 0.5F + 0.5F * Mth.sin(time * (3.0F + 9.0F * charge) - i * 1.3F);
            float lit = charge * (0.35F + 0.65F * wave);
            tube(buffer, view, sun, z - 0.5F, 7.8F, z + 0.5F, 7.8F, 24, 0.08F + 0.4F * lit, 0.12F + 0.8F * lit, 0.16F + 0.84F * lit, 1.0F, 1.0F);
        }
        // Four rails along the barrel.
        box(buffer, view, sun, -0.9F, 6.2F, -58.0F, 0.9F, 7.8F, 30.0F, 0.34F, 0.36F, 0.4F);
        box(buffer, view, sun, -0.9F, -7.8F, -58.0F, 0.9F, -6.2F, 30.0F, 0.34F, 0.36F, 0.4F);
        box(buffer, view, sun, 6.2F, -0.9F, -58.0F, 7.8F, 0.9F, 30.0F, 0.34F, 0.36F, 0.4F);
        box(buffer, view, sun, -7.8F, -0.9F, -58.0F, -6.2F, 0.9F, 30.0F, 0.34F, 0.36F, 0.4F);
        // Solar panels on booms either side of the reactor.
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side > 0 ? 11.0F : -15.0F;
            box(buffer, view, sun, x0, -1.0F, 44.0F, x0 + 4.0F, 1.0F, 48.0F, 0.4F, 0.4F, 0.44F);
            float p0 = side > 0 ? 15.0F : -74.0F;
            box(buffer, view, sun, p0, -0.3F, 36.0F, p0 + 59.0F, 0.3F, 56.0F, 0.06F, 0.12F, 0.38F);
        }
    }

    /** Light gathering at the muzzle while the cannon charges. */
    static void chargeGlow(BufferBuilder buffer, Matrix4f view, Vector3f right, Vector3f up, float charge, float time) {
        if (charge <= 0.0F) {
            return;
        }
        float flicker = 0.85F + 0.15F * Mth.sin(time * 53.0F);
        float z = MUZZLE_Z - 2.0F;
        glow(buffer, view, right, up, 0.0F, 0.0F, z, (4.0F + 26.0F * charge * charge) * flicker, 0.35F, 0.75F, 1.0F, 0.75F * charge);
        glow(buffer, view, right, up, 0.0F, 0.0F, z, (1.5F + 8.0F * charge) * flicker, 1.0F, 1.0F, 1.0F, charge);
        for (int i = 0; i < SPARK_COUNT; i++) {
            int o = i * 3;
            float phase = (time * 0.9F + i * 0.618F) % 1.0F;
            float distance = (1.0F - phase) * (16.0F + 30.0F * charge);
            glow(buffer, view, right, up, sparks[o] * distance, sparks[o + 1] * distance, z + sparks[o + 2] * distance,
                    0.5F + 0.9F * charge, 0.5F, 0.85F, 1.0F, phase * charge);
        }
    }
}
