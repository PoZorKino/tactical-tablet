package moe.dexx.tacticaltablet.client.cinematic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Locale;
import java.util.Random;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The flight-computer overlay of the cinematic: bracketed status lines, counters, speed bars, warp streaks and the
 * glitch when the projectile hits the atmosphere. The readouts are part of the fiction and stay in English.
 */
final class CinematicHud {
    private CinematicHud() {
    }

    static int accent(StrikeType type) {
        return switch (type) {
            case KINETIC -> 0xFFFF6A2A;
            case METEOR -> 0xFFFFB347;
            default -> 0xFF4FD8FF;
        };
    }

    /** How strongly the star streaks of hyperspace travel show in this shot, 0 to 1. */
    static float warpAmount(StrikeType type, Segment segment, float p) {
        return switch (segment) {
            case ASCENT -> Math.max(0.0F, (p - 0.55F) * 2.2F);
            case JUPITER -> type == StrikeType.METEOR ? 0.35F : Math.max(0.0F, 1.0F - p * 1.6F);
            case FIRE -> type == StrikeType.KINETIC ? 0.8F * (1.0F - p * 0.4F) : 0.0F;
            case DESCENT -> switch (type) {
                case KINETIC -> p < 0.45F ? 0.85F : 0.85F * (1.0F - (p - 0.45F) * 1.2F);
                case METEOR -> 0.35F * (1.0F - p);
                default -> 0.0F;
            };
            default -> 0.0F;
        };
    }

