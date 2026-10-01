package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.additive;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.backdrop;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.beam;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.begin;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.box;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.disc;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.ease;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.end;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.fbm;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.glow;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.opaque;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.tube;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Random;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The meteor strike: a flight through an asteroid belt, a giant electromagnet that drags one asteroid out of it
 * and hurls it at the home planet, and the burning fall into the atmosphere.
 */
final class MeteorShots {
    private static final Vector3f SUN = new Vector3f(0.5F, 0.35F, 0.8F).normalize();
    private static final float ARM_X = 75.0F;
    private static final float ARM_RADIUS = 16.0F;
    private static final float POLE_Z = -72.0F;
    private static final float ASTEROID_RADIUS = 46.0F;
    private static final float HOLD_Z = -118.0F;
    private static final int FIELD_ROCKS = 110;
    private static final int LINES = 10;

    private static SphereMesh asteroid;
    private static SphereMesh rock;
    private static float[] field;
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

    static void render(Segment segment, float p, float time) {
        build();
        switch (segment) {
            case JUPITER -> belt(p, time);
            case SATURN -> reveal(p, time);
            case CANNON -> capture(p, time);
            case FIRE -> hurl(p, time);
            case DESCENT -> fall(p, time);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- shots

    /** Flying through the belt with Jupiter hanging far off to one side. */
    private static void belt(float p, float time) {
        float travel = ease(p) * 1300.0F + p * 200.0F;
        Vector3f eye = new Vector3f(0.0F, 0.0F, -travel);
        Vector3f center = new Vector3f(Mth.sin(time * 0.4F) * 8.0F, 3.0F, -travel - 100.0F);
        Matrix4f view = new Matrix4f().lookAt(eye, center, new Vector3f(Mth.sin(time * 0.3F) * 0.15F, 1.0F, 0.0F).normalize());
        backdrop(eye, center, SUN);
        opaque();
        BufferBuilder buffer = begin();
        SpaceScene.jupiter.draw(buffer, view, new Vector3f(-1100.0F, 260.0F, -2800.0F), 520.0F, new Matrix3f().rotateZ(0.05F), SUN, 0.035F);
        Vector3f at = new Vector3f();
        for (int i = 0; i < FIELD_ROCKS; i++) {
            int o = i * 5;
            at.set(field[o], field[o + 1], field[o + 2]);
            Matrix3f spin = new Matrix3f().rotateXYZ(field[o + 4] + time * 0.3F, field[o + 4] * 1.7F, time * 0.2F);
            rock.draw(buffer, view, at, field[o + 3], spin, SUN, 0.04F);
        }
        end(buffer);
        additive();
        buffer = begin();
        SpaceScene.jupiter.drawRim(buffer, view, eye, new Vector3f(-1100.0F, 260.0F, -2800.0F), 530.0F, new Matrix3f(), 0.95F, 0.72F, 0.5F, 0.4F);
        end(buffer);
    }

    /** The magnet comes into view with the chosen asteroid hanging in front of its poles. */
    private static void reveal(float p, float time) {
        float charge = magnetCharge(Segment.SATURN, p);
        Vector3f eye = new Vector3f(Mth.lerp(ease(p), 230.0F, 120.0F), Mth.lerp(p, 60.0F, 24.0F), Mth.lerp(ease(p), 170.0F, 80.0F));
        Vector3f center = new Vector3f(0.0F, 0.0F, -150.0F);
        scene(eye, center, charge, time, new Vector3f(0.0F, 0.0F, -330.0F + 200.0F * p), 0.0F, 0.0F, 0.0F);
    }

    /** The field takes hold: lines of force reach out, the asteroid shudders and is dragged between the poles. */
    private static void capture(float p, float time) {
        float charge = magnetCharge(Segment.CANNON, p);
        float pull = ease(p);
        float z = Mth.lerp(pull, -130.0F, HOLD_Z);
        Vector3f eye = new Vector3f(Mth.lerp(p, 100.0F, 30.0F), Mth.lerp(p, 36.0F, 20.0F), Mth.lerp(p, 130.0F, 110.0F));
        Vector3f center = new Vector3f(0.0F, 0.0F, -90.0F);
        float shudder = p * 1.6F;
        Vector3f asteroidAt = new Vector3f(Mth.sin(time * 37.0F) * shudder, Mth.cos(time * 29.0F) * shudder, z);
        scene(eye, center, charge, time, asteroidAt, 1.0F, p, 0.0F);
    }

    /** The poles flip and the asteroid is thrown down the line of fire. */
    private static void hurl(float p, float time) {
        float away = p * p * 2600.0F;
        // Beside the poles, looking down the line of fire, so the yoke does not block the view.
        Vector3f eye = new Vector3f(118.0F + Mth.sin(time * 61.0F) * 1.4F * (1.0F - p), 34.0F + Mth.cos(time * 47.0F) * 1.4F * (1.0F - p), -10.0F);
        Vector3f center = new Vector3f(0.0F, 0.0F, -420.0F);
        scene(eye, center, 1.0F, time, new Vector3f(0.0F, 0.0F, HOLD_Z - away), 0.0F, 0.0F, p);
    }

    /** Riding beside the asteroid as it burns down onto the planet. */
    private static void fall(float p, float time) {
        float travel = ease(Mth.clamp(p, 0.0F, 1.0F)) * 400.0F;
        Vector3f rockAt = new Vector3f(0.0F, 0.0F, -travel - 120.0F);
        Vector3f eye = new Vector3f(70.0F, 30.0F, -travel + 20.0F);
        Matrix4f view = new Matrix4f().lookAt(eye, rockAt, new Vector3f(0.0F, 1.0F, 0.0F));
        backdrop(eye, rockAt, SUN);

        float heat = Mth.clamp((p - 0.35F) / 0.65F, 0.0F, 1.0F);
        Matrix3f earthSpin = new Matrix3f().rotateX(0.4F).rotateY(time * 0.01F);
        opaque();
        BufferBuilder buffer = begin();
        SpaceScene.earth.draw(buffer, view, SpaceScene.EARTH_CENTER, SpaceScene.EARTH_RADIUS, earthSpin, SUN, 0.03F);
        asteroid.draw(buffer, view, rockAt, ASTEROID_RADIUS * 0.85F, new Matrix3f().rotateY(time * 0.8F).rotateX(time * 0.5F), SUN, 0.1F + 0.6F * heat);
        end(buffer);

        additive();
        buffer = begin();
        Vector3f right = view.normalizedPositiveX(new Vector3f());
        Vector3f up = view.normalizedPositiveY(new Vector3f());
        SpaceScene.earth.drawRim(buffer, view, eye, SpaceScene.EARTH_CENTER, SpaceScene.EARTH_RADIUS * 1.03F, earthSpin, 0.35F, 0.6F, 1.0F, 1.6F);
        // A tail of fire streams back up the path; it grows with the heat.
        int steps = 26;
        for (int i = 0; i < steps; i++) {
            float fade = 1.0F - (float) i / steps;
            float wobble = Mth.sin(time * 9.0F + i * 0.8F) * 4.0F * heat;
            glow(buffer, view, right, up, rockAt.x + wobble, rockAt.y + wobble * 0.5F, rockAt.z + 18.0F * i,
                    (14.0F + 40.0F * fade) * (0.2F + heat), 1.0F, 0.4F + 0.3F * fade, 0.1F, 0.55F * fade * (0.15F + heat));
        }
        glow(buffer, view, right, up, rockAt.x, rockAt.y, rockAt.z - ASTEROID_RADIUS * 0.6F, 30.0F + 120.0F * heat * heat, 1.0F, 0.6F, 0.2F, 0.8F * heat);
        glow(buffer, view, right, up, rockAt.x, rockAt.y, rockAt.z - ASTEROID_RADIUS * 0.6F, 12.0F + 50.0F * heat, 1.0F, 0.95F, 0.8F, heat);
        end(buffer);
    }

    // ---------------------------------------------------------------- shared set

    /**
     * The magnet with the asteroid in front of it. fieldAmount draws the lines of force, release lights the shock
     * rings of the throw.
     */
    private static void scene(Vector3f eye, Vector3f center, float charge, float time, Vector3f asteroidAt,
                              float fieldAmount, float pull, float release) {
        Matrix4f view = new Matrix4f().lookAt(eye, center, new Vector3f(0.0F, 1.0F, 0.0F));
        backdrop(eye, center, SUN);
        opaque();
        BufferBuilder buffer = begin();
        magnet(buffer, view, charge, time);
        Matrix3f spin = new Matrix3f().rotateY(time * 0.25F).rotateX(0.3F + time * 0.1F);
        asteroid.draw(buffer, view, asteroidAt, ASTEROID_RADIUS, spin, SUN, 0.05F);
        end(buffer);

        additive();
        buffer = begin();
        Vector3f right = view.normalizedPositiveX(new Vector3f());
        Vector3f up = view.normalizedPositiveY(new Vector3f());
        float flicker = 0.85F + 0.15F * Mth.sin(time * 40.0F);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * ARM_X;
            glow(buffer, view, right, up, x, 0.0F, POLE_Z - 4.0F, (22.0F + 44.0F * charge) * flicker, 0.4F, 0.75F, 1.0F, 0.55F * charge);
            glow(buffer, view, right, up, x, 0.0F, POLE_Z - 4.0F, (8.0F + 14.0F * charge) * flicker, 0.9F, 0.97F, 1.0F, charge);
        }
        if (fieldAmount > 0.0F) {
            fieldLines(buffer, view, eye, asteroidAt, time, fieldAmount * (0.3F + 0.7F * pull));
        }
        if (release > 0.0F) {
            // Shock rings run down the line of fire from the poles; a tail of fire starts behind the asteroid.
            for (int ring = 0; ring < 3; ring++) {
                float t = Mth.clamp(release * 1.6F - ring * 0.25F, 0.0F, 1.0F);
                float radius = 30.0F + 260.0F * t;
                float z = POLE_Z - 40.0F - 340.0F * t;
                for (int i = 0; i < 36; i++) {
                    float a = Mth.TWO_PI * i / 36;
                    glow(buffer, view, right, up, Mth.cos(a) * radius, Mth.sin(a) * radius * 0.6F, z, 9.0F, 0.6F, 0.85F, 1.0F, 0.45F * (1.0F - t));
                }
            }
            for (int i = 0; i < 20; i++) {
                float fade = 1.0F - i / 20.0F;
                glow(buffer, view, right, up, asteroidAt.x, asteroidAt.y, asteroidAt.z + 16.0F * i, 10.0F + 30.0F * fade, 1.0F, 0.55F, 0.2F, 0.4F * fade * release);
            }
        }
        end(buffer);
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

    private static void build() {
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
        field = new float[FIELD_ROCKS * 5];
        for (int i = 0; i < FIELD_ROCKS; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float radius = 20.0F + random.nextFloat() * 240.0F;
            field[i * 5] = Mth.cos(angle) * radius;
            field[i * 5 + 1] = Mth.sin(angle) * radius * 0.6F;
            field[i * 5 + 2] = -random.nextFloat() * 1700.0F - 40.0F;
            field[i * 5 + 3] = 2.0F + random.nextFloat() * random.nextFloat() * 22.0F;
            field[i * 5 + 4] = random.nextFloat() * Mth.TWO_PI;
        }
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
