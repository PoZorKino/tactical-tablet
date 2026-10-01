package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.beam;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.box;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.disc;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.ease;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.fbm;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.glow;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.tube;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Random;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The meteor strike: a giant electromagnet beside Saturn drags an asteroid in front of its poles and hurls it down
 * the line of fire at the home planet. The magnet lies in the weapon's frame, poles towards -Z.
 */
final class MeteorShots {
    private static final Vector3f SUN = Stage.SUN_LOCAL;
    private static final float ARM_X = 75.0F;
    private static final float ARM_RADIUS = 16.0F;
    private static final float POLE_Z = -72.0F;
    static final float ASTEROID_RADIUS = 46.0F;
    private static final float HOLD_Z = -118.0F;
    private static final int LINES = 10;

    static SphereMesh asteroid;
    static SphereMesh rock;
    private static float[] lineAims;

    private MeteorShots() {
    }

    /** How hard the magnet is driven, 0 to 1, over the whole sequence. */
    static float magnetCharge(Segment segment, float p) {
        return switch (segment) {
            case SATURN -> 0.15F + 0.45F * p;
            case CANNON -> 0.6F + 0.4F * p;
            case FIRE -> 1.0F;
            default -> 0.0F;
        };
    }

    /** Field strength shown on the readout, in tesla. */
    static float fieldTesla(Segment segment, float p) {
        return 80.0F * magnetCharge(segment, p);
    }

    /** Where the asteroid is in the weapon's frame. */
    static Vector3f asteroidAt(Segment segment, float p, float time) {
        switch (segment) {
            case SATURN:
                return new Vector3f(0.0F, 0.0F, -330.0F + 200.0F * p);
            case CANNON:
                float shudder = p * 1.6F;
                return new Vector3f(Mth.sin(time * 37.0F) * shudder, Mth.cos(time * 29.0F) * shudder,
                        Mth.lerp(ease(p), -130.0F, HOLD_Z));
            default:
                return new Vector3f(0.0F, 0.0F, HOLD_Z - p * p * 2600.0F);
        }
    }

    /** Camera in the weapon's frame; for the throw it stands beside the poles, clear of the yoke. */
    static void camera(Segment segment, float p, float time, Vector3f eye, Vector3f center) {
        if (segment == Segment.CANNON) {
            eye.set(Mth.lerp(p, 100.0F, 30.0F), Mth.lerp(p, 36.0F, 20.0F), Mth.lerp(p, 130.0F, 110.0F));
            center.set(0.0F, 0.0F, -90.0F);
        } else {
            float shake = 1.4F * (1.0F - p);
            eye.set(118.0F + Mth.sin(time * 61.0F) * shake, 34.0F + Mth.cos(time * 47.0F) * shake, -10.0F);
            center.set(0.0F, 0.0F, -420.0F);
        }
    }

    static void solid(BufferBuilder buffer, Stage stage, Segment segment, float p, float time) {
        build();
        magnet(buffer, stage.device, magnetCharge(segment, p), time);
        Matrix3f spin = new Matrix3f().rotateY(time * 0.25F).rotateX(0.3F + time * 0.1F);
        asteroid.draw(buffer, stage.device, asteroidAt(segment, p, time), ASTEROID_RADIUS, spin, SUN, 0.05F);
    }

