package moe.dexx.tacticaltablet.client.hud;

import java.util.Locale;
import moe.dexx.tacticaltablet.client.gui.TabletScreen;
import moe.dexx.tacticaltablet.client.gui.TabletStyle;
import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.client.target.AimMode;
import moe.dexx.tacticaltablet.net.StrikeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** In-world overlay: the aim reticle, the status of the player's own strike and short notices. */
public final class TabletHud {
    private TabletHud() {
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Font font = minecraft.font;

        if (AimMode.isActive()) {
            renderAim(graphics, minecraft, font, width, height, partialTick);
        }
        if (minecraft.screen instanceof TabletScreen) {
            return;
        }

        int y = 8;
        StrikeState own = ClientStrikes.own();
        if (own != null) {
            Component line = strikeLine(own);
            int lineWidth = font.width(line);
            int x = (width - lineWidth) / 2;
            graphics.fill(x - 6, y - 3, x + lineWidth + 6, y + 11, 0xB0060C14);
            graphics.renderOutline(x - 6, y - 3, lineWidth + 12, 14, TabletStyle.ACCENT_DIM);
            graphics.drawString(font, line, x, y, TabletStyle.WARNING, false);
            y += 18;
        }
        Component notice = ClientStrikes.notice();
        if (notice != null) {
            int noticeWidth = font.width(notice);
            int x = (width - noticeWidth) / 2;
            graphics.fill(x - 4, y - 2, x + noticeWidth + 4, y + 10, 0x90060C14);
            graphics.drawString(font, notice, x, y, ClientStrikes.noticeIsError() ? TabletStyle.ERROR : TabletStyle.OK, false);
        }
    }

    private static Component strikeLine(StrikeState own) {
        String seconds = String.format(Locale.ROOT, "%.1f", ClientStrikes.ticksLeft(own) / 20.0);
        return switch (own.phase()) {
            case COUNTDOWN -> Component.translatable("tactical_tablet.hud.phase.countdown", seconds);
            case CHARGE -> Component.translatable("tactical_tablet.hud.phase.charge", seconds);
            case TRAVEL -> Component.translatable("tactical_tablet.hud.phase.travel");
            case IMPACT -> Component.translatable("tactical_tablet.hud.phase.impact");
            default -> Component.translatable("tactical_tablet.hud.phase.destroying", ClientStrikes.progressPercent(own),
                    ClientStrikes.progressDone(own), ClientStrikes.progressTotal(own));
        };
    }

    private static void renderAim(GuiGraphics graphics, Minecraft minecraft, Font font, int width, int height, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        int size = 12;
        int arm = 5;
        int color = TabletStyle.ACCENT;
        // Four corner brackets around the vanilla crosshair.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * size;
                int y = cy + sy * size;
                graphics.fill(Math.min(x, x - sx * arm), y, Math.max(x, x - sx * arm) + 1, y + 1, color);
                graphics.fill(x, Math.min(y, y - sy * arm), x + 1, Math.max(y, y - sy * arm) + 1, color);
            }
        }
        BlockPos aimed = AimMode.aimedBlock(minecraft, partialTick);
        Component line;
        int lineColor;
        if (aimed == null) {
            line = Component.translatable("tactical_tablet.hud.aim.none");
            lineColor = TabletStyle.WARNING;
        } else {
            int distance = (int) Math.sqrt(aimed.distToCenterSqr(minecraft.player.getEyePosition(partialTick)));
            line = Component.translatable("tactical_tablet.hud.aim.target", aimed.getX(), aimed.getY(), aimed.getZ(), distance);
            lineColor = TabletStyle.ACCENT;
        }
        graphics.drawCenteredString(font, line, cx, cy + size + 8, lineColor);
        graphics.drawCenteredString(font, Component.translatable("tactical_tablet.hud.aim.hint"), cx, cy + size + 20, TabletStyle.TEXT_DIM);
    }
}
