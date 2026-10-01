package moe.dexx.tacticaltablet.client.cinematic;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A latitude/longitude sphere whose surface colours are computed once, then lit and drawn every frame. */
final class SphereMesh {
    @FunctionalInterface
    interface Surface {
        /** Writes the RGB colour of the surface point with the given unit normal into out. */
        void color(float x, float y, float z, float[] out);
    }

    private final int rings;
    private final int sectors;
    private final float[] normals;
    private final float[] colors;
    private final float[] world;
    private final float[] light;

    SphereMesh(int rings, int sectors, Surface surface) {
        this.rings = rings;
        this.sectors = sectors;
        int count = (rings + 1) * (sectors + 1);
        normals = new float[count * 3];
        colors = new float[count * 3];
        world = new float[count * 3];
        light = new float[count];
        float[] rgb = new float[3];
        int index = 0;
        for (int ring = 0; ring <= rings; ring++) {
            double polar = Math.PI * ring / rings;
            float y = (float) Math.cos(polar);
            float ringRadius = (float) Math.sin(polar);
            for (int sector = 0; sector <= sectors; sector++) {
                double azimuth = Math.PI * 2.0 * sector / sectors;
                float x = ringRadius * (float) Math.cos(azimuth);
                float z = ringRadius * (float) Math.sin(azimuth);
                normals[index] = x;
                normals[index + 1] = y;
                normals[index + 2] = z;
                surface.color(x, y, z, rgb);
                colors[index] = rgb[0];
                colors[index + 1] = rgb[1];
                colors[index + 2] = rgb[2];
                index += 3;
            }
        }
    }

    private void orient(Matrix3f rotation) {
        Vector3f normal = new Vector3f();
        for (int i = 0; i < normals.length; i += 3) {
            normal.set(normals[i], normals[i + 1], normals[i + 2]);
            rotation.transform(normal);
            world[i] = normal.x;
            world[i + 1] = normal.y;
            world[i + 2] = normal.z;
        }
    }

    /** Draws the lit sphere. The buffer must be collecting POSITION_COLOR quads. */
    void draw(BufferBuilder buffer, Matrix4f view, Vector3f center, float radius, Matrix3f rotation, Vector3f sun, float ambient) {
        orient(rotation);
        for (int i = 0, v = 0; i < world.length; i += 3, v++) {
            float facing = world[i] * sun.x + world[i + 1] * sun.y + world[i + 2] * sun.z;
            // A soft terminator instead of a hard day/night edge.
            float lit = Math.min(1.0F, Math.max(0.0F, facing * 2.5F + 0.25F));
            light[v] = ambient + (1.0F - ambient) * lit;
        }
        emit(buffer, view, center, radius, false, null, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /** Draws a glowing shell that is brightest at the limb, as an atmosphere. Needs additive blending. */
    void drawRim(BufferBuilder buffer, Matrix4f view, Vector3f eye, Vector3f center, float radius, Matrix3f rotation,
                 float red, float green, float blue, float strength) {
        orient(rotation);
        emit(buffer, view, center, radius, true, eye, red, green, blue, strength);
    }

    private void emit(BufferBuilder buffer, Matrix4f view, Vector3f center, float radius, boolean rim, Vector3f eye,
                      float red, float green, float blue, float strength) {
        int stride = sectors + 1;
        for (int ring = 0; ring < rings; ring++) {
            for (int sector = 0; sector < sectors; sector++) {
                int a = ring * stride + sector;
                int b = a + 1;
                int c = a + stride + 1;
                int d = a + stride;
                if (rim) {
                    rimVertex(buffer, view, eye, center, radius, a, red, green, blue, strength);
                    rimVertex(buffer, view, eye, center, radius, b, red, green, blue, strength);
                    rimVertex(buffer, view, eye, center, radius, c, red, green, blue, strength);
                    rimVertex(buffer, view, eye, center, radius, d, red, green, blue, strength);
                } else {
                    vertex(buffer, view, center, radius, a);
                    vertex(buffer, view, center, radius, b);
                    vertex(buffer, view, center, radius, c);
                    vertex(buffer, view, center, radius, d);
                }
            }
        }
    }

    private void vertex(BufferBuilder buffer, Matrix4f view, Vector3f center, float radius, int v) {
        int i = v * 3;
        float shade = light[v];
        buffer.vertex(view, center.x + world[i] * radius, center.y + world[i + 1] * radius, center.z + world[i + 2] * radius)
                .color(colors[i] * shade, colors[i + 1] * shade, colors[i + 2] * shade, 1.0F).endVertex();
    }

    private void rimVertex(BufferBuilder buffer, Matrix4f view, Vector3f eye, Vector3f center, float radius, int v,
                           float red, float green, float blue, float strength) {
        int i = v * 3;
        float x = center.x + world[i] * radius;
        float y = center.y + world[i + 1] * radius;
        float z = center.z + world[i + 2] * radius;
        float dx = eye.x - x;
        float dy = eye.y - y;
        float dz = eye.z - z;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float facing = length < 1.0e-4F ? 1.0F : Math.abs((world[i] * dx + world[i + 1] * dy + world[i + 2] * dz) / length);
        float edge = 1.0F - facing;
        float alpha = Math.min(1.0F, edge * edge * edge * strength);
        buffer.vertex(view, x, y, z).color(red, green, blue, alpha).endVertex();
    }
}
