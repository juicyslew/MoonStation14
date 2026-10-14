package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProduceProfileTest {
    @Test void resolvedProduceAndIngestionSourcesAreUniqueAndComplete() {
        var manager = PrototypeRuntime.serverManager();
        manager.reload(ModProduceProfiles.TYPE, read("produce_profile", "produce_base", "wheat_bushel", "tomato", "potato", "poppy", "aloe"));
        manager.reload(ModIngestionProfiles.TYPE, read("ingestion_profile", "food_base", "edible_defaults", "tomato", "potato", "poppy", "aloe"));
        var produce = manager.snapshot(ModProduceProfiles.TYPE);
        var foods = manager.snapshot(ModIngestionProfiles.TYPE);
        Map<String, Integer> sourceCaps = Map.of("tomato", 1400, "potato", 1400, "poppy", 2200, "aloe", 2000);
        for (String name : new String[]{"wheat_bushel", "tomato", "potato", "poppy", "aloe"}) {
            var profile = produce.get(id(name));
            assertNotNull(profile);
            assertTrue(profile.grindable());
            assertEquals(name.equals("wheat_bushel") ? "wheat" : name, profile.seed().getPath());
            var ingestion = foods.get(id(name));
            if (name.equals("wheat_bushel")) {
                assertNull(ingestion, "wheat must not receive ordinary edible policy");
                assertEquals(1000, profile.sourceSolution().capacityCents());
                assertEquals(Map.of(id("flour"), 1000L), profile.sourceSolution().contentsCents());
            } else {
                assertNotNull(ingestion);
                assertEquals(sourceCaps.get(name), ingestion.edible().sourceSolution().capacityCents());
                assertNull(profile.sourceSolution(), "food source belongs only to IngestionProfile");
                assertEquals(500, ingestion.doseCents());
                assertEquals(20, ingestion.delayTicks());
                assertEquals(60, ingestion.forceFeedDelayTicks());
            }
        }
    }

    private Map<ResourceLocation, JsonObject> read(String family, String... names) {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (String name : names) {
            String path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                result.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        return result;
    }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14", value); }
}
