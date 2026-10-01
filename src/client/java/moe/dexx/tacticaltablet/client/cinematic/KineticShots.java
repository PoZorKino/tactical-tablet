package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.ease;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.glow;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.mix;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.shade;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Random;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The kinetic rod: a tungsten needle loaded into a ring accelerator beside Saturn, spun up over seven laps and
 * released along the line of fire. The ring lies in the weapon's frame, with its release point at the -Z side.
 */
final class KineticShots {
    static final int LAPS = 7;
    /** Angle of the breech on the ring; the tangent there points down the line of fire. */
    static final float STATION = Mth.PI;
    private static final float RING_RADIUS = 118.0F;
    private static final float TUBE = 7.0F;
    private static final int SEGMENTS = 96;
    private static final int SIDES = 12;
    private static final float ROD_LENGTH = 46.0F;
    private static final float ROD_RADIUS = 2.4F;
    private static final int ROCK_COUNT = 56;
    private static final Matrix3f TILT = new Matrix3f().rotateZ(0.3F);
    private static final Vector3f SUN = Stage.SUN_LOCAL_KINETIC;
    private static final float[] ORANGE = {1.0F, 0.42F, 0.12F};

    private static SphereMesh rock;
    private static float[] rocks;

    private KineticShots() {
    }

    /** Angle covered since the start of the laps; slow at first, then faster, so each lap is shorter. */
    static float angle(float p) {
        return Mth.TWO_PI * LAPS * p * p;
    }

    static int lap(float p) {
        return Math.min(LAPS, (int) (angle(p) / Mth.TWO_PI) + 1);
    }

    /** Speed as a fraction of light speed. */
    static float velocity(float p) {
        return 0.17F + 0.6F * p;
    }

    /** How far down the line of fire the released rod has got, in local units. */
    static float released(float p) {
        return p * p * 900.0F;
    }

    private static Vector3f outward() {
        Vector3f out = new Vector3f();
        ringPoint(STATION, out);
        return out.normalize();
    }

    private static Vector3f tiltedUp() {
        return TILT.transform(new Vector3f(0.0F, 1.0F, 0.0F));
    }

    static Vector3f releasePoint() {
        Vector3f out = new Vector3f();
        ringPoint(STATION, out);
        return out;
    }

    /** Camera in the weapon's frame: around the ring, then inside its tube, then beside the release. */
    static void camera(Segment segment, float p, float time, Vector3f eye, Vector3f center) {
        if (segment == Segment.CANNON) {
            float theta = STATION + angle(p);
            float inside = ease(Mth.clamp((p - 0.42F) / 0.2F, 0.0F, 1.0F));
            float orbit = 0.5F + time * 0.18F;
            Vector3f outsideEye = new Vector3f(Mth.cos(orbit) * 330.0F, 120.0F - 40.0F * p, Mth.sin(orbit) * 330.0F);
            Vector3f behind = new Vector3f();
            ringPoint(theta - 0.02F - 0.05F * p, behind);
            Vector3f ahead = new Vector3f();
            ringPoint(theta + 0.35F, ahead);
            eye.set(mix(outsideEye, behind, inside));
            center.set(mix(new Vector3f(), ahead, inside));
        } else {
            Vector3f station = releasePoint();
            Vector3f tangent = tangent(STATION);
            eye.set(station).sub(new Vector3f(tangent).mul(70.0F)).add(new Vector3f(outward()).mul(34.0F)).add(new Vector3f(tiltedUp()).mul(14.0F));
            center.set(station).add(new Vector3f(tangent).mul(160.0F + released(p) * 0.6F));
        }
    }

    static void solid(BufferBuilder buffer, Stage stage, Segment segment, float p, float time) {
        Matrix4f view = stage.device;
        Vector3f station = releasePoint();
        Vector3f tangent = tangent(STATION);
        switch (segment) {
            case SATURN -> {
                float lit = Mth.clamp((p - 0.5F) * 2.0F, 0.0F, 1.0F);
                ring(buffer, view, lit, time, -1.0F);
                station(buffer, view, station, outward(), tiltedUp());
                rod(buffer, view, station, tangent, 0.2F + 0.8F * lit);
            }
            case CANNON -> {
                float theta = STATION + angle(p);
                Vector3f rodPos = new Vector3f();
                ringPoint(theta, rodPos);
                ring(buffer, view, 1.0F, time, theta);
                float inside = ease(Mth.clamp((p - 0.42F) / 0.2F, 0.0F, 1.0F));
                if (inside < 0.97F) {
                    rod(buffer, view, rodPos, tangent(theta), 1.0F);
                }
            }
            default -> {
                ring(buffer, view, 1.0F, time, STATION);
                rod(buffer, view, new Vector3f(station).add(new Vector3f(tangent).mul(released(p))), tangent, 1.0F);
            }
        }
    }

