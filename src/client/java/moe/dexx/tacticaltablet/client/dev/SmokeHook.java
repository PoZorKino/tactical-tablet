package moe.dexx.tacticaltablet.client.dev;

import java.util.Locale;
import moe.dexx.tacticaltablet.client.cinematic.Cinematic;
import moe.dexx.tacticaltablet.client.gui.TabletScreen;
import moe.dexx.tacticaltablet.client.net.ClientNetworking;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development aid, inactive unless the game is started with -Dtactical_tablet.smoke: creates a flat world, launches
 * one small strike and saves a screenshot of the tablet and of every shot of the cinematic, then quits.
 */
public final class SmokeHook {
    /** Cinematic seconds at which a screenshot is taken. */
    private static final double[] SHOTS = {1.5, 4.8, 5.8, 7.0, 8.8, 11.0, 13.0, 15.0, 17.0, 18.8, 19.9, 20.5, 21.0, 22.0, 23.0, 23.8, 24.5, 26.0, 28.0, 29.5};

    private static boolean worldRequested;
    private static int ticksInWorld;
    private static int nextShot;
    private static int ticksAfterEnd;

    private SmokeHook() {
    }

    public static void register() {
        if (System.getProperty("tactical_tablet.smoke") != null) {
            ClientTickEvents.END_CLIENT_TICK.register(SmokeHook::tick);
        }
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            if (!worldRequested && minecraft.getOverlay() == null && minecraft.screen != null) {
                worldRequested = true;
                String name = "smoke-" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261001L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        if (minecraft.player == null) {
            return;
        }
        ticksInWorld++;
        switch (ticksInWorld) {
            case 60 -> {
                minecraft.player.connection.sendCommand("give @s tactical_tablet:tactical_tablet");
                minecraft.player.connection.sendCommand("time set noon");
            }
            case 100 -> minecraft.setScreen(new TabletScreen());
            case 125 -> shot(minecraft, "tablet");
            case 130 -> {
                minecraft.setScreen(null);
                BlockPos target = minecraft.player.blockPosition().offset(70, -1, 0);
                ClientNetworking.sendLaunch(StrikeParams.DEFAULT.withTarget(target.getX(), target.getY(), target.getZ())
                        .withRadius(30).withSalvos(3).withCountdown(3).withType(smokeType()));
            }
            case 160 -> shot(minecraft, "countdown");
            default -> {
            }
        }
        if (Cinematic.isActive()) {
            double seconds = Cinematic.seconds(0.0F);
            if (nextShot < SHOTS.length && seconds >= SHOTS[nextShot]) {
                shot(minecraft, String.format(Locale.ROOT, "shot_%02d", nextShot + 1));
                nextShot++;
            }
        } else if (nextShot > 0) {
            ticksAfterEnd++;
            if (ticksAfterEnd == 60) {
                shot(minecraft, "after");
            }
        }
        if (ticksAfterEnd >= 100 || ticksInWorld > 2200) {
            minecraft.stop();
        }
    }

    private static StrikeType smokeType() {
        try {
            return StrikeType.valueOf(System.getProperty("tactical_tablet.smoke").toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return StrikeType.ORBITAL_LASER;
        }
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), message -> {
        });
    }
}
