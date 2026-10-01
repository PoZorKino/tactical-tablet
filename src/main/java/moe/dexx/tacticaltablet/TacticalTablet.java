package moe.dexx.tacticaltablet;

import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.TickTimeTracker;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.strike.PlayerOwnerProbe;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TacticalTablet implements ModInitializer {
    public static final String MOD_ID = "tactical_tablet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final TickTimeTracker TICK_TIMES = new TickTimeTracker();
    private static ServerConfig config = new ServerConfig();
    private static StrikeManager manager;
    private static long tickStartNanos;

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static ServerConfig config() {
        return config;
    }

    /** @return the manager of the running server, or null while no server is running */
    public static StrikeManager manager() {
        return manager;
    }

    public static TickTimeTracker tickTimes() {
        return TICK_TIMES;
    }

    @Override
    public void onInitialize() {
        ModItems.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            config = ServerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("tactical_tablet-server.json"));
            manager = new StrikeManager(server, config, new PlayerOwnerProbe(config), StrikeManager.Listener.NONE, TICK_TIMES);
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) {
                manager.tick();
            }
            TICK_TIMES.record(System.nanoTime() - tickStartNanos);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) {
                manager.shutdown();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> manager = null);

        LOGGER.info("Tactical Tablet loaded");
    }
}
