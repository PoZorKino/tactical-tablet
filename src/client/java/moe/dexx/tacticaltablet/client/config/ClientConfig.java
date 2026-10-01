package moe.dexx.tacticaltablet.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import moe.dexx.tacticaltablet.TacticalTablet;

/** Per-player presentation settings. Loading never throws: a missing or broken file gives the defaults. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] QUALITIES = {"low", "medium", "high"};

    public boolean cinematicEnabled = true;
    public String effectsQuality = "medium";
    public boolean visualEffects = true;
    public boolean soundEffects = true;
    public boolean cameraShake = true;

    private transient Path file;

    public static ClientConfig load(Path file) {
        ClientConfig config = null;
        if (Files.exists(file)) {
            try {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ClientConfig.class);
            } catch (IOException | RuntimeException e) {
                TacticalTablet.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
            }
        }
        if (config == null) {
            config = new ClientConfig();
        }
        config.file = file;
        config.sanitize();
        return config;
    }

    public void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TacticalTablet.LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    /** Cycles low -> medium -> high -> low. */
    public void cycleQuality() {
        for (int i = 0; i < QUALITIES.length; i++) {
            if (QUALITIES[i].equals(effectsQuality)) {
                effectsQuality = QUALITIES[(i + 1) % QUALITIES.length];
                return;
            }
        }
        effectsQuality = "medium";
    }

    /** @return the particle budget for one strike: 200, 800 or 2000 */
    public int particleBudget() {
        return switch (effectsQuality) {
            case "low" -> 200;
            case "high" -> 2000;
            default -> 800;
        };
    }

    private void sanitize() {
        boolean known = false;
        for (String quality : QUALITIES) {
            known |= quality.equals(effectsQuality);
        }
        if (!known) {
            effectsQuality = "medium";
        }
    }
}
