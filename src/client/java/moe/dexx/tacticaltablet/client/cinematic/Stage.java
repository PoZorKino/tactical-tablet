package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.additive;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.begin;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.end;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.opaque;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.translucent;

import com.mojang.blaze3d.vertex.BufferBuilder;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One shared piece of space for the whole flight: Earth, Jupiter, Saturn and the weapon. The camera moves through
 * it continuously, so every shot is a view of the same place and nothing is cut. The laser and the magnet stand
 * beside Saturn; the kinetic accelerator is a ring around Jupiter.
 */
final class Stage {
    static final Vector3f SUN = new Vector3f(-0.45F, 0.35F, 0.82F).normalize();

    static final Vector3f EARTH = new Vector3f(0.0F, 0.0F, 0.0F);
    static final float EARTH_RADIUS = 330.0F;
    static final Vector3f JUPITER = new Vector3f(-1800.0F, 300.0F, -4200.0F);
    static final float JUPITER_RADIUS = 700.0F;
    static final Vector3f SATURN = new Vector3f(1900.0F, -250.0F, -8200.0F);
    static final float SATURN_RADIUS = 600.0F;
    static final Matrix3f SATURN_TILT = new Matrix3f().rotateZ(0.42F).rotateX(0.18F);

    /** The weapon's own coordinate system: -Z is the line of fire towards Earth, one local unit is scale world units. */
    static final class Frame {
        final Vector3f origin;
        final float scale;
        final Vector3f fireDir;
        final Matrix3f rotInv;
        final Matrix4f model;
        final Matrix4f modelInv;
        final Vector3f sunLocal;

        /**
         * @param release where the projectile leaves the weapon, in local units, or null when the line of fire
         *                runs through the origin. The frame is turned until the line of fire from that point
         *                passes through Earth, so the projectile leaves straight and never has to turn.
         */
        Frame(Vector3f origin, float scale, Vector3f release) {
            this.origin = origin;
            this.scale = scale;
            Vector3f dir = new Vector3f(EARTH).sub(origin).normalize();
            Matrix3f rot = basis(dir);
            if (release != null) {
                for (int i = 0; i < 12; i++) {
                    Vector3f start = rot.transform(new Vector3f(release).mul(scale)).add(origin);
                    dir = new Vector3f(EARTH).sub(start).normalize();
                    rot = basis(dir);
                }
            }
            this.fireDir = dir;
            this.rotInv = new Matrix3f(rot).transpose();
            this.model = new Matrix4f().translation(origin).mul(new Matrix4f(rot)).scale(scale);
            this.modelInv = new Matrix4f(model).invert();
            this.sunLocal = rotInv.transform(new Vector3f(SUN));
        }

        private static Matrix3f basis(Vector3f dir) {
            Vector3f z = new Vector3f(dir).negate();
            Vector3f x = new Vector3f(0.0F, 1.0F, 0.0F).cross(z).normalize();
            Vector3f y = new Vector3f(z).cross(x);
            return new Matrix3f(x, y, z);
        }

        Vector3f toWorld(Vector3f local) {
            return model.transformPosition(new Vector3f(local));
        }

        Vector3f toLocal(Vector3f world) {
            return modelInv.transformPosition(new Vector3f(world));
        }
    }

    /** Beside Saturn's rings, for the laser and the magnet. */
    static final Frame BESIDE_SATURN = new Frame(new Vector3f(900.0F, -50.0F, -7500.0F), 1.0F, null);
    /** Around Jupiter: the ring is scaled up until it clears the planet. */
    /** The release point is the breech of the accelerator ring: radius 118 at angle pi, tilted 0.3 rad (see KineticShots). */
    static final Frame AROUND_JUPITER = new Frame(JUPITER, 8.0F, new Vector3f(-112.73F, -34.87F, 0.0F));
    static final Vector3f SUN_LOCAL = BESIDE_SATURN.sunLocal;
    static final Vector3f SUN_LOCAL_KINETIC = AROUND_JUPITER.sunLocal;

    private static final Vector3f[] EYE_SATURN = {
            new Vector3f(0.0F, 640.0F, 460.0F),
            new Vector3f(-250.0F, 560.0F, -1500.0F),
            new Vector3f(-250.0F, 300.0F, -3300.0F),
            new Vector3f(700.0F, 80.0F, -5400.0F),
            new Vector3f(BESIDE_SATURN.origin).add(240.0F, 70.0F, 360.0F)
    };
    private static final Vector3f[] LOOK_SATURN = {
            new Vector3f(EARTH),
            new Vector3f(-900.0F, 300.0F, -2600.0F),
            new Vector3f(JUPITER),
            new Vector3f(1700.0F, -250.0F, -7600.0F),
            new Vector3f(BESIDE_SATURN.origin)
    };
    private static final Vector3f[] LOOK_JUPITER = {
            new Vector3f(EARTH),
            new Vector3f(-900.0F, 300.0F, -2600.0F),
            new Vector3f(JUPITER),
            new Vector3f(JUPITER),
            new Vector3f(JUPITER)
    };

    final Frame frame;
    final Matrix4f world;
    /** World view with the weapon's own frame applied: draw weapon geometry with this one. */
    final Matrix4f device;
    final Vector3f eye;
    final Vector3f eyeLocal;
    final Vector3f rightWorld;
    final Vector3f upWorld;
    final Vector3f rightLocal;
    final Vector3f upLocal;

