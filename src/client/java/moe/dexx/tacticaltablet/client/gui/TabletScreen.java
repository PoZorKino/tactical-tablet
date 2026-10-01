package moe.dexx.tacticaltablet.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import moe.dexx.tacticaltablet.ModSounds;
import moe.dexx.tacticaltablet.client.TacticalTabletClient;
import moe.dexx.tacticaltablet.client.config.ClientConfig;
import moe.dexx.tacticaltablet.client.net.ClientNetworking;
import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.client.target.AimMode;
import moe.dexx.tacticaltablet.client.target.HeldTablet;
import moe.dexx.tacticaltablet.destruction.CraterProfile;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeTimeline;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** The tablet interface: status, strike type, target and settings pages plus the launch confirmation. */
public class TabletScreen extends Screen {
    private static final int PANEL_WIDTH = 348;
    private static final int PANEL_HEIGHT = 232;
    private static final int NAV_WIDTH = 112;
    private static final int[] RADIUS_PRESETS = {25, 50, 100, 250, 500, 1000};
    private static final int LARGE_RADIUS = 250;

    private enum Page { STATUS, STRIKE_TYPE, TARGET, SETTINGS }

    private StrikeParams params;
    private Page page = Page.STATUS;
    private boolean confirming;
    private boolean dirty;
    private int left;
    private int top;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private TabletButton launchButton;
    private TabletButton cancelButton;
    private TabletButton applyTargetButton;
    private EditBox radiusBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    // Screen keeps its own widget list private, so this screen tracks what it added to draw the two layers in order.
    private final List<Renderable> pageWidgets = new ArrayList<>();
    private final List<Renderable> modalWidgets = new ArrayList<>();
    private long rejectSeen = ClientStrikes.lastRejectMillis();

