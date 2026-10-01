package moe.dexx.tacticaltablet.client;

import moe.dexx.tacticaltablet.client.cinematic.Cinematic;
import moe.dexx.tacticaltablet.client.config.ClientConfig;
import moe.dexx.tacticaltablet.client.dev.SmokeHook;
import moe.dexx.tacticaltablet.client.effects.StrikeEffects;
import moe.dexx.tacticaltablet.client.gui.TabletScreen;
import moe.dexx.tacticaltablet.client.hud.TabletHud;
import moe.dexx.tacticaltablet.client.net.ClientNetworking;
import moe.dexx.tacticaltablet.client.render.ZoneRing;
import moe.dexx.tacticaltablet.client.target.AimMode;
import moe.dexx.tacticaltablet.item.TacticalTabletItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class TacticalTabletClient implements ClientModInitializer {
    private static ClientConfig config = new ClientConfig();

    public static ClientConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load(FabricLoader.getInstance().getConfigDir().resolve("tactical_tablet-client.json"));
        ClientNetworking.register();
        SmokeHook.register();

        // Using the tablet either fixes the aimed block as the target or opens the interface.
        TacticalTabletItem.clientUseHandler = hand -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (AimMode.isActive()) {
                AimMode.pick(minecraft);
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new TabletScreen());
            }
        };

        ClientTickEvents.END_CLIENT_TICK.register(AimMode::tick);
        ClientTickEvents.END_CLIENT_TICK.register(StrikeEffects::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Cinematic::tick);
        // The flash goes under the status lines so they stay readable.
        HudRenderCallback.EVENT.register(StrikeEffects::renderFlash);
        HudRenderCallback.EVENT.register(TabletHud::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ZoneRing::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(StrikeEffects::render);
    }
}
