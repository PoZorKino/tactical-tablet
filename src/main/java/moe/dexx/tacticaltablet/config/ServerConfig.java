package moe.dexx.tacticaltablet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side limits. Loading never throws: a missing, broken or absurd file falls back to safe values. */
public final class ServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("tactical_tablet");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int maxRadius = 1000;
    public int maxConcurrentStrikes = 2;
    public int tickBudgetMs = 15;
    public int maxForcedChunks = 16;
    public boolean generateMissingChunks = true;
    public List<String> allowedGameModes = defaultGameModes();

    private static List<String> defaultGameModes() {
        return new ArrayList<>(List.of("survival", "creative", "adventure"));
    }

    public static ServerConfig load(Path file) {
        ServerConfig config = null;
        if (Files.exists(file)) {
            try {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerConfig.class);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
            }
        }
        boolean writeBack = config == null && !Files.exists(file);
        if (config == null) {
            config = new ServerConfig();
        }
        config.sanitize();
        if (writeBack) {
            config.save(file);
        }
        return config;
    }

    public void sanitize() {
        maxRadius = clamp(maxRadius, 1, 1000);
        maxConcurrentStrikes = clamp(maxConcurrentStrikes, 1, 16);
        tickBudgetMs = clamp(tickBudgetMs, 2, 40);
        maxForcedChunks = clamp(maxForcedChunks, 1, 256);
        List<String> modes = new ArrayList<>();
        if (allowedGameModes != null) {
            for (String mode : allowedGameModes) {
                if (mode != null && !mode.isBlank()) {
                    modes.add(mode.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        allowedGameModes = modes.isEmpty() ? defaultGameModes() : modes;
    }

    public boolean allows(String gameModeName) {
        return gameModeName != null && allowedGameModes.contains(gameModeName.toLowerCase(Locale.ROOT));
    }

    private void save(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
