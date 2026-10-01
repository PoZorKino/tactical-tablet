package moe.dexx.tacticaltablet.client.cinematic;

import moe.dexx.tacticaltablet.client.gui.TabletStyle;
import moe.dexx.tacticaltablet.client.net.ClientNetworking;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.CinematicTimeline;
import moe.dexx.tacticaltablet.strike.CinematicTimeline.Segment;
import moe.dexx.tacticaltablet.strike.StrikePhase;
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

        if (segment.inSpace()) {
            SpaceScene.render(graphics, segment, progress, seconds);
        }
        int fade = Cinematic.fade(seconds, salvos);
        if (fade >>> 24 != 0) {
            graphics.fill(0, 0, width, height, fade);
        }

        int bar = height / 10;
        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);

        Component caption = caption(segment, progress);
        if (caption != null) {
            graphics.drawCenteredString(font, caption, width / 2, height - bar + (bar - 8) / 2, TabletStyle.ACCENT);
        }
        Component hint = Component.translatable("tactical_tablet.cinematic.hint.skip");
        if (state.phase() == StrikePhase.CHARGE) {
            hint = hint.copy().append("    ").append(Component.translatable("tactical_tablet.cinematic.hint.cancel"));
        }
        graphics.drawString(font, hint, width - font.width(hint) - 8, (bar - 8) / 2, TabletStyle.TEXT_DIM, false);
    }

    private static Component caption(Segment segment, double progress) {
        return switch (segment) {
            case PULL_AWAY, ASCENT -> Component.translatable("tactical_tablet.cinematic.ascent");
            case JUPITER -> Component.translatable("tactical_tablet.cinematic.jupiter");
            case SATURN -> Component.translatable("tactical_tablet.cinematic.saturn");
            case CANNON -> Component.translatable("tactical_tablet.cinematic.cannon", (int) (progress * 100.0));
            case FIRE -> Component.translatable("tactical_tablet.cinematic.fire");
            case DESCENT -> Component.translatable("tactical_tablet.cinematic.descent");
            case IMPACT -> Component.translatable("tactical_tablet.cinematic.impact");
            case RETURN, OVER -> null;
        };
    }
}