    static void glows(BufferBuilder buffer, Stage stage, Segment segment, float p, float time) {
        Matrix4f view = stage.device;
        Vector3f right = stage.rightLocal;
        Vector3f up = stage.upLocal;
        float charge = magnetCharge(segment, p);
        Vector3f asteroidAt = asteroidAt(segment, p, time);
        float flicker = 0.85F + 0.15F * Mth.sin(time * 40.0F);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * ARM_X;
            glow(buffer, view, right, up, x, 0.0F, POLE_Z - 4.0F, (22.0F + 44.0F * charge) * flicker, 0.4F, 0.75F, 1.0F, 0.55F * charge);
            glow(buffer, view, right, up, x, 0.0F, POLE_Z - 4.0F, (8.0F + 14.0F * charge) * flicker, 0.9F, 0.97F, 1.0F, charge);
        }
        if (segment == Segment.CANNON) {
            fieldLines(buffer, view, stage.eyeLocal, asteroidAt, time, 0.3F + 0.7F * ease(p));
        }
        if (segment == Segment.FIRE) {
            // Shock rings run down the line of fire from the poles; a tail of fire starts behind the asteroid.
            for (int ring = 0; ring < 3; ring++) {
                float t = Mth.clamp(p * 1.6F - ring * 0.25F, 0.0F, 1.0F);
                float radius = 30.0F + 260.0F * t;
                float z = POLE_Z - 40.0F - 340.0F * t;
                for (int i = 0; i < 36; i++) {
                    float a = Mth.TWO_PI * i / 36;
                    glow(buffer, view, right, up, Mth.cos(a) * radius, Mth.sin(a) * radius * 0.6F, z, 9.0F, 0.6F, 0.85F, 1.0F, 0.45F * (1.0F - t));
                }
            }
            for (int i = 0; i < 20; i++) {
                float fade = 1.0F - i / 20.0F;
                glow(buffer, view, right, up, asteroidAt.x, asteroidAt.y, asteroidAt.z + 16.0F * i, 10.0F + 30.0F * fade, 1.0F, 0.55F, 0.2F, 0.4F * fade * p);
            }
        }
    }

    /** The horseshoe: a yoke at the back and two wound arms reaching forward to the poles. */
    private static void magnet(BufferBuilder buffer, Matrix4f view, float charge, float time) {
        box(buffer, view, SUN, -ARM_X - ARM_RADIUS, -ARM_RADIUS, 82.0F, ARM_X + ARM_RADIUS, ARM_RADIUS, 124.0F, 0.3F, 0.32F, 0.36F);
        box(buffer, view, SUN, -22.0F, ARM_RADIUS, 90.0F, 22.0F, ARM_RADIUS + 10.0F, 116.0F, 0.22F, 0.24F, 0.28F);
        for (int side = -1; side <= 1; side += 2) {
            Matrix4f arm = new Matrix4f(view).translate(side * ARM_X, 0.0F, 0.0F);
            tube(buffer, arm, SUN, POLE_Z + 12.0F, ARM_RADIUS, 84.0F, ARM_RADIUS, 24, 0.34F, 0.36F, 0.4F, 0.0F, 1.0F);
            // The pole cap: red on one side, blue on the other, as on any magnet.
            float red = side < 0 ? 0.8F : 0.18F;
            float blue = side < 0 ? 0.14F : 0.75F;
            float green = side < 0 ? 0.14F : 0.35F;
            tube(buffer, arm, SUN, POLE_Z, ARM_RADIUS + 3.0F, POLE_Z + 12.0F, ARM_RADIUS + 3.0F, 24, red, green, blue, 0.15F + 0.4F * charge, 1.0F);
            disc(buffer, arm, SUN, POLE_Z, 0.0F, ARM_RADIUS + 3.0F, 24, -1.0F, red, green, blue, 0.2F + 0.5F * charge);
            for (int i = 0; i < 7; i++) {
                float z = 66.0F - i * 12.0F;
                float wave = 0.5F + 0.5F * Mth.sin(time * (3.0F + 8.0F * charge) - i * 0.9F);
                float lit = charge * (0.3F + 0.7F * wave);
                tube(buffer, arm, SUN, z - 4.5F, ARM_RADIUS + 2.4F, z + 4.5F, ARM_RADIUS + 2.4F, 24,
                        0.62F + 0.2F * lit, 0.34F + 0.5F * lit, 0.16F + 0.8F * lit, lit * 0.7F, 1.0F);
            }
        }
    }

    /** Curved lines of force from both poles to the asteroid. */
    private static void fieldLines(BufferBuilder buffer, Matrix4f view, Vector3f eye, Vector3f asteroidAt, float time, float strength) {
        Vector3f previous = new Vector3f();
        Vector3f current = new Vector3f();
        for (int line = 0; line < LINES; line++) {
            int side = line % 2 == 0 ? -1 : 1;
            float aimX = lineAims[line * 3];
            float aimY = lineAims[line * 3 + 1];
            float aimZ = lineAims[line * 3 + 2];
            Vector3f from = new Vector3f(side * ARM_X, aimY * 6.0F, POLE_Z - 6.0F);
            Vector3f to = new Vector3f(asteroidAt).add(aimX * ASTEROID_RADIUS, aimY * ASTEROID_RADIUS, aimZ * ASTEROID_RADIUS);
            int steps = 16;
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / steps;
                float bulge = Mth.sin(t * Mth.PI) * (30.0F + 24.0F * aimY);
                current.set(Mth.lerp(t, from.x, to.x) - side * bulge * 0.5F, Mth.lerp(t, from.y, to.y) + bulge * (aimY < 0 ? -1.0F : 1.0F), Mth.lerp(t, from.z, to.z));
                if (i > 0) {
                    float flow = 0.55F + 0.45F * Mth.sin(time * 9.0F - t * 7.0F + line);
                    beam(buffer, view, eye, previous, current, 1.5F, 0.55F, 0.85F, 1.0F, strength * flow);
                }
                previous.set(current);
            }
        }
    }

    // ---------------------------------------------------------------- content

    static void build() {
        if (asteroid != null) {
            return;
        }
        SphereMesh.Surface stone = (x, y, z, out) -> {
            float tone = 0.26F + 0.34F * fbm(x * 5.0F + 3.0F, y * 5.0F, z * 5.0F, 4);
            float rust = fbm(x * 2.0F, y * 2.0F + 8.0F, z * 2.0F, 2);
            out[0] = tone * (0.95F + 0.3F * rust);
            out[1] = tone * 0.9F;
            out[2] = tone * (0.85F - 0.15F * rust);
        };
        asteroid = new SphereMesh(40, 64, stone, (x, y, z) -> 0.62F + 0.7F * fbm(x * 1.7F + 11.0F, y * 1.7F, z * 1.7F + 2.0F, 4));
        rock = new SphereMesh(8, 12, stone, (x, y, z) -> 0.7F + 0.5F * fbm(x * 2.2F + 9.0F, y * 2.2F + 1.0F, z * 2.2F, 3));
        Random random = new Random(4242L);
        lineAims = new float[LINES * 3];
        for (int i = 0; i < LINES; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            lineAims[i * 3] = Mth.cos(angle) * 0.8F;
            lineAims[i * 3 + 1] = Mth.sin(angle) * 0.8F;
            // Always the side that faces the magnet.
            lineAims[i * 3 + 2] = 0.3F + random.nextFloat() * 0.5F;
        }
    }
}
