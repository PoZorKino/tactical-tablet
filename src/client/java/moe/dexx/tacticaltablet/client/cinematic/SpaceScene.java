package moe.dexx.tacticaltablet.client.cinematic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.Random;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The shots filmed outside the world: the fly-bys of Jupiter and Saturn, the cannon charging at its station beyond
 * Saturn, the salvo and the dive back to the home planet. Drawn over the whole screen with its own perspective projection; every surface
 * is computed in code, so there are no textures to load.
 */
public final class SpaceScene {
    private static final float FOV = (float) Math.toRadians(58.0);
    private static final float NEAR = 1.0F;
    private static final float FAR = 40000.0F;
    private static final int STAR_COUNT = 1600;
    static final int SPARK_COUNT = 48;

            private static final Vector3f CANNON_SATURN_CENTER = new Vector3f(-420.0F, -30.0F, -380.0F);

    static SphereMesh jupiter;
    static SphereMesh saturn;
    static SphereMesh earth;
    static SphereMesh moon;
    private static float[] stars;
    static float[] sparks;

    private SpaceScene() {
    }

    public static void render(GuiGraphics graphics, StrikeType type, Segment segment, double progress, double seconds) {
        build();
        graphics.flush();
        Window window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(FOV, aspect, NEAR, FAR), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.clearColor(0.0F, 0.0F, 0.012F, 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableCull();
        try {
            float p = (float) progress;
            float time = (float) seconds;
            Voyage.render(type, segment, p, time);
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            // The interface drawn after this must not be hidden behind the scene's depth.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // ---------------------------------------------------------------- scene pieces

    /** Stars and the sun, drawn behind everything. */
    static void backdrop(Vector3f eye, Vector3f center, Vector3f sun) {
        // Rotation only: the sky stays infinitely far away.
        Matrix4f view = new Matrix4f().lookAt(0.0F, 0.0F, 0.0F, center.x - eye.x, center.y - eye.y, center.z - eye.z, 0.0F, 1.0F, 0.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder buffer = begin();
        Vector3f direction = new Vector3f();
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int i = 0; i < STAR_COUNT; i++) {
            int o = i * 6;
            direction.set(stars[o], stars[o + 1], stars[o + 2]);
            float size = stars[o + 3];
            float brightness = stars[o + 4];
            float warmth = stars[o + 5];
            if (Math.abs(direction.y) > 0.95F) {
                a.set(1.0F, 0.0F, 0.0F);
            } else {
                a.set(0.0F, 1.0F, 0.0F);
            }
            direction.cross(a, a).normalize().mul(size);
            direction.cross(a, b).normalize().mul(size);
            float x = direction.x * 1000.0F;
            float y = direction.y * 1000.0F;
            float z = direction.z * 1000.0F;
            float red = 0.8F + 0.2F * warmth;
            float blue = 1.0F - 0.25F * warmth;
            buffer.vertex(view, x - a.x - b.x, y - a.y - b.y, z - a.z - b.z).color(red, 0.9F, blue, brightness).endVertex();
            buffer.vertex(view, x + a.x - b.x, y + a.y - b.y, z + a.z - b.z).color(red, 0.9F, blue, brightness).endVertex();
            buffer.vertex(view, x + a.x + b.x, y + a.y + b.y, z + a.z + b.z).color(red, 0.9F, blue, brightness).endVertex();
            buffer.vertex(view, x - a.x + b.x, y - a.y + b.y, z - a.z + b.z).color(red, 0.9F, blue, brightness).endVertex();
        }
        Vector3f right = view.normalizedPositiveX(new Vector3f());
        Vector3f up = view.normalizedPositiveY(new Vector3f());
        glow(buffer, view, right, up, sun.x * 900.0F, sun.y * 900.0F, sun.z * 900.0F, 230.0F, 1.0F, 0.9F, 0.7F, 0.35F);
        glow(buffer, view, right, up, sun.x * 900.0F, sun.y * 900.0F, sun.z * 900.0F, 42.0F, 1.0F, 1.0F, 0.95F, 1.0F);
        end(buffer);
    }

    static void rings(BufferBuilder buffer, Matrix4f view, Vector3f center, Matrix3f tilt, Vector3f sun,
                              float planetRadius, float inner, float outer) {
        int radial = 48;
        int around = 144;
        float[] bandA = new float[4];
        float[] bandB = new float[4];
        Vector3f point = new Vector3f();
        for (int i = 0; i < radial; i++) {
            float f0 = (float) i / radial;
            float f1 = (float) (i + 1) / radial;
            float r0 = Mth.lerp(f0, inner, outer);
            float r1 = Mth.lerp(f1, inner, outer);
            ringBand(f0, bandA);
            ringBand(f1, bandB);
            for (int j = 0; j < around; j++) {
                float a0 = Mth.TWO_PI * j / around;
                float a1 = Mth.TWO_PI * (j + 1) / around;
                ringVertex(buffer, view, center, tilt, sun, planetRadius, point, a0, r0, bandA);
                ringVertex(buffer, view, center, tilt, sun, planetRadius, point, a1, r0, bandA);
                ringVertex(buffer, view, center, tilt, sun, planetRadius, point, a1, r1, bandB);
                ringVertex(buffer, view, center, tilt, sun, planetRadius, point, a0, r1, bandB);
            }
        }
    }

    private static void ringVertex(BufferBuilder buffer, Matrix4f view, Vector3f center, Matrix3f tilt, Vector3f sun,
                                   float planetRadius, Vector3f point, float angle, float radius, float[] band) {
        point.set(Mth.cos(angle) * radius, 0.0F, Mth.sin(angle) * radius);
        tilt.transform(point);
        // The planet throws its shadow across the far side of the rings.
        float shade = 1.0F;
        float along = point.dot(sun);
        if (along < 0.0F) {
            float off = (float) Math.sqrt(Math.max(0.0F, point.lengthSquared() - along * along));
            shade = Mth.clamp((off - planetRadius * 0.96F) / (planetRadius * 0.1F), 0.1F, 1.0F);
        }
        buffer.vertex(view, center.x + point.x, center.y + point.y, center.z + point.z)
                .color(band[0] * shade, band[1] * shade, band[2] * shade, band[3]).endVertex();
    }

    /** Colour and opacity of the rings at the given fraction of their width, 0 at the inner edge. */
    private static void ringBand(float f, float[] out) {
        float alpha;
        if (f < 0.16F) {
            alpha = 0.16F + f;
        } else if (f < 0.56F) {
            alpha = 0.82F;
        } else if (f < 0.63F) {
            alpha = 0.05F;
        } else if (f < 0.92F) {
            alpha = 0.62F;
        } else {
            alpha = 0.62F * (1.0F - (f - 0.92F) / 0.08F);
        }
        float fine = 0.8F + 0.2F * Mth.sin(f * 90.0F);
        float tone = 0.5F + 0.5F * Mth.sin(f * 23.0F);
        out[0] = Mth.lerp(tone, 0.82F, 0.64F) * fine;
        out[1] = Mth.lerp(tone, 0.74F, 0.56F) * fine;
        out[2] = Mth.lerp(tone, 0.58F, 0.43F) * fine;
        out[3] = Math.max(0.0F, alpha * fine);
    }

    // ---------------------------------------------------------------- primitives

    static BufferBuilder begin() {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return buffer;
    }

    static void end(BufferBuilder buffer) {
        BufferUploader.drawWithShader(buffer.end());
    }

    static void opaque() {
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    static void translucent() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
    }

    static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
    }

    static float shade(Vector3f sun, float nx, float ny, float nz, float emissive) {
        float lit = 0.14F + 0.86F * Math.max(0.0F, nx * sun.x + ny * sun.y + nz * sun.z);
        return emissive + (1.0F - emissive) * lit;
    }

    /** A round tube or cone along the Z axis. */
    static void tube(BufferBuilder buffer, Matrix4f view, Vector3f sun, float z0, float r0, float z1, float r1,
                             int sides, float red, float green, float blue, float emissive, float alpha) {
        for (int i = 0; i < sides; i++) {
            float a0 = Mth.TWO_PI * i / sides;
            float a1 = Mth.TWO_PI * (i + 1) / sides;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float l0 = shade(sun, c0, s0, 0.0F, emissive);
            float l1 = shade(sun, c1, s1, 0.0F, emissive);
            buffer.vertex(view, c0 * r0, s0 * r0, z0).color(red * l0, green * l0, blue * l0, alpha).endVertex();
            buffer.vertex(view, c1 * r0, s1 * r0, z0).color(red * l1, green * l1, blue * l1, alpha).endVertex();
            buffer.vertex(view, c1 * r1, s1 * r1, z1).color(red * l1, green * l1, blue * l1, alpha).endVertex();
            buffer.vertex(view, c0 * r1, s0 * r1, z1).color(red * l0, green * l0, blue * l0, alpha).endVertex();
        }
    }

    /** A flat ring facing along the Z axis; facing is +1 or -1. */
    static void disc(BufferBuilder buffer, Matrix4f view, Vector3f sun, float z, float inner, float outer, int sides,
                             float facing, float red, float green, float blue, float emissive) {
        float light = shade(sun, 0.0F, 0.0F, facing, emissive);
        for (int i = 0; i < sides; i++) {
            float a0 = Mth.TWO_PI * i / sides;
            float a1 = Mth.TWO_PI * (i + 1) / sides;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            buffer.vertex(view, c0 * inner, s0 * inner, z).color(red * light, green * light, blue * light, 1.0F).endVertex();
            buffer.vertex(view, c1 * inner, s1 * inner, z).color(red * light, green * light, blue * light, 1.0F).endVertex();
            buffer.vertex(view, c1 * outer, s1 * outer, z).color(red * light, green * light, blue * light, 1.0F).endVertex();
            buffer.vertex(view, c0 * outer, s0 * outer, z).color(red * light, green * light, blue * light, 1.0F).endVertex();
        }
    }

    static void box(BufferBuilder buffer, Matrix4f view, Vector3f sun, float x0, float y0, float z0,
                            float x1, float y1, float z1, float red, float green, float blue) {
        face(buffer, view, shade(sun, -1.0F, 0.0F, 0.0F, 0.0F), red, green, blue, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(buffer, view, shade(sun, 1.0F, 0.0F, 0.0F, 0.0F), red, green, blue, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        face(buffer, view, shade(sun, 0.0F, -1.0F, 0.0F, 0.0F), red, green, blue, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(buffer, view, shade(sun, 0.0F, 1.0F, 0.0F, 0.0F), red, green, blue, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        face(buffer, view, shade(sun, 0.0F, 0.0F, -1.0F, 0.0F), red, green, blue, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        face(buffer, view, shade(sun, 0.0F, 0.0F, 1.0F, 0.0F), red, green, blue, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
    }

    static void face(BufferBuilder buffer, Matrix4f view, float light, float red, float green, float blue,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float r = red * light;
        float g = green * light;
        float b = blue * light;
        buffer.vertex(view, ax, ay, az).color(r, g, b, 1.0F).endVertex();
        buffer.vertex(view, bx, by, bz).color(r, g, b, 1.0F).endVertex();
        buffer.vertex(view, cx, cy, cz).color(r, g, b, 1.0F).endVertex();
        buffer.vertex(view, dx, dy, dz).color(r, g, b, 1.0F).endVertex();
    }

    /** A camera-facing disc, bright in the middle and fading to nothing at the edge. Needs additive blending. */
    static void glow(BufferBuilder buffer, Matrix4f view, Vector3f right, Vector3f up, float x, float y, float z,
                             float radius, float red, float green, float blue, float alpha) {
        if (alpha <= 0.0F || radius <= 0.0F) {
            return;
        }
        int steps = 20;
        for (int i = 0; i < steps; i++) {
            float a0 = Mth.TWO_PI * i / steps;
            float a1 = Mth.TWO_PI * (i + 1) / steps;
            float c0 = Mth.cos(a0) * radius;
            float s0 = Mth.sin(a0) * radius;
            float c1 = Mth.cos(a1) * radius;
            float s1 = Mth.sin(a1) * radius;
            buffer.vertex(view, x, y, z).color(red, green, blue, alpha).endVertex();
            buffer.vertex(view, x, y, z).color(red, green, blue, alpha).endVertex();
            buffer.vertex(view, x + right.x * c0 + up.x * s0, y + right.y * c0 + up.y * s0, z + right.z * c0 + up.z * s0)
                    .color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(view, x + right.x * c1 + up.x * s1, y + right.y * c1 + up.y * s1, z + right.z * c1 + up.z * s1)
                    .color(red, green, blue, 0.0F).endVertex();
        }
    }

    /** A streak of light between two points, turned to face the camera. Needs additive blending. */
    static void beam(BufferBuilder buffer, Matrix4f view, Vector3f eye, Vector3f from, Vector3f to, float width,
                             float red, float green, float blue, float alpha) {
        Vector3f axis = new Vector3f(to).sub(from);
        Vector3f side = new Vector3f(eye).sub(from).cross(axis);
        if (side.lengthSquared() < 1.0e-6F) {
            side.set(1.0F, 0.0F, 0.0F);
        }
        side.normalize().mul(width);
        for (int sign = -1; sign <= 1; sign += 2) {
            float sx = side.x * sign;
            float sy = side.y * sign;
            float sz = side.z * sign;
            buffer.vertex(view, from.x, from.y, from.z).color(red, green, blue, alpha).endVertex();
            buffer.vertex(view, from.x + sx, from.y + sy, from.z + sz).color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(view, to.x + sx, to.y + sy, to.z + sz).color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(view, to.x, to.y, to.z).color(red, green, blue, alpha).endVertex();
        }
    }

    static Vector3f mix(Vector3f from, Vector3f to, float amount) {
        return new Vector3f(from).lerp(to, amount);
    }

    static float ease(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    // ---------------------------------------------------------------- generated content

    static void build() {
        if (stars != null) {
            return;
        }
        Random random = new Random(20261001L);
        float[] generated = new float[STAR_COUNT * 6];
        for (int i = 0; i < STAR_COUNT; i++) {
            // Uniform on the sphere.
            double y = random.nextDouble() * 2.0 - 1.0;
            double azimuth = random.nextDouble() * Math.PI * 2.0;
            double ring = Math.sqrt(1.0 - y * y);
            int o = i * 6;
            generated[o] = (float) (ring * Math.cos(azimuth));
            generated[o + 1] = (float) y;
            generated[o + 2] = (float) (ring * Math.sin(azimuth));
            float magnitude = random.nextFloat();
            generated[o + 3] = 0.8F + 2.2F * magnitude * magnitude * magnitude;
            generated[o + 4] = 0.35F + 0.65F * random.nextFloat();
            generated[o + 5] = random.nextFloat();
        }
        sparks = new float[SPARK_COUNT * 3];
        for (int i = 0; i < SPARK_COUNT; i++) {
            double y = random.nextDouble() * 2.0 - 1.0;
            double azimuth = random.nextDouble() * Math.PI * 2.0;
            double ring = Math.sqrt(1.0 - y * y);
            sparks[i * 3] = (float) (ring * Math.cos(azimuth));
            sparks[i * 3 + 1] = (float) y;
            sparks[i * 3 + 2] = (float) (ring * Math.sin(azimuth));
        }
        jupiter = new SphereMesh(96, 96, SpaceScene::jupiterSurface);
        saturn = new SphereMesh(72, 72, SpaceScene::saturnSurface);
        earth = new SphereMesh(96, 160, SpaceScene::earthSurface);
        moon = new SphereMesh(16, 24, SpaceScene::moonSurface);
        stars = generated;
    }

    private static void jupiterSurface(float x, float y, float z, float[] out) {
        float turbulence = fbm(x * 3.0F, y * 9.0F, z * 3.0F, 3) - 0.5F;
        float band = smooth(0.5F + 0.5F * Mth.sin(y * 16.0F + turbulence * 3.0F));
        float fine = 0.25F * (0.5F + 0.5F * Mth.sin(y * 37.0F + turbulence * 5.0F));
        float red = Mth.lerp(fine, Mth.lerp(band, 0.90F, 0.62F), 0.80F);
        float green = Mth.lerp(fine, Mth.lerp(band, 0.82F, 0.38F), 0.60F);
        float blue = Mth.lerp(fine, Mth.lerp(band, 0.68F, 0.24F), 0.42F);
        float pole = Mth.clamp((Math.abs(y) - 0.78F) / 0.2F, 0.0F, 1.0F);
        red = Mth.lerp(pole, red, 0.46F);
        green = Mth.lerp(pole, green, 0.43F);
        blue = Mth.lerp(pole, blue, 0.43F);
        // The Great Red Spot.
        float longitude = (float) Math.atan2(z, x);
        float dLat = (y + 0.36F) / 0.10F;
        float dLon = Mth.wrapDegrees((longitude - 0.9F) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD / 0.28F;
        float spot = (float) Math.exp(-(dLat * dLat + dLon * dLon));
        out[0] = Mth.lerp(spot, red, 0.76F);
        out[1] = Mth.lerp(spot, green, 0.27F);
        out[2] = Mth.lerp(spot, blue, 0.16F);
    }

    private static void saturnSurface(float x, float y, float z, float[] out) {
        float turbulence = fbm(x * 2.0F, y * 6.0F, z * 2.0F, 2) - 0.5F;
        float band = 0.5F + 0.5F * Mth.sin(y * 11.0F + turbulence);
        float pole = Mth.clamp((Math.abs(y) - 0.8F) / 0.2F, 0.0F, 1.0F);
        out[0] = Mth.lerp(pole, Mth.lerp(band, 0.90F, 0.76F), 0.62F);
        out[1] = Mth.lerp(pole, Mth.lerp(band, 0.82F, 0.64F), 0.60F);
        out[2] = Mth.lerp(pole, Mth.lerp(band, 0.60F, 0.42F), 0.50F);
    }

    private static void earthSurface(float x, float y, float z, float[] out) {
        float height = fbm(x * 1.9F + 7.0F, y * 1.9F + 3.0F, z * 1.9F + 11.0F, 5);
        float red;
        float green;
        float blue;
        if (height > 0.53F) {
            float rise = Mth.clamp((height - 0.53F) / 0.2F, 0.0F, 1.0F);
            red = Mth.lerp(rise, 0.16F, 0.48F);
            green = Mth.lerp(rise, 0.42F, 0.40F);
            blue = Mth.lerp(rise, 0.14F, 0.24F);
        } else {
            float shallow = Mth.clamp((height - 0.35F) / 0.18F, 0.0F, 1.0F);
            red = Mth.lerp(shallow, 0.03F, 0.08F);
            green = Mth.lerp(shallow, 0.10F, 0.30F);
            blue = Mth.lerp(shallow, 0.32F, 0.55F);
        }
        float ice = Mth.clamp((Math.abs(y) - 0.84F) / 0.06F, 0.0F, 1.0F);
        float cloud = 0.85F * smooth(Mth.clamp((fbm(x * 3.1F + 40.0F, y * 4.5F + 17.0F, z * 3.1F + 5.0F, 4) - 0.55F) / 0.17F, 0.0F, 1.0F));
        float white = Math.max(ice, cloud);
        out[0] = Mth.lerp(white, red, 0.95F);
        out[1] = Mth.lerp(white, green, 0.96F);
        out[2] = Mth.lerp(white, blue, 0.98F);
    }

    private static void moonSurface(float x, float y, float z, float[] out) {
        float tone = 0.45F + 0.35F * fbm(x * 4.0F, y * 4.0F, z * 4.0F, 3);
        out[0] = tone;
        out[1] = tone * 0.97F;
        out[2] = tone * 0.92F;
    }

    static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float lattice(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Smooth value noise in 0..1. */
    static float noise(float x, float y, float z) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        int iz = Mth.floor(z);
        float fx = smooth(x - ix);
        float fy = smooth(y - iy);
        float fz = smooth(z - iz);
        float x00 = Mth.lerp(fx, lattice(ix, iy, iz), lattice(ix + 1, iy, iz));
        float x10 = Mth.lerp(fx, lattice(ix, iy + 1, iz), lattice(ix + 1, iy + 1, iz));
        float x01 = Mth.lerp(fx, lattice(ix, iy, iz + 1), lattice(ix + 1, iy, iz + 1));
        float x11 = Mth.lerp(fx, lattice(ix, iy + 1, iz + 1), lattice(ix + 1, iy + 1, iz + 1));
        return Mth.lerp(fz, Mth.lerp(fy, x00, x10), Mth.lerp(fy, x01, x11));
    }

    static float fbm(float x, float y, float z, int octaves) {
        float sum = 0.0F;
        float weight = 0.5F;
        float total = 0.0F;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x, y, z) * weight;
            total += weight;
            weight *= 0.5F;
            x *= 2.03F;
            y *= 2.03F;
            z *= 2.03F;
        }
        return sum / total;
    }
}
