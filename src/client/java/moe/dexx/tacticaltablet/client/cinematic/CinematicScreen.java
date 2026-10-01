package moe.dexx.tacticaltablet.client.cinematic;

import moe.dexx.tacticaltablet.client.gui.TabletStyle;
import moe.dexx.tacticaltablet.client.net.ClientNetworking;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.CinematicTimeline;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Open for as long as the cinematic runs: draws the space shots, the fades and the letterbox, and takes the keys
 * that skip the cinematic or cancel the launch.
 */
public final class CinematicScreen extends Screen {
    public CinematicScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        // Also reached when something else replaces this screen (death, disconnect): the camera must be given back.
        Cinematic.screenRemoved();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Cinematic.skip();
            return true;
        }
        StrikeState state = Cinematic.state();
        if (keyCode == GLFW.GLFW_KEY_C && state != null && state.phase() == StrikePhase.CHARGE) {
            ClientNetworking.sendCancel(false);
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        StrikeState state = Cinematic.state();
        if (state == null) {
            return;
        }
        double seconds = Cinematic.seconds(partialTick);
        int salvos = state.salvos();
        Segment segment = CinematicTimeline.segment(seconds, salvos);
        double progress = CinematicTimeline.progress(seconds, salvos);

        StrikeType type = state.type() == StrikeType.VISUAL_ONLY ? StrikeType.ORBITAL_LASER : state.type();
        if (segment.inSpace()) {
            SpaceScene.render(graphics, type, segment, progress, seconds);
        }
        int accent = CinematicHud.accent(type);
        CinematicHud.warp(graphics, CinematicHud.warpAmount(type, segment, (float) progress), accent, seconds);
        int bar = height / 10;
        CinematicHud.render(graphics, font, state, segment, (float) progress, seconds, bar + 10, height - bar - 14);
        if (type != StrikeType.ORBITAL_LASER) {
            // The picture breaks up as the projectile burns into the atmosphere and again at the moment of impact.
            if (segment == Segment.DESCENT && progress > 0.82) {
                CinematicHud.glitch(graphics, (float) ((progress - 0.82) / 0.18), 0xFF5A1F, seconds);
            } else if (segment == Segment.IMPACT && seconds - CinematicTimeline.DESCENT_END < 0.8) {
                CinematicHud.glitch(graphics, (float) (1.0 - (seconds - CinematicTimeline.DESCENT_END) / 0.8), 0x4FA8FF, seconds);
            }
        }
        int fade = Cinematic.fade(seconds, salvos, state.type());
        if (fade >>> 24 != 0) {
            graphics.fill(0, 0, width, height, fade);
        }

        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);

        Component caption = caption(type, segment, progress);
        if (caption != null) {
            graphics.drawCenteredString(font, caption, width / 2, height - bar + (bar - 8) / 2, accent);
        }
        Component hint = Component.translatable("tactical_tablet.cinematic.hint.skip");
        if (state.phase() == StrikePhase.CHARGE) {
            hint = hint.copy().append("    ").append(Component.translatable("tactical_tablet.cinematic.hint.cancel"));
        }
        graphics.drawString(font, hint, width - font.width(hint) - 8, (bar - 8) / 2, TabletStyle.TEXT_DIM, false);
    }

    private static Component caption(StrikeType type, Segment segment, double progress) {
        String key = switch (segment) {
            case PULL_AWAY, ASCENT -> "ascent";
            case JUPITER -> "jupiter";
            case SATURN -> "saturn";
            case CANNON -> "cannon";
            case FIRE -> "fire";
            case DESCENT -> "descent";
            case IMPACT -> "impact";
            case RETURN, OVER -> null;
        };
        if (key == null) {
            return null;
        }
        return Component.translatable("tactical_tablet.cinematic." + type.name().toLowerCase(Locale.ROOT) + "." + key,
                (int) (progress * 100.0));
    }
}