    static void glows(BufferBuilder buffer, Stage stage, Segment segment, float p, float time) {
        Matrix4f view = stage.device;
        Vector3f right = stage.rightLocal;
        Vector3f up = stage.upLocal;
        Vector3f station = releasePoint();
        Vector3f tangent = tangent(STATION);
        switch (segment) {
            case SATURN -> {
                float flash = (float) Math.max(0.0, Math.sin(p * Mth.PI * 3.0F)) * 0.5F;
                glow(buffer, view, right, up, station.x, station.y, station.z, 22.0F + 10.0F * flash, 1.0F, 0.55F, 0.2F, 0.3F + flash);
            }
            case CANNON -> {
                float theta = STATION + angle(p);
                Vector3f rodPos = new Vector3f();
                ringPoint(theta, rodPos);
                float inside = ease(Mth.clamp((p - 0.42F) / 0.2F, 0.0F, 1.0F));
                Vector3f trail = new Vector3f();
                for (int i = 0; i < 14; i++) {
                    float fade = 1.0F - i / 14.0F;
                    ringPoint(theta - i * 0.045F * (0.4F + p), trail);
                    glow(buffer, view, right, up, trail.x, trail.y, trail.z, 7.0F * fade + 2.0F, 1.0F, 0.5F, 0.15F, 0.55F * fade * (1.0F - inside * 0.8F));
                }
                glow(buffer, view, right, up, rodPos.x, rodPos.y, rodPos.z, 15.0F + 12.0F * p, 1.0F, 0.75F, 0.4F, 0.8F * (1.0F - inside));
            }
            default -> {
                float gone = released(p);
                Vector3f rodPos = new Vector3f(station).add(new Vector3f(tangent).mul(gone));
                Vector3f outward = outward();
                Vector3f tUp = tiltedUp();
                float ringRadius = 6.0F + 90.0F * ease(p);
                for (int i = 0; i < 40; i++) {
                    float a = Mth.TWO_PI * i / 40;
                    Vector3f at = new Vector3f(station).add(new Vector3f(outward).mul(Mth.cos(a) * ringRadius)).add(new Vector3f(tUp).mul(Mth.sin(a) * ringRadius));
                    glow(buffer, view, right, up, at.x, at.y, at.z, 6.0F, 1.0F, 0.85F, 0.6F, 0.5F * (1.0F - p));
                }
                glow(buffer, view, right, up, station.x, station.y, station.z, 40.0F * (1.0F - p) + 6.0F, 1.0F, 0.9F, 0.7F, 0.9F);
                for (int i = 0; i < 24; i++) {
                    float fade = 1.0F - i / 24.0F;
                    Vector3f at = new Vector3f(rodPos).sub(new Vector3f(tangent).mul(i * 9.0F));
                    glow(buffer, view, right, up, at.x, at.y, at.z, 4.0F + 6.0F * fade, 1.0F, 0.55F, 0.2F, 0.45F * fade);
                }
            }
        }
    }

    // ---------------------------------------------------------------- pieces

    private static void ringPoint(float angle, Vector3f out) {
        out.set(Mth.cos(angle) * RING_RADIUS, 0.0F, Mth.sin(angle) * RING_RADIUS);
        TILT.transform(out);
    }

    private static Vector3f tangent(float angle) {
        Vector3f out = new Vector3f(-Mth.sin(angle), 0.0F, Mth.cos(angle));
        TILT.transform(out);
        return out;
    }

