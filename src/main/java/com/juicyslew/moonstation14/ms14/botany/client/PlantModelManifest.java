package com.juicyslew.moonstation14.ms14.botany.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses the packaged plant appearance binding and owns its model identifiers. */
public final class PlantModelManifest {
    private static final String RESOURCE = "/assets/moonstation14/botany/plant-models.json";
    private final Map<String, Map<String, ModelResourceLocation>> plants;
    private final Map<String, ModelResourceLocation> indicators;

    private PlantModelManifest(Map<String, Map<String, ModelResourceLocation>> plants, Map<String, ModelResourceLocation> indicators) { this.plants = Map.copyOf(plants); this.indicators=Map.copyOf(indicators); }

    public static PlantModelManifest load() {
        try (var stream = PlantModelManifest.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing packaged " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("formatVersion").getAsInt() != 1) throw new IllegalStateException("Unsupported plant model manifest version");
            JsonObject source = root.getAsJsonObject("plants");
            Map<String, Map<String, ModelResourceLocation>> plants = new LinkedHashMap<>();
            source.entrySet().forEach(plant -> {
                Map<String, ModelResourceLocation> states = new LinkedHashMap<>();
                plant.getValue().getAsJsonObject().entrySet().forEach(state -> {
                    ResourceLocation id = ResourceLocation.tryParse(state.getValue().getAsString());
                    if (id == null || !id.getPath().startsWith("block/botany/plants/"))
                        throw new IllegalStateException("Invalid plant model reference " + state.getValue());
                    states.put(state.getKey(), ModelResourceLocation.standalone(id));
                });
                plants.put(plant.getKey(), Map.copyOf(states));
            });
            Map<String, ModelResourceLocation> indicators = new LinkedHashMap<>();
            for (String key : java.util.List.of("harvest", "health", "alert", "nutrition", "water")) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", "block/botany/indicators/" + key);
                indicators.put(key, ModelResourceLocation.standalone(id));
            }
            return new PlantModelManifest(plants, indicators);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load plant model manifest", exception);
        }
    }

    public Map<String, Map<String, ModelResourceLocation>> plants() { return plants; }
    public Map<String, ModelResourceLocation> indicators() { return indicators; }
    public ModelResourceLocation model(String plant, String state) {
        Map<String, ModelResourceLocation> states = plants.get(plant);
        return states == null ? null : states.get(state);
    }
}
