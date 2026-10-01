package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.additive;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.backdrop;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.beam;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.begin;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.ease;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.end;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.glow;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.mix;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.opaque;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Random;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The flight through space as one continuous camera move: away from Earth, past Jupiter, on to Saturn, around the
 * weapon while it charges, then along the projectile back down to the home planet.
 */
final class Voyage {
    private static final int BELT_ROCKS = 150;
    private static float[] belt;

    private Voyage() {
    }

    static void render(StrikeType type, Segment segment, float p, float seconds) {
        switch (segment) {
            case JUPITER, SATURN -> travel(type, segment, p, seconds);
            case CANNON, FIRE -> atWeapon(type, segment, p, seconds);
            case DESCENT -> fall(type, p, seconds);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- shots

    /** Earth to Jupiter to Saturn along the camera route, with the weapon coming into view at the end. */
    private static void travel(StrikeType type, Segment segment, float p, float seconds) {
        Vector3f eye = new Vector3f();
        Vector3f center = new Vector3f();
        Stage.route(type, seconds, eye, center);
        Stage stage = Stage.looking(type, eye, center);
        backdrop(eye, center, Stage.SUN);
        stage.planets(seconds);
        drawBelt(stage, type, seconds);
        drawWeapon(type, stage, segment, p, seconds);
    }

    /** Around the weapon in its own frame; the first moments blend in from where the route ended. */
    private static void atWeapon(StrikeType type, Segment segment, float p, float seconds) {
        Vector3f eyeLocal = new Vector3f();
        Vector3f centerLocal = new Vector3f();
        switch (type) {
            case KINETIC -> KineticShots.camera(segment, p, seconds, eyeLocal, centerLocal);
            case METEOR -> MeteorShots.camera(segment, p, seconds, eyeLocal, centerLocal);
            default -> LaserShots.camera(segment, p, seconds, eyeLocal, centerLocal);
        }
        if (segment == Segment.CANNON) {
            float settle = ease(Mth.clamp(p / 0.2F, 0.0F, 1.0F));
            Vector3f arrival = new Vector3f();
            Stage.route(type, 14.0F, arrival, new Vector3f());
            eyeLocal = mix(Stage.frameOf(type).toLocal(arrival), eyeLocal, settle);
            centerLocal = mix(new Vector3f(), centerLocal, settle);
        }
        Vector3f eye = Stage.frameOf(type).toWorld(eyeLocal);
        Vector3f center = Stage.frameOf(type).toWorld(centerLocal);
        Stage stage = Stage.looking(type, eye, center);
        backdrop(eye, center, Stage.SUN);
        stage.planets(seconds);
        drawBelt(stage, type, seconds);
        drawWeapon(type, stage, segment, p, seconds);
    }

    private static void drawWeapon(StrikeType type, Stage stage, Segment segment, float p, float seconds) {
        // On the way in the weapons are dormant: the ring around Jupiter sits dark, the others are still far off.
        if (segment == Segment.JUPITER) {
            if (type != StrikeType.KINETIC) {
                return;
            }
            segment = Segment.SATURN;
            p = 0.0F;
        } else if (segment == Segment.SATURN && type != StrikeType.KINETIC && p < 0.35F) {
            return;
        }
        float laserCharge = segment == Segment.SATURN ? 0.1F : segment == Segment.CANNON ? p : 1.0F;
        opaque();
        BufferBuilder buffer = begin();
        switch (type) {
            case KINETIC -> KineticShots.solid(buffer, stage, segment, p, seconds);
            case METEOR -> MeteorShots.solid(buffer, stage, segment, p, seconds);
            default -> LaserShots.solid(buffer, stage, laserCharge, seconds);
        }
        end(buffer);
        additive();
        buffer = begin();
        switch (type) {
            case KINETIC -> KineticShots.glows(buffer, stage, segment, p, seconds);
            case METEOR -> MeteorShots.glows(buffer, stage, segment, p, seconds);
            default -> LaserShots.glows(buffer, stage, segment, p, laserCharge, seconds);
        }
        end(buffer);
    }

    /** Riding behind the projectile all the way down; it always flies point first at the planet. */
    private static void fall(StrikeType type, float p, float seconds) {
        Stage.Frame frame = Stage.frameOf(type);
        float k = frame.scale;
        Vector3f start = frame.toWorld(startLocal(type));
        // Aimed at the planet from wherever the weapon released it, so the projectile always hits.
        Vector3f dir = new Vector3f(Stage.EARTH).sub(start).normalize();
        float distance = distanceToEarth(start, dir);
        float travelled = distance * (float) Math.pow(p, 1.15);
        Vector3f head = new Vector3f(start).fma(travelled, dir);

        Vector3f side = new Vector3f(0.0F, 1.0F, 0.0F).cross(dir).normalize();
        Vector3f up = new Vector3f(dir).cross(side).normalize();
        float back = type == StrikeType.KINETIC ? 85.0F * k : type == StrikeType.METEOR ? 125.0F : 110.0F;
        float sideways = type == StrikeType.KINETIC ? 75.0F * k : type == StrikeType.METEOR ? 55.0F : 22.0F;
        float lift = type == StrikeType.KINETIC ? 24.0F * k : type == StrikeType.METEOR ? 22.0F : 9.0F;
        Vector3f eye = new Vector3f(head).fma(-back, dir).fma(sideways, side).fma(lift, up);
        // Always looking at the planet the projectile is heading for, so it is clear where it goes.
        Vector3f center = new Vector3f(start).fma(distance, dir);
        Stage stage = Stage.looking(type, eye, center);
        backdrop(eye, center, Stage.SUN);
        stage.planets(seconds);

        float heat = Mth.clamp((p - 0.86F) / 0.14F, 0.0F, 1.0F);
        opaque();
        BufferBuilder buffer = begin();
        if (type != StrikeType.ORBITAL_LASER && type != StrikeType.VISUAL_ONLY) {
            MeteorShots.build();
            float debris = 1.0F - ease(Mth.clamp((p - 0.3F) / 0.3F, 0.0F, 1.0F));
            if (debris > 0.01F) {
                KineticShots.drawRocks(buffer, stage.world, eye, side, up, dir, seconds, debris, Stage.SUN);
            }
        }
        if (type == StrikeType.KINETIC) {
            KineticShots.rod(buffer, new Matrix4f(stage.world).translate(head).scale(k), new Vector3f(), dir, 1.0F);
        } else if (type == StrikeType.METEOR) {
            Matrix3f spin = new Matrix3f().rotateY(seconds * 0.8F).rotateX(seconds * 0.5F);
            MeteorShots.asteroid.draw(buffer, stage.world, head, MeteorShots.ASTEROID_RADIUS * 0.85F, spin, Stage.SUN, 0.3F + 0.5F * heat);
        }
        end(buffer);

        additive();
        buffer = begin();
        Vector3f right = stage.rightWorld;
        Vector3f upW = stage.upWorld;
        switch (type) {
            case KINETIC -> {
                Vector3f tail = new Vector3f(head).fma(-50.0F * k, dir);
                glow(buffer, stage.world, right, upW, tail.x, tail.y, tail.z, 10.0F * k, 1.0F, 0.5F, 0.15F, 0.9F);
                Vector3f nose = new Vector3f(head).fma(48.0F * k, dir);
                float size = (8.0F + 90.0F * heat * heat) * k;
                glow(buffer, stage.world, right, upW, nose.x, nose.y, nose.z, size, 1.0F, 0.5F, 0.15F, 0.85F * heat);
                glow(buffer, stage.world, right, upW, nose.x, nose.y, nose.z, size * 0.35F, 1.0F, 0.95F, 0.8F, heat);
            }
            case METEOR -> {
                for (int i = 0; i < 26; i++) {
                    float fade = 1.0F - i / 26.0F;
                    Vector3f at = new Vector3f(head).fma(-18.0F * i, dir);
                    float wobble = Mth.sin(seconds * 9.0F + i * 0.8F) * 4.0F * heat;
                    glow(buffer, stage.world, right, upW, at.x + wobble, at.y + wobble * 0.5F, at.z,
                            (14.0F + 40.0F * fade) * (0.2F + heat), 1.0F, 0.4F + 0.3F * fade, 0.1F, 0.55F * fade * (0.15F + heat));
                }
                Vector3f nose = new Vector3f(head).fma(ASTEROID_FRONT, dir);
                glow(buffer, stage.world, right, upW, nose.x, nose.y, nose.z, 30.0F + 120.0F * heat * heat, 1.0F, 0.6F, 0.2F, 0.8F * heat);
                glow(buffer, stage.world, right, upW, nose.x, nose.y, nose.z, 12.0F + 50.0F * heat, 1.0F, 0.95F, 0.8F, heat);
            }
            default -> {
                float pulse = 0.8F + 0.2F * Mth.sin(seconds * 40.0F);
                beam(buffer, stage.world, eye, start, head, 16.0F, 0.3F, 0.6F, 1.0F, 0.28F * pulse);
                beam(buffer, stage.world, eye, start, head, 7.0F, 0.45F, 0.85F, 1.0F, 0.65F * pulse);
                beam(buffer, stage.world, eye, start, head, 2.4F, 1.0F, 1.0F, 1.0F, 1.0F);
                glow(buffer, stage.world, right, upW, head.x, head.y, head.z, 16.0F + 80.0F * heat, 1.0F, 0.8F, 0.5F, 0.9F);
                glow(buffer, stage.world, right, upW, head.x, head.y, head.z, 6.0F + 30.0F * heat, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        end(buffer);
    }

    private static final float ASTEROID_FRONT = -MeteorShots.ASTEROID_RADIUS * 0.6F;

    /** Where the projectile is, in the weapon's frame, when the cinematic hands over to the fall. */
    private static Vector3f startLocal(StrikeType type) {
        return switch (type) {
            case KINETIC -> KineticShots.releasePoint().add(0.0F, 0.0F, -KineticShots.released(1.0F));
            case METEOR -> new Vector3f(0.0F, 0.0F, -118.0F - 2600.0F);
            default -> new Vector3f(0.0F, 0.0F, LaserShots.MUZZLE_Z - 900.0F);
        };
    }

    /** How far the ray from start along dir runs before it meets the planet's surface. */
    private static float distanceToEarth(Vector3f start, Vector3f dir) {
        Vector3f offset = new Vector3f(start).sub(Stage.EARTH);
        float b = dir.dot(offset);
        float c = offset.lengthSquared() - Stage.EARTH_RADIUS * Stage.EARTH_RADIUS;
        float discriminant = b * b - c;
        if (discriminant < 0.0F) {
            return Math.max(1.0F, offset.length() - Stage.EARTH_RADIUS);
        }
        return Math.max(1.0F, -b - (float) Math.sqrt(discriminant));
    }

    // ---------------------------------------------------------------- belt

    /** Rocks scattered along the route, so the flight has something to pass. */
    private static void drawBelt(Stage stage, StrikeType type, float seconds) {
        MeteorShots.build();
        if (belt == null) {
            Random random = new Random(5150L);
            belt = new float[BELT_ROCKS * 5];
            Vector3f eye = new Vector3f();
            Vector3f center = new Vector3f();
            for (int i = 0; i < BELT_ROCKS; i++) {
                Stage.route(StrikeType.ORBITAL_LASER, 6.8F + random.nextFloat() * 7.0F, eye, center);
                Vector3f offset = new Vector3f(random.nextFloat() - 0.5F, (random.nextFloat() - 0.5F) * 0.6F, random.nextFloat() - 0.5F)
                        .normalize().mul(70.0F + random.nextFloat() * 420.0F);
                belt[i * 5] = eye.x + offset.x;
                belt[i * 5 + 1] = eye.y + offset.y;
                belt[i * 5 + 2] = eye.z + offset.z;
                belt[i * 5 + 3] = 3.0F + random.nextFloat() * random.nextFloat() * 26.0F;
                belt[i * 5 + 4] = random.nextFloat() * Mth.TWO_PI;
            }
        }
        opaque();
        BufferBuilder buffer = begin();
        Vector3f at = new Vector3f();
        int step = type == StrikeType.METEOR ? 1 : 2;
        for (int i = 0; i < BELT_ROCKS; i += step) {
            at.set(belt[i * 5], belt[i * 5 + 1], belt[i * 5 + 2]);
            Matrix3f spin = new Matrix3f().rotateXYZ(belt[i * 5 + 4] + seconds * 0.3F, belt[i * 5 + 4] * 1.7F, seconds * 0.2F);
            MeteorShots.rock.draw(buffer, stage.world, at, belt[i * 5 + 3], spin, Stage.SUN, 0.04F);
        }
        end(buffer);
    }
}