    static void warp(GuiGraphics graphics, float amount, int color, double seconds) {
        if (amount <= 0.01F) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        float reach = (float) Math.sqrt(cx * cx + cy * cy);
        Random random = new Random(99L);
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        float red = (color >> 16 & 0xFF) / 255.0F * 0.5F + 0.5F;
        float green = (color >> 8 & 0xFF) / 255.0F * 0.5F + 0.5F;
        float blue = (color & 0xFF) / 255.0F * 0.5F + 0.5F;
        for (int i = 0; i < 170; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float speed = 0.35F + random.nextFloat() * 0.9F;
            float phase = (float) ((random.nextFloat() + seconds * speed * (0.5F + amount)) % 1.0);
            float near = phase * phase * reach;
            float tail = near * (0.04F + 0.28F * amount * phase);
            float far = near + tail;
            float dx = Mth.cos(angle);
            float dy = Mth.sin(angle);
            float half = 0.5F + phase;
            float alpha = amount * Math.min(1.0F, phase * 3.0F) * (1.0F - phase * 0.3F);
            float x0 = cx + dx * near;
            float y0 = cy + dy * near;
            float x1 = cx + dx * far;
            float y1 = cy + dy * far;
            buffer.vertex(matrix, x0 - dy * half, y0 + dx * half, 0.0F).color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(matrix, x0 + dy * half, y0 - dx * half, 0.0F).color(red, green, blue, 0.0F).endVertex();
            buffer.vertex(matrix, x1 + dy * half, y1 - dx * half, 0.0F).color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, x1 - dy * half, y1 + dx * half, 0.0F).color(red, green, blue, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Torn horizontal bands, as when the picture breaks up in the heat of re-entry or at the moment of impact. */
    static void glitch(GuiGraphics graphics, float amount, int color, double seconds) {
        if (amount <= 0.01F) {
            return;
        }
        Random random = new Random((long) (seconds * 24.0) * 7919L);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int bands = (int) (4 + 22 * amount);
        for (int i = 0; i < bands; i++) {
            int y = random.nextInt(height);
            int h = 1 + random.nextInt(Math.max(2, (int) (14 * amount)));
            int x = random.nextInt(width);
            int w = (int) (width * (0.1F + random.nextFloat() * 0.7F));
            int alpha = (int) (Mth.clamp(amount, 0.0F, 1.0F) * (80 + random.nextInt(160)));
            int pick = random.nextInt(4);
            int rgb = pick == 0 ? 0x000000 : pick == 1 ? 0xFFFFFF : pick == 2 ? color & 0xFFFFFF : 0x1A6CFF;
            graphics.fill(Math.max(0, x - w / 2), y, Math.min(width, x + w / 2), Math.min(height, y + h), alpha << 24 | rgb);
        }
    }

    static void render(GuiGraphics graphics, Font font, StrikeState state, Segment segment, float p, double seconds, int topY, int bottomY) {
        StrikeType type = state.type() == StrikeType.VISUAL_ONLY ? StrikeType.ORBITAL_LASER : state.type();
        int accent = accent(type);
        int width = graphics.guiWidth();
        String top = null;
        String line1 = null;
        String line2 = null;
        float bar = -1.0F;
        String title = null;
        String subtitle = null;

        switch (segment) {
            case PULL_AWAY -> {
                top = "[ TARGET LOCKED ]";
                line1 = name(type) + " · AUTHORISED";
                line2 = String.format(Locale.ROOT, "X %d  Y %d  Z %d  ·  R %d", state.target().getX(), state.target().getY(), state.target().getZ(), state.radius());
            }
            case ASCENT -> {
                top = "[ LEAVING ATMOSPHERE ]";
                line1 = String.format(Locale.ROOT, "ALTITUDE %,d KM", (int) (p * p * 420.0F));
            }
            case JUPITER -> {
                switch (type) {
                    case KINETIC -> {
                        top = "[ RELAY · JUPITER 5.2 AU ]";
                        title = "KINETIC ROD";
                        subtitle = "TUNGSTEN CORE · 9,800 KG · ORBITAL ACCELERATOR";
                    }
                    case METEOR -> {
                        top = "[ BELT TRANSIT ]";
                        title = "METEOR";
                        subtitle = "MAIN BELT · 2,114,907 CATALOGUED BODIES";
                    }
                    default -> {
                        top = "[ RELAY · JUPITER 5.2 AU ]";
                        line1 = "JUPITER · RELAY STATION";
                    }
                }
            }
            case SATURN -> {
                switch (type) {
                    case KINETIC -> {
                        top = "[ ACCELERATOR WARMING ]";
                        line1 = String.format(Locale.ROOT, "COILS %,d / 670,000", (int) (670_000 * p));
                    }
                    case METEOR -> {
                        top = "[ MAGNET ONLINE ]";
                        line1 = String.format(Locale.ROOT, "FIELD %.1f T", MeteorShots.fieldTesla(segment, p));
                        line2 = "TARGET BODY · 4.1 MILLION TONNES";
                    }
                    default -> {
                        top = "[ RELAY · SATURN 9.5 AU ]";
                        line1 = "SATURN · RELAY STATION";
                    }
                }
            }
            case CANNON -> {
                switch (type) {
                    case KINETIC -> {
                        top = String.format(Locale.ROOT, "[ LAP %d / %d ]", KineticShots.lap(p), KineticShots.LAPS);
                        line1 = String.format(Locale.ROOT, "VELOCITY %.4f c", KineticShots.velocity(p));
                        bar = KineticShots.velocity(p);
                    }
                    case METEOR -> {
                        top = "[ CAPTURE ]";
                        line1 = String.format(Locale.ROOT, "FIELD %.1f T", MeteorShots.fieldTesla(segment, p));
                        line2 = String.format(Locale.ROOT, "SEPARATION %,d M", (int) (180.0F * (1.0F - p)));
                        bar = p;
                    }
                    default -> {
                        top = "[ CAPACITORS CHARGING ]";
                        line1 = String.format(Locale.ROOT, "CHARGE %d%%", (int) (p * 100.0F));
                        bar = p;
                    }
                }
            }
            case FIRE -> top = type == StrikeType.ORBITAL_LASER ? "[ SALVO AWAY ]" : "[ RELEASE ]";
            case DESCENT -> {
                int range = (int) (665_268_260L * Math.pow(1.0F - p, 2.4F));
                switch (type) {
                    case KINETIC -> top = p < 0.45F ? "[ DEBRIS FIELD · MAIN BELT ]" : "[ TERMINAL BOLT ]";
                    case METEOR -> top = p < 0.45F ? "[ TRAJECTORY LOCKED ]" : "[ TERMINAL ENTRY ]";
                    default -> top = "[ BEAM INBOUND ]";
                }
                line1 = String.format(Locale.ROOT, "RANGE %,d KM", range);
                if (type != StrikeType.ORBITAL_LASER) {
                    line2 = String.format(Locale.ROOT, "VELOCITY %.4f c", 0.78F + 0.05F * p);
                }
            }
            case IMPACT -> {
                top = "[ IMPACT CONFIRMED ]";
                line1 = String.format(Locale.ROOT, "ZONE %04d FLAGGED · %s · %d M", state.radius(), standing(type), 384);
            }
            default -> {
            }
        }

        if (top != null) {
            text(graphics, font, top, width / 2, topY, accent, 1.0F, true);
        }
        if (title != null) {
            // Outlined title in the upper third.
            int ty = topY + 28;
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                text(graphics, font, title, width / 2 + d[0], ty + d[1], 0xFF000000, 2.6F, true);
            }
            text(graphics, font, title, width / 2, ty, accent, 2.6F, true);
            if (subtitle != null) {
                text(graphics, font, subtitle, width / 2, ty + 26, 0xFFE8E8E8, 0.8F, true);
            }
        }
        int y = bottomY;
        if (line2 != null) {
            text(graphics, font, line2, width / 2, y, 0xFFB0B8C0, 0.8F, true);
            y -= 12;
        }
        if (bar >= 0.0F) {
            speedBar(graphics, width / 2, y - 8, Math.min(1.0F, bar), accent);
            y -= 16;
        }
        if (line1 != null) {
            text(graphics, font, line1, width / 2, y, 0xFFFFFFFF, 0.9F, true);
        }
    }

    private static String name(StrikeType type) {
        return switch (type) {
            case KINETIC -> "KINETIC ROD";
            case METEOR -> "METEOR STRIKE";
            default -> "ORBITAL LASER";
        };
    }

    private static String standing(StrikeType type) {
        return switch (type) {
            case KINETIC -> "ROD STANDING";
            case METEOR -> "EJECTA SETTLING";
            default -> "BEAM SPENT";
        };
    }

    private static void speedBar(GuiGraphics graphics, int centerX, int y, float fraction, int color) {
        int length = 120;
        int x = centerX - length / 2;
        graphics.fill(x, y, x + length, y + 1, 0x55FFFFFF);
        graphics.fill(x, y - 1, x + (int) (length * fraction), y + 2, color);
        for (int i = 0; i < 6; i++) {
            int tick = x + length * i / 5 - (i == 5 ? 1 : 0);
            graphics.fill(tick, y + 4, tick + 1, y + 6, i / 5.0F <= fraction ? color : 0x66FFFFFF);
        }
    }

    static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color, float scale, boolean centered) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, centered ? -font.width(text) / 2 : 0, 0, color, false);
        graphics.pose().popPose();
    }
}