    /**
     * The accelerator ring. Coils up to the lit fraction glow orange; with a rod angle given, a bright wave runs
     * along the coils just behind the rod.
     */
    private static void ring(BufferBuilder buffer, Matrix4f view, float lit, float time, float rodAngle) {
        Vector3f point = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            boolean coil = i % 4 < 2;
            float tube = coil ? TUBE * 1.3F : TUBE;
            float glowAmount = 0.0F;
            if (coil) {
                float fraction = (float) i / SEGMENTS;
                glowAmount = fraction <= lit ? 0.55F + 0.25F * Mth.sin(time * 6.0F + i) : 0.08F;
                if (rodAngle >= 0.0F) {
                    float behind = Mth.positiveModulo(rodAngle - a0, Mth.TWO_PI);
                    glowAmount = Math.max(glowAmount, Math.max(0.0F, 1.0F - behind * 2.2F));
                }
            }
            for (int s = 0; s < SIDES; s++) {
                float f0 = Mth.TWO_PI * s / SIDES;
                float f1 = Mth.TWO_PI * (s + 1) / SIDES;
                torusVertex(buffer, view, point, normal, a0, f0, tube, glowAmount);
                torusVertex(buffer, view, point, normal, a0, f1, tube, glowAmount);
                torusVertex(buffer, view, point, normal, a1, f1, tube, glowAmount);
                torusVertex(buffer, view, point, normal, a1, f0, tube, glowAmount);
            }
        }
    }

    private static void torusVertex(BufferBuilder buffer, Matrix4f view, Vector3f point, Vector3f normal,
                                    float angle, float around, float tube, float glowAmount) {
        float cosA = Mth.cos(angle);
        float sinA = Mth.sin(angle);
        float radial = RING_RADIUS + tube * Mth.cos(around);
        point.set(radial * cosA, tube * Mth.sin(around), radial * sinA);
        TILT.transform(point);
        normal.set(Mth.cos(around) * cosA, Mth.sin(around), Mth.cos(around) * sinA);
        TILT.transform(normal);
        float light = Math.max(0.3F, shade(SUN, normal.x, normal.y, normal.z, 0.0F));
        float metal = 0.2F * light;
        buffer.vertex(view, point.x, point.y, point.z)
                .color(Math.min(1.0F, metal + ORANGE[0] * glowAmount), Math.min(1.0F, metal * 1.05F + ORANGE[1] * glowAmount),
                        Math.min(1.0F, metal * 1.15F + ORANGE[2] * glowAmount), 1.0F)
                .endVertex();
    }

    /** The loading gantry at the breech: a frame either side of the ring. */
    private static void station(BufferBuilder buffer, Matrix4f view, Vector3f at, Vector3f outward, Vector3f up) {
        Vector3f tangent = tangent(STATION);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f arm = new Vector3f(at).add(new Vector3f(tangent).mul(side * 26.0F));
            Vector3f lo = new Vector3f(arm).sub(new Vector3f(up).mul(24.0F));
            Vector3f hi = new Vector3f(arm).add(new Vector3f(up).mul(24.0F));
            beam3d(buffer, view, lo, hi, 2.4F);
        }
        Vector3f a = new Vector3f(at).add(new Vector3f(tangent).mul(-26.0F)).add(new Vector3f(up).mul(24.0F));
        Vector3f b = new Vector3f(at).add(new Vector3f(tangent).mul(26.0F)).add(new Vector3f(up).mul(24.0F));
        beam3d(buffer, view, a, b, 2.4F);
        Vector3f c = new Vector3f(at).add(new Vector3f(tangent).mul(-26.0F)).sub(new Vector3f(up).mul(24.0F));
        Vector3f d = new Vector3f(at).add(new Vector3f(tangent).mul(26.0F)).sub(new Vector3f(up).mul(24.0F));
        beam3d(buffer, view, c, d, 2.4F);
    }

    /** A square strut between two points. */
    private static void beam3d(BufferBuilder buffer, Matrix4f view, Vector3f from, Vector3f to, float half) {
        Vector3f axis = new Vector3f(to).sub(from);
        Vector3f u = perpendicular(new Vector3f(axis).normalize());
        Vector3f v = new Vector3f(axis).normalize().cross(u);
        u.mul(half);
        v.mul(half);
        float[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        for (int i = 0; i < 4; i++) {
            float[] c0 = corners[i];
            float[] c1 = corners[(i + 1) % 4];
            float light = 0.18F + 0.12F * i;
            put(buffer, view, from, u, v, c0, light);
            put(buffer, view, from, u, v, c1, light);
            put(buffer, view, to, u, v, c1, light);
            put(buffer, view, to, u, v, c0, light);
        }
    }

    private static void put(BufferBuilder buffer, Matrix4f view, Vector3f at, Vector3f u, Vector3f v, float[] corner, float light) {
        buffer.vertex(view, at.x + u.x * corner[0] + v.x * corner[1], at.y + u.y * corner[0] + v.y * corner[1],
                at.z + u.z * corner[0] + v.z * corner[1]).color(light, light * 1.05F, light * 1.15F, 1.0F).endVertex();
    }

    private static Vector3f perpendicular(Vector3f direction) {
        Vector3f helper = Math.abs(direction.y) > 0.9F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        return helper.cross(direction).normalize();
    }

    /** The needle: dark segmented body with glowing orange bands and a pointed nose, flying along dir. */
    static void rod(BufferBuilder buffer, Matrix4f view, Vector3f center, Vector3f dir, float glowAmount) {
        Vector3f forward = new Vector3f(dir).normalize();
        Vector3f u = perpendicular(forward);
        Vector3f v = new Vector3f(forward).cross(u);
        int sections = 20;
        int sides = 8;
        Vector3f p0 = new Vector3f();
        for (int k = 0; k < sections; k++) {
            float x0 = (float) k / sections;
            float x1 = (float) (k + 1) / sections;
            float r0 = ROD_RADIUS * noseTaper(x0);
            float r1 = ROD_RADIUS * noseTaper(x1);
            boolean band = k % 4 == 1;
            float glowing = band ? glowAmount : 0.0F;
            for (int s = 0; s < sides; s++) {
                float a0 = Mth.TWO_PI * s / sides;
                float a1 = Mth.TWO_PI * (s + 1) / sides;
                float light = Math.max(0.35F, shade(SUN, u.x * Mth.cos(a0) + v.x * Mth.sin(a0),
                        u.y * Mth.cos(a0) + v.y * Mth.sin(a0), u.z * Mth.cos(a0) + v.z * Mth.sin(a0), 0.0F));
                float body = 0.2F * light;
                // Vertex colours must stay within 0..1: the buffer packs them into bytes and wraps anything larger.
                float red = Math.min(1.0F, body + ORANGE[0] * glowing);
                float green = Math.min(1.0F, body + ORANGE[1] * glowing);
                float blue = Math.min(1.0F, body * 1.1F + ORANGE[2] * glowing);
                rodVertex(buffer, view, p0, center, forward, u, v, x0, a0, r0, red, green, blue);
                rodVertex(buffer, view, p0, center, forward, u, v, x0, a1, r0, red, green, blue);
                rodVertex(buffer, view, p0, center, forward, u, v, x1, a1, r1, red, green, blue);
                rodVertex(buffer, view, p0, center, forward, u, v, x1, a0, r1, red, green, blue);
            }
        }
    }

    /** 1 along the body, shrinking to a point over the front fifth. */
    private static float noseTaper(float x) {
        return x < 0.8F ? 1.0F : Math.max(0.0F, 1.0F - (x - 0.8F) / 0.2F);
    }

    private static void rodVertex(BufferBuilder buffer, Matrix4f view, Vector3f scratch, Vector3f center, Vector3f forward,
                                  Vector3f u, Vector3f v, float along, float around, float radius, float red, float green, float blue) {
        float t = (along - 0.5F) * ROD_LENGTH;
        float c = Mth.cos(around) * radius;
        float s = Mth.sin(around) * radius;
        scratch.set(center.x + forward.x * t + u.x * c + v.x * s, center.y + forward.y * t + u.y * c + v.y * s,
                center.z + forward.z * t + u.z * c + v.z * s);
        buffer.vertex(view, scratch.x, scratch.y, scratch.z).color(red, green, blue, 1.0F).endVertex();
    }

    // ---------------------------------------------------------------- debris

    private static void buildRocks() {
        if (rock != null) {
            return;
        }
        rock = new SphereMesh(8, 12, (x, y, z, out) -> {
            float tone = 0.3F + 0.3F * SpaceScene.fbm(x * 3.0F + 5.0F, y * 3.0F, z * 3.0F, 3);
            out[0] = tone;
            out[1] = tone * 0.95F;
            out[2] = tone * 0.9F;
        }, (x, y, z) -> 0.7F + 0.5F * SpaceScene.fbm(x * 2.2F + 9.0F, y * 2.2F + 1.0F, z * 2.2F, 3));
        Random random = new Random(77L);
        rocks = new float[ROCK_COUNT * 5];
        for (int i = 0; i < ROCK_COUNT; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float radius = 14.0F + random.nextFloat() * 130.0F;
            rocks[i * 5] = Mth.cos(angle) * radius;
            rocks[i * 5 + 1] = Mth.sin(angle) * radius * 0.7F;
            rocks[i * 5 + 2] = random.nextFloat() * 900.0F;
            rocks[i * 5 + 3] = 1.5F + random.nextFloat() * random.nextFloat() * 11.0F;
            rocks[i * 5 + 4] = random.nextFloat() * Mth.TWO_PI;
        }
    }

    /** Debris around the flight path, streaming towards the camera along the direction of travel. */
    static void drawRocks(BufferBuilder buffer, Matrix4f view, Vector3f eye, Vector3f side, Vector3f up, Vector3f forward,
                          float time, float amount, Vector3f sun) {
        buildRocks();
        Vector3f center = new Vector3f();
        for (int i = 0; i < ROCK_COUNT; i++) {
            int o = i * 5;
            float ahead = 900.0F - (rocks[o + 2] + time * 420.0F) % 900.0F;
            center.set(eye).fma(rocks[o], side).fma(rocks[o + 1], up).fma(ahead, forward);
            Matrix3f spin = new Matrix3f().rotateXYZ(rocks[o + 4] + time * 0.7F, rocks[o + 4] * 2.0F, time * 0.4F);
            rock.draw(buffer, view, center, rocks[o + 3] * amount, spin, sun, 0.05F);
        }
    }
}
