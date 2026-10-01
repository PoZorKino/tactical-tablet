package moe.dexx.tacticaltablet.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Integer slider in the tablet style: click or drag to set a value between min and max. */
public class TabletSlider extends AbstractWidget {
    private final int min;
    private final int max;
    private final IntFunction<Component> label;
    private final IntConsumer onChange;
    private int value;

    public TabletSlider(int x, int y, int width, int height, int min, int max, int value,
                        IntFunction<Component> label, IntConsumer onChange) {
        super(x, y, width, height, label.apply(value));
        this.min = min;
        this.max = max;
        this.value = Mth.clamp(value, min, max);
        this.label = label;
        this.onChange = onChange;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        setFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        setFromMouse(mouseX);
    }

    private void setFromMouse(double mouseX) {
        double fraction = Mth.clamp((mouseX - (getX() + 3)) / (double) (width - 6), 0.0, 1.0);
        int newValue = min + (int) Math.round(fraction * (max - min));
        if (newValue != value) {
            value = newValue;
            setMessage(label.apply(value));
            onChange.accept(value);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, TabletStyle.BUTTON);
        int filled = (int) Math.round((width - 2) * (value - min) / (double) (max - min));
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, hovered ? 0xA02A9CC4 : 0x801B7FA0);
        graphics.fill(x + filled, y + 1, x + filled + 2, y + height - 1, TabletStyle.ACCENT);
        graphics.renderOutline(x, y, width, height, hovered ? TabletStyle.ACCENT : TabletStyle.LINE);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + (height - 8) / 2, TabletStyle.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
