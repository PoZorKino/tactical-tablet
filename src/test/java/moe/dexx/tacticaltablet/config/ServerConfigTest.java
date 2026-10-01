package moe.dexx.tacticaltablet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigTest {
    @TempDir
    Path dir;

    private ServerConfig loadFrom(String json) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return ServerConfig.load(file);
    }

    @Test
    void missingFileGivesDefaultsAndIsCreated() {
        Path file = dir.resolve("nested").resolve("config.json");
        ServerConfig config = ServerConfig.load(file);
        assertEquals(1000, config.maxRadius);
        assertEquals(2, config.maxConcurrentStrikes);
        assertEquals(15, config.tickBudgetMs);
        assertEquals(16, config.maxForcedChunks);
        assertTrue(config.generateMissingChunks);
        assertTrue(config.allows("survival"));
        assertTrue(config.allows("creative"));
        assertTrue(config.allows("adventure"));
        assertFalse(config.allows("spectator"));
        assertTrue(Files.exists(file));
    }

    @Test
    void valuesFromTheFileAreUsed() throws IOException {
        ServerConfig config = loadFrom("""
                {"maxRadius": 250, "maxConcurrentStrikes": 4, "tickBudgetMs": 8, "maxForcedChunks": 32,
                 "generateMissingChunks": false, "allowedGameModes": ["creative"]}
                """);
        assertEquals(250, config.maxRadius);
        assertEquals(4, config.maxConcurrentStrikes);
        assertEquals(8, config.tickBudgetMs);
        assertEquals(32, config.maxForcedChunks);
        assertFalse(config.generateMissingChunks);
        assertTrue(config.allows("creative"));
        assertFalse(config.allows("survival"));
    }

    @Test
    void absurdValuesAreBroughtIntoRange() throws IOException {
        ServerConfig config = loadFrom("""
                {"maxRadius": 5000, "maxConcurrentStrikes": 0, "tickBudgetMs": 900, "maxForcedChunks": -5}
                """);
        assertEquals(1000, config.maxRadius);
        assertEquals(1, config.maxConcurrentStrikes);
        assertEquals(40, config.tickBudgetMs);
        assertEquals(1, config.maxForcedChunks);
    }

    @Test
    void negativeRadiusBecomesOne() throws IOException {
        assertEquals(1, loadFrom("{\"maxRadius\": -10}").maxRadius);
    }

    @Test
    void brokenJsonGivesDefaults() throws IOException {
        ServerConfig config = loadFrom("{ this is not json");
        assertEquals(1000, config.maxRadius);
        assertTrue(config.allows("survival"));
    }

    @Test
    void wrongTypesGiveDefaults() throws IOException {
        ServerConfig config = loadFrom("{\"maxRadius\": \"lots\"}");
        assertEquals(1000, config.maxRadius);
    }

    @Test
    void nullOrEmptyGameModeListFallsBackToDefaults() throws IOException {
        assertTrue(loadFrom("{\"allowedGameModes\": null}").allows("survival"));
        assertTrue(loadFrom("{\"allowedGameModes\": [null, \"CREATIVE\"]}").allows("creative"));
    }

    @Test
    void emptyFileGivesDefaults() throws IOException {
        assertEquals(1000, loadFrom("").maxRadius);
    }
}
