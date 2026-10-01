package moe.dexx.tacticaltablet.client.cinematic;

import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.additive;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.begin;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.end;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.opaque;
import static moe.dexx.tacticaltablet.client.cinematic.SpaceScene.translucent;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One shared piece of space for the whole flight: Earth, Jupiter, Saturn and, near Saturn, the weapon. The camera
 * moves through it continuously, so every shot is a view of the same place and nothing is cut.
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

    /** Where the weapon hangs, in front of Saturn's rings and facing Earth. */
    static final Vector3f DEVICE = new Vector3f(900.0F, -50.0F, -7500.0F);
    static final Vector3f FIRE_DIR = new Vector3f(EARTH).sub(DEVICE).normalize();
    static final Matrix3f DEVICE_ROT;
    static final Matrix3f DEVICE_ROT_INV;
    static final Matrix4f DEVICE_MODEL;
    static final Matrix4f DEVICE_MODEL_INV;
    static final Vector3f SUN_LOCAL;

    /** Camera knots, one every two seconds from the end of the ascent (6 s) to the arrival at the weapon (14 s). */
    private static final Vector3f[] EYE = {
            new Vector3f(0.0F, 640.0F, 460.0F),
            new Vector3f(-250.0F, 560.0F, -1500.0F),
            new Vector3f(-250.0F, 300.0F, -3300.0F),
            new Vector3f(700.0F, 80.0F, -5400.0F),
            new Vector3f(DEVICE).add(240.0F, 70.0F, 360.0F)
    };
    private static final Vector3f[] LOOK = {
            new Vector3f(EARTH),
            new Vector3f(-900.0F, 300.0F, -2600.0F),
            new Vector3f(JUPITER),
            new Vector3f(1700.0F, -250.0F, -7600.0F),
            new Vector3f(DEVICE)
    };

    static {
        Vector3f z = new Vector3f(FIRE_DIR).negate();
        Vector3f x = new Vector3f(0.0F, 1.0F, 0.0F).cross(z).normalize();
        Vector3f y = new Vector3f(z).cross(x);
        DEVICE_ROT = new Matrix3f(x, y, z);
        DEVICE_ROT_INV = new Matrix3f(DEVICE_ROT).transpose();
        DEVICE_MODEL = new Matrix4f().translation(DEVICE).mul(new Matrix4f(DEVICE_ROT));
        DEVICE_MODEL_INV = new Matrix4f(DEVICE_MODEL).invert();
        SUN_LOCAL = DEVICE_ROT_INV.transform(new Vector3f(SUN));
    }

    final Matrix4f world;
    /** World view with the weapon's own frame applied: draw weapon geometry with this one. */
    final Matrix4f device;
    final Vector3f eye;
    final Vector3f eyeLocal;
    final Vector3f rightWorld;
    final Vector3f upWorld;
    final Vector3f rightLocal;
    final Vector3f upLocal;

    private Stage(Vector3f eye, Vector3f center) {
        this.eye = eye;
        this.world = new Matrix4f().lookAt(eye, center, new Vector3f(0.0F, 1.0F, 0.0F));
        this.device = new Matrix4f(world).mul(DEVICE_MODEL);
        this.eyeLocal = toLocal(eye);
        this.rightWorld = world.normalizedPositiveX(new Vector3f());
        this.upWorld = world.normalizedPositiveY(new Vector3f());
        this.rightLocal = DEVICE_ROT_INV.transform(new Vector3f(rightWorld));
        this.upLocal = DEVICE_ROT_INV.transform(new Vector3f(upWorld));
    }

    /** Where the route ends, beside the weapon. */
    static Vector3f arrivalEye() {
        return new Vector3f(EYE[EYE.length - 1]);
    }

    static Stage looking(Vector3f eye, Vector3f center) {
        return new Stage(eye, center);
    }

    static Vector3f toWorld(Vector3f local) {
        return DEVICE_MODEL.transformPosition(new Vector3f(local));
    }

    static Vector3f toLocal(Vector3f world) {
        return DEVICE_MODEL_INV.transformPosition(new Vector3f(world));
    }

    /** Camera position and target along the flight for the given second of the cinematic. */
    static void route(float seconds, Vector3f eyeOut, Vector3f centerOut) {
        float s = Mth.clamp((seconds - 6.0F) / 8.0F, 0.0F, 1.0F);
        // Starts from rest, cruises past Jupiter, and settles at the weapon.
        float u = 4.0F * (s * s * (3.0F - 2.0F * s));
        spline(EYE, u, eyeOut);
        spline(LOOK, u, centerOut);
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
        SpaceScene.earth.draw(buffer, world, EARTH, EARTH_RADIUS, earthSpin, SUN, 0.03F);
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
