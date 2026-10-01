package moe.dexx.tacticaltablet.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.client.target.HeldTablet;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the boundary of the strike zone in the world: a translucent wall along the circle of the chosen radius and
 * a marker column at the target. Shown for the target on the held tablet and for the player's own pending strike.
 */
public final class ZoneRing {
    private static final float WALL_BELOW = 12.0F;
    private static final float WALL_ABOVE = 28.0F;
    private static final float MARKER_HEIGHT = 96.0F;

    private ZoneRing() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        StrikeState own = ClientStrikes.own();
        if (own != null && (own.phase() == StrikePhase.COUNTDOWN || own.phase() == StrikePhase.CHARGE || own.phase() == StrikePhase.TRAVEL)) {
            // A pending strike pulses, faster as the shot approaches.
            double speed = own.phase() == StrikePhase.COUNTDOWN ? 300.0 : 120.0;
            float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / speed);
            draw(context, own.target().getX(), own.target().getY(), own.target().getZ(), own.radius(), 1.0F, 0.35F, 0.2F, pulse);
            return;
        }
        if (own != null) {
            // The strike is landing: the beams and the crater mark the zone now.
            return;
        }
        StrikeParams params = HeldTablet.params(minecraft.player);
        if (params != null && params.hasTarget()) {
            draw(context, params.targetX(), params.targetY(), params.targetZ(), params.radius(), 0.2F, 0.82F, 1.0F, 0.8F);
        }
    }

    private static void draw(WorldRenderContext context, int targetX, int targetY, int targetZ, int radius,
                             float red, float green, float blue, float strength) {
        Vec3 camera = context.camera().getPosition();
        PoseStack pose = context.matrixStack();
        pose.pushPose();
        // Work relative to the target so float precision holds far from the world origin.
        pose.translate(targetX + 0.5 - camera.x, targetY - camera.y, targetZ + 0.5 - camera.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float r = radius + 0.5F;
        int segments = Mth.clamp(radius * 2, 24, 192);
        float wallAlpha = 0.28F * strength;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * r;
            float z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r;
            float z1 = (float) Math.sin(a1) * r;
            // Opaque at the target's height, fading out above and below.
            buffer.vertex(matrix, x0, 0.0F, z0).color(red, green, blue, wallAlpha).endVertex();
            buffer.vertex(matrix, x1, 0.0F, z1).color(red, green, blue, wallAlpha).endVertex();
            buffer.vertex(matrix, x1, WALL_ABOVE, z1).color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(matrix, x0, WALL_ABOVE, z0).color(red, green, blue, 0.0F).endVertex();

            buffer.vertex(matrix, x0, 0.0F, z0).color(red, green, blue, wallAlpha).endVertex();
            buffer.vertex(matrix, x1, 0.0F, z1).color(red, green, blue, wallAlpha).endVertex();
            buffer.vertex(matrix, x1, -WALL_BELOW, z1).color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(matrix, x0, -WALL_BELOW, z0).color(red, green, blue, 0.0F).endVertex();
        }

        // Marker: two crossed vertical planes at the target.
        float half = 0.35F;
        float markerAlpha = 0.6F * strength;
        buffer.vertex(matrix, -half, 0.0F, 0.0F).color(red, green, blue, markerAlpha).endVertex();
        buffer.vertex(matrix, half, 0.0F, 0.0F).color(red, green, blue, markerAlpha).endVertex();
        buffer.vertex(matrix, half, MARKER_HEIGHT, 0.0F).color(red, green, blue, 0.0F).endVertex();
        buffer.vertex(matrix, -half, MARKER_HEIGHT, 0.0F).color(red, green, blue, 0.0F).endVertex();
        buffer.vertex(matrix, 0.0F, 0.0F, -half).color(red, green, blue, markerAlpha).endVertex();
        buffer.vertex(matrix, 0.0F, 0.0F, half).color(red, green, blue, markerAlpha).endVertex();
        buffer.vertex(matrix, 0.0F, MARKER_HEIGHT, half).color(red, green, blue, 0.0F).endVertex();
        buffer.vertex(matrix, 0.0F, MARKER_HEIGHT, -half).color(red, green, blue, 0.0F).endVertex();

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