    private Stage(Frame frame, Vector3f eye, Vector3f center) {
        this.frame = frame;
        this.eye = eye;
        this.world = new Matrix4f().lookAt(eye, center, new Vector3f(0.0F, 1.0F, 0.0F));
        this.device = new Matrix4f(world).mul(frame.model);
        this.eyeLocal = frame.toLocal(eye);
        this.rightWorld = world.normalizedPositiveX(new Vector3f());
        this.upWorld = world.normalizedPositiveY(new Vector3f());
        this.rightLocal = frame.rotInv.transform(new Vector3f(rightWorld));
        this.upLocal = frame.rotInv.transform(new Vector3f(upWorld));
    }

    static Frame frameOf(StrikeType type) {
        return type == StrikeType.KINETIC ? AROUND_JUPITER : BESIDE_SATURN;
    }

    static Stage looking(StrikeType type, Vector3f eye, Vector3f center) {
        return new Stage(frameOf(type), eye, center);
    }

    /** Camera position and target along the flight for the given second of the cinematic. */
    static void route(StrikeType type, float seconds, Vector3f eyeOut, Vector3f centerOut) {
        float s = Mth.clamp((seconds - 6.0F) / 8.0F, 0.0F, 1.0F);
        // Starts from rest, cruises past the planets, and settles at the weapon.
        float u = 4.0F * (s * s * (3.0F - 2.0F * s));
        if (type == StrikeType.KINETIC) {
            // Straight to Jupiter, ending exactly where the camera around the ring starts.
            Vector3f arrival = new Vector3f();
            Vector3f ignored = new Vector3f();
            KineticShots.camera(Segment.CANNON, 0.0F, 14.0F, arrival, ignored);
            Vector3f end = AROUND_JUPITER.toWorld(arrival);
            Vector3f[] eyes = {
                    EYE_SATURN[0],
                    EYE_SATURN[1],
                    new Vector3f(JUPITER).add(2600.0F, 500.0F, 3300.0F),
                    new Vector3f(JUPITER).add(2100.0F, 700.0F, 1900.0F).lerp(end, 0.5F),
                    end
            };
            spline(eyes, u, eyeOut);
            spline(LOOK_JUPITER, u, centerOut);
            return;
        }
        spline(EYE_SATURN, u, eyeOut);
        spline(LOOK_SATURN, u, centerOut);
    }

    private static void spline(Vector3f[] knots, float u, Vector3f out) {
        int last = knots.length - 1;
        int i = Mth.clamp((int) Math.floor(u), 0, last - 1);
        float f = Mth.clamp(u - i, 0.0F, 1.0F);
        Vector3f p0 = knots[Math.max(i - 1, 0)];
        Vector3f p1 = knots[i];
        Vector3f p2 = knots[i + 1];
        Vector3f p3 = knots[Math.min(i + 2, last)];
        float f2 = f * f;
        float f3 = f2 * f;
        out.set(0.0F, 0.0F, 0.0F);
        for (int axis = 0; axis < 3; axis++) {
            float a = p0.get(axis);
            float b = p1.get(axis);
            float c = p2.get(axis);
            float d = p3.get(axis);
            out.setComponent(axis, 0.5F * ((2.0F * b) + (-a + c) * f + (2.0F * a - 5.0F * b + 4.0F * c - d) * f2 + (-a + 3.0F * b - 3.0F * c + d) * f3));
        }
    }

    /** The three planets, their moons and Saturn's rings, in world space. */
    void planets(float time) {
        Matrix3f earthSpin = new Matrix3f().rotateZ(1.3F).rotateY(time * 0.01F);
        Matrix3f jupiterSpin = new Matrix3f().rotateZ(0.05F).rotateY(time * 0.06F);
        Matrix3f saturnSpin = new Matrix3f(SATURN_TILT).rotateY(time * 0.05F);
        Matrix3f still = new Matrix3f();

        opaque();
        BufferBuilder buffer = begin();
        SpaceScene.earth.draw(buffer, world, EARTH, EARTH_RADIUS, earthSpin, SUN, 0.32F);
        SpaceScene.jupiter.draw(buffer, world, JUPITER, JUPITER_RADIUS, jupiterSpin, SUN, 0.035F);
        SpaceScene.saturn.draw(buffer, world, SATURN, SATURN_RADIUS, saturnSpin, SUN, 0.035F);
        SpaceScene.moon.draw(buffer, world, new Vector3f(JUPITER).add(1150.0F, 90.0F, 380.0F), 55.0F, still, SUN, 0.03F);
        SpaceScene.moon.draw(buffer, world, new Vector3f(JUPITER).add(-900.0F, -180.0F, 720.0F), 36.0F, still, SUN, 0.03F);
        SpaceScene.moon.draw(buffer, world, new Vector3f(SATURN).add(-950.0F, 420.0F, 300.0F), 44.0F, still, SUN, 0.03F);
        end(buffer);

        translucent();
        buffer = begin();
        SpaceScene.rings(buffer, world, SATURN, SATURN_TILT, SUN, SATURN_RADIUS, SATURN_RADIUS * 1.25F, SATURN_RADIUS * 2.33F);
        end(buffer);

        additive();
        buffer = begin();
        SpaceScene.earth.drawRim(buffer, world, eye, EARTH, EARTH_RADIUS * 1.025F, earthSpin, 0.35F, 0.6F, 1.0F, 1.6F);
        SpaceScene.jupiter.drawRim(buffer, world, eye, JUPITER, JUPITER_RADIUS * 1.02F, jupiterSpin, 0.95F, 0.72F, 0.5F, 0.45F);
        SpaceScene.saturn.drawRim(buffer, world, eye, SATURN, SATURN_RADIUS * 1.02F, saturnSpin, 0.95F, 0.85F, 0.6F, 0.4F);
        end(buffer);
    }
}
