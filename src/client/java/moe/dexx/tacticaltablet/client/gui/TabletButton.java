package moe.dexx.tacticaltablet.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Tablet button with five visual states: normal, hovered, pressed, disabled and error.
 * The error state is entered with flashError() and lasts one second.
 */
public class TabletButton extends AbstractWidget {
    private static final long ERROR_MILLIS = 1000L;

    private final Runnable onPress;
    private boolean pressed;
    private boolean selected;
    private long errorUntil;

    public TabletButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    /** Marks the button as the chosen option (tabs, presets, toggles that are on). */
    public TabletButton selected(boolean value) {
        this.selected = value;
        return this;
    }

    public void flashError() {
        errorUntil = System.currentTimeMillis() + ERROR_MILLIS;
    }

    public boolean inError() {
        return System.currentTimeMillis() < errorUntil;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        pressed = true;
        onPress.run();
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        pressed = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean error = inError();
        boolean hovered = isHoveredOrFocused();
        if (!hovered) {
            pressed = false;
        }
        int x = getX();
        if (error) {
            // A short horizontal shake while the error state lasts.
            x += (int) Math.round(Math.sin(System.currentTimeMillis() / 25.0) * 2.0);
        }
        int y = getY();

        int background;
        int border;
        int text;
        if (!active) {
            background = TabletStyle.BUTTON_DISABLED;
            border = 0xFF1A2A36;
            text = TabletStyle.TEXT_DISABLED;
        } else if (error) {
            background = 0xC0401010;
            border = TabletStyle.ERROR;
            text = TabletStyle.ERROR;
        } else if (pressed) {
            background = TabletStyle.BUTTON_PRESSED;
            border = 0xFFFFFFFF;
            text = TabletStyle.TEXT_PRESSED;
        } else if (hovered) {
            background = TabletStyle.BUTTON_HOVER;
            border = TabletStyle.ACCENT;
            text = 0xFFFFFFFF;
        } else {
            background = TabletStyle.BUTTON;
            border = selected ? TabletStyle.ACCENT : TabletStyle.LINE;
            text = selected ? TabletStyle.ACCENT : TabletStyle.TEXT;
        }

        graphics.fill(x, y, x + width, y + height, background);
        graphics.renderOutline(x, y, width, height, border);
        if (selected && active && !pressed) {
            graphics.fill(x + 1, y + 1, x + 3, y + height - 1, TabletStyle.ACCENT);
        }
        Font font = Minecraft.getInstance().font;
        Component label = getMessage();
        int labelWidth = font.width(label);
        int textX = labelWidth > width - 6 ? x + 4 : x + (width - labelWidth) / 2;
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        graphics.drawString(font, label, textX, y + (height - 8) / 2, text, false);
        graphics.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