    public TabletScreen() {
        super(Component.translatable("tactical_tablet.gui.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (params == null) {
            StrikeParams held = minecraft.player == null ? null : HeldTablet.params(minecraft.player);
            params = held == null ? StrikeParams.DEFAULT : held;
            if (TacticalTabletClient.config().soundEffects) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TABLET_OPEN, 1.0F));
            }
        }
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        contentX = left + NAV_WIDTH + 10;
        contentY = top + 30;
        contentWidth = PANEL_WIDTH - NAV_WIDTH - 20;
        pageWidgets.clear();
        modalWidgets.clear();
        radiusBox = null;
        xBox = null;
        yBox = null;
        zBox = null;
        applyTargetButton = null;

        initNavigation();
        switch (page) {
            case STRIKE_TYPE -> initStrikeTypePage();
            case TARGET -> initTargetPage();
            case SETTINGS -> initSettingsPage();
            default -> {
            }
        }
        if (confirming) {
            initConfirmation();
        }
        updateDynamicButtons();
    }

    private <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
        // While the confirmation is open the page underneath stays visible but does not react.
        if (confirming && widget instanceof net.minecraft.client.gui.components.AbstractWidget abstractWidget) {
            abstractWidget.active = false;
        }
        pageWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private void initNavigation() {
        int x = left + 8;
        int y = top + 30;
        int w = NAV_WIDTH - 8;
        add(new TabletButton(x, y, w, 20, Component.translatable("tactical_tablet.gui.nav.strike_type"),
                () -> open(Page.STRIKE_TYPE)).selected(page == Page.STRIKE_TYPE));
        add(new TabletButton(x, y + 24, w, 20, Component.translatable("tactical_tablet.gui.nav.target"),
                () -> open(Page.TARGET)).selected(page == Page.TARGET));
        add(new TabletButton(x, y + 48, w, 20, Component.translatable("tactical_tablet.gui.nav.settings"),
                () -> open(Page.SETTINGS)).selected(page == Page.SETTINGS));
        launchButton = add(new TabletButton(x, y + 84, w, 24, Component.translatable("tactical_tablet.gui.nav.launch"), this::requestLaunch));
        cancelButton = add(new TabletButton(x, y + 112, w, 20, Component.translatable("tactical_tablet.gui.nav.cancel"), this::cancelOrStop));
        add(new TabletButton(x, top + PANEL_HEIGHT - 28, w, 20, Component.translatable("tactical_tablet.gui.nav.close"), this::onClose));
        if (page != Page.STATUS) {
            add(new TabletButton(left + PANEL_WIDTH - 68, top + 6, 60, 16, Component.translatable("tactical_tablet.gui.back"),
                    () -> open(Page.STATUS)));
        }
    }

    private void initStrikeTypePage() {
        int y = contentY + 14;
        for (StrikeType type : StrikeType.values()) {
            add(new TabletButton(contentX, y, contentWidth, 20, typeName(type), () -> {
                change(params.withType(type));
                rebuildWidgets();
            }).selected(params.type() == type));
            y += 24;
        }
    }

    private void initTargetPage() {
        int y = contentY + 28;
        add(new TabletButton(contentX, y, contentWidth, 20, Component.translatable("tactical_tablet.gui.target.aim"), () -> startAim(true)));
        add(new TabletButton(contentX, y + 24, contentWidth, 20, Component.translatable("tactical_tablet.gui.target.block"), () -> startAim(false)));

        int boxY = y + 70;
        int boxWidth = (contentWidth - 8) / 3;
        xBox = coordinateBox(contentX, boxY, boxWidth, params.hasTarget() ? params.targetX() : blockCoordinate(0));
        yBox = coordinateBox(contentX + boxWidth + 4, boxY, boxWidth, params.hasTarget() ? params.targetY() : blockCoordinate(1));
        zBox = coordinateBox(contentX + 2 * (boxWidth + 4), boxY, boxWidth, params.hasTarget() ? params.targetZ() : blockCoordinate(2));
        applyTargetButton = add(new TabletButton(contentX, boxY + 24, contentWidth, 20,
                Component.translatable("tactical_tablet.gui.target.apply"), this::applyManualTarget));
    }

    private int blockCoordinate(int axis) {
        if (minecraft.player == null) {
            return 0;
        }
        return switch (axis) {
            case 0 -> minecraft.player.getBlockX();
            case 1 -> minecraft.player.getBlockY();
            default -> minecraft.player.getBlockZ();
        };
    }

    private EditBox coordinateBox(int x, int y, int w, int value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("-?\\d{0,8}"));
        box.setValue(Integer.toString(value));
        return add(box);
    }

    private void initSettingsPage() {
        int y = contentY + 12;
        int presetWidth = (contentWidth - 10) / RADIUS_PRESETS.length;
        for (int i = 0; i < RADIUS_PRESETS.length; i++) {
            int radius = RADIUS_PRESETS[i];
            add(new TabletButton(contentX + i * (presetWidth + 2), y, presetWidth, 18, Component.literal(Integer.toString(radius)), () -> {
                change(params.withRadius(radius));
                rebuildWidgets();
            }).selected(params.radius() == radius));
        }
        y += 22;
        radiusBox = new EditBox(font, contentX + contentWidth - 60, y, 60, 18, Component.empty());
        radiusBox.setMaxLength(4);
        radiusBox.setFilter(text -> text.matches("\\d{0,4}"));
        radiusBox.setValue(Integer.toString(params.radius()));
        radiusBox.setResponder(this::onCustomRadius);
        add(radiusBox);

        y += 22;
        add(new TabletSlider(contentX, y, contentWidth, 18, StrikeParams.MIN_POWER, StrikeParams.MAX_POWER, params.power(),
                value -> Component.translatable("tactical_tablet.gui.settings.power", value), value -> change(params.withPower(value))));
        y += 22;
        add(new TabletSlider(contentX, y, contentWidth, 18, StrikeParams.MIN_SALVOS, StrikeParams.MAX_SALVOS, params.salvos(),
                value -> Component.translatable("tactical_tablet.gui.settings.salvos", value), value -> change(params.withSalvos(value))));
        y += 22;
        add(new TabletSlider(contentX, y, contentWidth, 18, StrikeParams.MIN_COUNTDOWN, StrikeParams.MAX_COUNTDOWN, params.countdownSeconds(),
                value -> Component.translatable("tactical_tablet.gui.settings.countdown", value), value -> change(params.withCountdown(value))));

        y += 24;
        int third = (contentWidth - 6) / 3;
        toggle(contentX, y, third, "tactical_tablet.gui.settings.blocks", params.destroyBlocks(), value -> change(params.withDestroyBlocks(value)));
        toggle(contentX + third + 3, y, third, "tactical_tablet.gui.settings.liquids", params.destroyLiquids(), value -> change(params.withDestroyLiquids(value)));
        toggle(contentX + 2 * (third + 3), y, third, "tactical_tablet.gui.settings.entities", params.damageEntities(), value -> change(params.withDamageEntities(value)));

        y += 22;
        ClientConfig config = TacticalTabletClient.config();
        toggle(contentX, y, third, "tactical_tablet.gui.settings.visual", config.visualEffects, value -> {
            config.visualEffects = value;
            config.save();
        });
        toggle(contentX + third + 3, y, third, "tactical_tablet.gui.settings.sound", config.soundEffects, value -> {
            config.soundEffects = value;
            config.save();
        });
        toggle(contentX + 2 * (third + 3), y, third, "tactical_tablet.gui.settings.cinematic", config.cinematicEnabled, value -> {
            config.cinematicEnabled = value;
            config.save();
        });

        y += 22;
        add(new TabletButton(contentX, y, contentWidth, 18,
                Component.translatable("tactical_tablet.gui.settings.quality",
                        Component.translatable("tactical_tablet.gui.quality." + config.effectsQuality)), () -> {
            config.cycleQuality();
            config.save();
            rebuildWidgets();
        }));
    }

    private void toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> setter) {
        MutableComponent label = Component.translatable(key).append(": ")
                .append(Component.translatable(value ? "tactical_tablet.gui.on" : "tactical_tablet.gui.off"));
        add(new TabletButton(x, y, w, 18, label, () -> {
            setter.accept(!value);
            rebuildWidgets();
        }).selected(value));
    }

    private void initConfirmation() {
        int boxWidth = 250;
        int boxX = (width - boxWidth) / 2;
        int boxY = (height - 96) / 2;
        TabletButton yes = new TabletButton(boxX + 10, boxY + 66, 126, 20, Component.translatable("tactical_tablet.gui.confirm.yes"), () -> {
            confirming = false;
            ClientNetworking.sendLaunch(params);
            dirty = false;
            page = Page.STATUS;
            rebuildWidgets();
        });
        TabletButton no = new TabletButton(boxX + boxWidth - 100, boxY + 66, 90, 20, Component.translatable("tactical_tablet.gui.confirm.no"), () -> {
            confirming = false;
            rebuildWidgets();
        });
        addRenderableWidget(yes);
        addRenderableWidget(no);
        modalWidgets.add(yes);
        modalWidgets.add(no);
    }

    private void open(Page newPage) {
        page = newPage;
        rebuildWidgets();
    }

    private void change(StrikeParams newParams) {
        params = newParams;
        dirty = true;
    }

    private void onCustomRadius(String text) {
        int radius = parse(text, -1);
        boolean valid = radius >= StrikeParams.MIN_RADIUS && radius <= StrikeParams.MAX_RADIUS;
        radiusBox.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
        if (valid && radius != params.radius()) {
            change(params.withRadius(radius));
        }
    }

    private void applyManualTarget() {
        int x = parse(xBox.getValue(), Integer.MIN_VALUE);
        int y = parse(yBox.getValue(), Integer.MIN_VALUE);
        int z = parse(zBox.getValue(), Integer.MIN_VALUE);
        boolean valid = x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && z != Integer.MIN_VALUE
                && minecraft.level != null && y >= minecraft.level.getMinBuildHeight() && y < minecraft.level.getMaxBuildHeight()
                && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000;
        if (!valid) {
            applyTargetButton.flashError();
            ClientStrikes.notify(Component.translatable("tactical_tablet.gui.target.invalid"), true);
            return;
        }
        change(params.withTarget(x, y, z));
        save();
        open(Page.STATUS);
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void startAim(boolean longRange) {
        save();
        AimMode.start(longRange);
        onClose();
    }

    private void requestLaunch() {
        if (!params.hasTarget()) {
            launchButton.flashError();
            ClientStrikes.notify(Component.translatable(StrikeParams.REJECT_NO_TARGET), true);
            return;
        }
        if (params.validateSettings(StrikeParams.MAX_RADIUS) != null) {
            launchButton.flashError();
            ClientStrikes.notify(Component.translatable(params.validateSettings(StrikeParams.MAX_RADIUS)), true);
            return;
        }
        confirming = true;
        rebuildWidgets();
    }

    private void cancelOrStop() {
        StrikeState own = ClientStrikes.own();
        if (own == null) {
            return;
        }
        if (own.phase().cancellable()) {
            ClientNetworking.sendCancel(false);
        } else {
            ClientNetworking.sendEmergencyStop();
        }
    }

    private void save() {
        if (dirty && minecraft.player != null) {
            HeldTablet.save(minecraft.player, params);
            dirty = false;
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || HeldTablet.params(minecraft.player) == null) {
            onClose();
            return;
        }
        updateDynamicButtons();
        long reject = ClientStrikes.lastRejectMillis();
        if (reject != rejectSeen) {
            rejectSeen = reject;
            if (launchButton != null) {
                launchButton.flashError();
            }
        }
        if (radiusBox != null) {
            radiusBox.tick();
        }
        if (xBox != null) {
            xBox.tick();
            yBox.tick();
            zBox.tick();
        }
    }

    private void updateDynamicButtons() {
        StrikeState own = ClientStrikes.own();
        if (launchButton != null) {
            launchButton.active = !confirming && own == null;
        }
        if (cancelButton != null) {
            boolean canCancel = own != null && own.phase().cancellable();
            boolean canStop = own != null && own.modifiesWorld()
                    && (own.phase() == StrikePhase.IMPACT || own.phase() == StrikePhase.DESTROYING);
            cancelButton.active = !confirming && (canCancel || canStop);
            cancelButton.setMessage(Component.translatable(canStop ? "tactical_tablet.gui.nav.emergency" : "tactical_tablet.gui.nav.cancel"));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirming && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            confirming = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        save();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics);
        switch (page) {
            case STRIKE_TYPE -> drawStrikeTypePage(graphics);
            case TARGET -> drawTargetPage(graphics);
            case SETTINGS -> drawSettingsPage(graphics);
            default -> drawStatusPage(graphics);
        }
        for (Renderable renderable : pageWidgets) {
            renderable.render(graphics, confirming ? -1 : mouseX, confirming ? -1 : mouseY, partialTick);
        }
        if (confirming) {
            drawConfirmation(graphics);
            for (Renderable renderable : modalWidgets) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, TabletStyle.PANEL);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, TabletStyle.ACCENT_DIM);
        graphics.renderOutline(left - 2, top - 2, PANEL_WIDTH + 4, PANEL_HEIGHT + 4, 0x6035D0FF);
        graphics.fill(left + 1, top + 24, left + PANEL_WIDTH - 1, top + 25, TabletStyle.LINE);
        graphics.fill(left + NAV_WIDTH + 4, top + 25, left + NAV_WIDTH + 5, top + PANEL_HEIGHT - 1, TabletStyle.LINE);
        graphics.fill(left + NAV_WIDTH + 5, top + 25, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, TabletStyle.PANEL_INSET);

        // Scan line sweeping down the content area.
        int span = PANEL_HEIGHT - 28;
        int scan = top + 26 + (int) ((System.currentTimeMillis() / 18L) % span);
        graphics.fill(left + NAV_WIDTH + 5, scan, left + PANEL_WIDTH - 1, scan + 1, 0x3035D0FF);

        graphics.drawString(font, title, left + 8, top + 5, TabletStyle.ACCENT, false);
        graphics.drawString(font, Component.translatable("tactical_tablet.gui.subtitle"), left + 8, top + 14, TabletStyle.TEXT_DIM, false);
        boolean blink = (System.currentTimeMillis() / 500L) % 2 == 0;
        StrikeState own = ClientStrikes.own();
        int lamp = own != null ? (blink ? TabletStyle.WARNING : 0xFF604010) : TabletStyle.OK;
        if (page == Page.STATUS) {
            graphics.fill(left + PANEL_WIDTH - 14, top + 9, left + PANEL_WIDTH - 8, top + 15, lamp);
        }
    }

    private void drawStatusPage(GuiGraphics graphics) {
        int x = contentX;
        int y = contentY;
        StrikeState own = ClientStrikes.own();

        Component target = params.hasTarget()
                ? Component.literal(params.targetX() + "  " + params.targetY() + "  " + params.targetZ())
                : Component.translatable("tactical_tablet.gui.status.target_none");
        y = statusLine(graphics, x, y, "tactical_tablet.gui.status.target", target, params.hasTarget() ? TabletStyle.TEXT : TabletStyle.WARNING);
        y = statusLine(graphics, x, y, "tactical_tablet.gui.status.radius",
                Component.translatable("tactical_tablet.gui.status.radius_value", params.radius()), TabletStyle.TEXT);
        y = statusLine(graphics, x, y, "tactical_tablet.gui.status.type", typeName(params.type()), TabletStyle.TEXT);
        y = statusLine(graphics, x, y, "tactical_tablet.gui.status.system", systemState(own), systemColor(own));
        y = statusLine(graphics, x, y, "tactical_tablet.gui.status.time", timeToShot(own), TabletStyle.TEXT);

        y += 4;
        graphics.fill(x, y, x + contentWidth, y + 1, TabletStyle.LINE);
        y += 6;
        long area = Math.round(Math.PI * params.radius() * params.radius());
        Component scale = Component.translatable("tactical_tablet.gui.status.scale",
                String.format(Locale.ROOT, "%,d", area).replace(',', ' '), CraterProfile.maxDepth(params.radius(), params.power()));
        y = wrapped(graphics, scale, x, y, params.modifiesWorld() ? TabletStyle.TEXT : TabletStyle.TEXT_DIM);
        if (params.radius() >= LARGE_RADIUS && params.modifiesWorld()) {
            y = wrapped(graphics, Component.translatable("tactical_tablet.gui.warning.large"), x, y + 4, TabletStyle.WARNING);
        }
        if (params.hasTarget() && reachesUnloadedChunks()) {
            y = wrapped(graphics, Component.translatable("tactical_tablet.gui.warning.unloaded"), x, y + 4, TabletStyle.WARNING);
        }
        Component notice = ClientStrikes.notice();
        if (notice != null) {
            wrapped(graphics, notice, x, y + 4, ClientStrikes.noticeIsError() ? TabletStyle.ERROR : TabletStyle.OK);
        }
    }

    private int statusLine(GuiGraphics graphics, int x, int y, String labelKey, Component value, int valueColor) {
        graphics.drawString(font, Component.translatable(labelKey), x, y, TabletStyle.TEXT_DIM, false);
        graphics.drawString(font, value, x + 84, y, valueColor, false);
        return y + 13;
    }

    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : font.split(text, contentWidth)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private Component systemState(StrikeState own) {
        if (own != null) {
            return switch (own.phase()) {
                case COUNTDOWN -> Component.translatable("tactical_tablet.gui.system.countdown");
                case CHARGE -> Component.translatable("tactical_tablet.gui.system.charge");
                case TRAVEL -> Component.translatable("tactical_tablet.gui.system.travel");
                case IMPACT -> Component.translatable("tactical_tablet.gui.system.impact");
                default -> Component.translatable("tactical_tablet.gui.system.destroying", ClientStrikes.progressPercent(own));
            };
        }
        if (ClientStrikes.notice() != null && ClientStrikes.noticeIsError()) {
            return Component.translatable("tactical_tablet.gui.system.error");
        }
        return Component.translatable(params.hasTarget() ? "tactical_tablet.gui.system.ready" : "tactical_tablet.gui.system.no_target");
    }

    private int systemColor(StrikeState own) {
        if (own != null) {
            return TabletStyle.WARNING;
        }
        if (ClientStrikes.notice() != null && ClientStrikes.noticeIsError()) {
            return TabletStyle.ERROR;
        }
        return params.hasTarget() ? TabletStyle.OK : TabletStyle.WARNING;
    }

    private Component timeToShot(StrikeState own) {
        if (own == null) {
            return Component.translatable("tactical_tablet.gui.status.time_idle", params.countdownSeconds(), StrikeTimeline.CHARGE_TICKS / 20);
        }
        long ticks = switch (own.phase()) {
            case COUNTDOWN -> ClientStrikes.ticksLeft(own) + StrikeTimeline.CHARGE_TICKS;
            case CHARGE -> ClientStrikes.ticksLeft(own);
            default -> 0;
        };
        return Component.translatable("tactical_tablet.gui.status.time_value", String.format(Locale.ROOT, "%.1f", ticks / 20.0));
    }

    private boolean reachesUnloadedChunks() {
        if (minecraft.player == null) {
            return false;
        }
        double dx = params.targetX() + 0.5 - minecraft.player.getX();
        double dz = params.targetZ() + 0.5 - minecraft.player.getZ();
        double reach = Math.sqrt(dx * dx + dz * dz) + params.radius();
        return reach > minecraft.options.getEffectiveRenderDistance() * 16.0;
    }

    private void drawStrikeTypePage(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("tactical_tablet.gui.nav.strike_type"), contentX, contentY, TabletStyle.ACCENT, false);
        int y = contentY + 14 + StrikeType.values().length * 24 + 6;
        String key = "tactical_tablet.gui.type." + params.type().name().toLowerCase(Locale.ROOT) + ".desc";
        wrapped(graphics, Component.translatable(key), contentX, y, TabletStyle.TEXT);
    }

    private void drawTargetPage(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("tactical_tablet.gui.nav.target"), contentX, contentY, TabletStyle.ACCENT, false);
        Component target = params.hasTarget()
                ? Component.literal("X " + params.targetX() + "   Y " + params.targetY() + "   Z " + params.targetZ())
                : Component.translatable("tactical_tablet.gui.status.target_none");
        graphics.drawString(font, target, contentX, contentY + 13, params.hasTarget() ? TabletStyle.TEXT : TabletStyle.WARNING, false);
        graphics.drawString(font, Component.translatable("tactical_tablet.gui.target.manual"), contentX, contentY + 86, TabletStyle.TEXT_DIM, false);
        Component notice = ClientStrikes.notice();
        if (notice != null && ClientStrikes.noticeIsError()) {
            wrapped(graphics, notice, contentX, contentY + 150, TabletStyle.ERROR);
        }
    }

    private void drawSettingsPage(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("tactical_tablet.gui.settings.radius"), contentX, contentY, TabletStyle.ACCENT, false);
        graphics.drawString(font, Component.translatable("tactical_tablet.gui.settings.custom", StrikeParams.MAX_RADIUS),
                contentX, contentY + 39, TabletStyle.TEXT_DIM, false);
        if (params.radius() >= LARGE_RADIUS) {
            graphics.fill(contentX + contentWidth - 64, contentY + 33, contentX + contentWidth - 62, contentY + 53, TabletStyle.WARNING);
        }
    }

    private void drawConfirmation(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int boxWidth = 250;
        int boxX = (width - boxWidth) / 2;
        int boxY = (height - 96) / 2;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 96, 0xF0100808);
        graphics.renderOutline(boxX, boxY, boxWidth, 96, TabletStyle.ERROR);
        Component text = Component.translatable(params.modifiesWorld()
                ? "tactical_tablet.gui.confirm.text" : "tactical_tablet.gui.confirm.text_visual");
        int y = boxY + 10;
        for (FormattedCharSequence line : font.split(text, boxWidth - 20)) {
            graphics.drawString(font, line, boxX + 10, y, TabletStyle.TEXT, false);
            y += 10;
        }
        Component summary = Component.literal(params.targetX() + " " + params.targetY() + " " + params.targetZ() + "  ·  R " + params.radius());
        graphics.drawString(font, summary, boxX + 10, boxY + 50, TabletStyle.WARNING, false);
    }

    private static Component typeName(StrikeType type) {
        return Component.translatable("tactical_tablet.gui.type." + type.name().toLowerCase(Locale.ROOT));
    }
}
