package com.juicyslew.moonstation14.ms14.botany.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses the packaged plant appearance binding and owns its model identifiers. */
public final class PlantModelManifest {
    private static final String RESOURCE = "/assets/moonstation14/botany/plant-models.json";
    private final Map<String, Map<String, ModelResourceLocation>> plants;
    private final Map<String, ModelResourceLocation> indicators;
    private final Map<String, AABB> renderBounds;

    private PlantModelManifest(Map<String, Map<String, ModelResourceLocation>> plants, Map<String, ModelResourceLocation> indicators, Map<String, AABB> renderBounds) { this.plants = Map.copyOf(plants); this.indicators=Map.copyOf(indicators); this.renderBounds = Map.copyOf(renderBounds); }

    public static PlantModelManifest load() {
        try (var stream = PlantModelManifest.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing packaged " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load plant model manifest", exception);
        }
    }

    /** Parses version-1 manifests, including optional local-block-unit render envelopes. */
    static PlantModelManifest parse(JsonObject root) {
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
            Map<String, AABB> renderBounds = new LinkedHashMap<>();
            if (root.has("render_bounds")) {
                JsonObject bounds = root.getAsJsonObject("render_bounds");
                for (var entry : bounds.entrySet()) {
                    if (!plants.containsKey(entry.getKey())) throw new IllegalStateException("Unknown plant render_bounds key " + entry.getKey());
                    var values = entry.getValue().getAsJsonArray();
                    if (values.size() != 6) throw new IllegalStateException("render_bounds must contain six coordinates for " + entry.getKey());
                    double[] c = new double[6];
                    for (int i = 0; i < c.length; i++) {
                        c[i] = values.get(i).getAsDouble();
                        if (!Double.isFinite(c[i])) throw new IllegalStateException("Non-finite plant render bound for " + entry.getKey());
                    }
                    if (c[0] > c[3] || c[1] > c[4] || c[2] > c[5]) throw new IllegalStateException("Reversed plant render bounds for " + entry.getKey());
                    renderBounds.put(entry.getKey(), new AABB(c[0], c[1], c[2], c[3], c[4], c[5]));
                }
            }
            return new PlantModelManifest(plants, indicators, renderBounds);
    }

    public Map<String, Map<String, ModelResourceLocation>> plants() { return plants; }
    public Map<String, ModelResourceLocation> indicators() { return indicators; }
    public AABB renderBounds(String plant) { return renderBounds.get(plant); }
    public AABB effectiveRenderBounds(String plant) {
        return renderBounds(plant);
    }
    /** Resolves the world-space renderer envelope, retaining the legacy tray box for empty or unbounded appearances. */
    public AABB renderBoundingBox(String visualKey, boolean occupied, BlockPos pos) {
        AABB legacy = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        if (!occupied) return legacy;
        AABB local = effectiveRenderBounds(visualKey);
        return local == null ? legacy : legacy.minmax(local.move(pos.getX(), pos.getY(), pos.getZ()));
    }
    public ModelResourceLocation model(String plant, String state) {
        Map<String, ModelResourceLocation> states = plants.get(plant);
        return states == null ? null : states.get(state);
    }
    /** Shared projection from the atomically published tray snapshot to its authored model ID. */
    public ModelResourceLocation model(com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity.AppearanceSnapshot appearance) {
        if (appearance == null || appearance.status() == com.juicyslew.moonstation14.ms14.botany.PlantInstance.Status.EMPTY) return null;
        String state = switch (appearance.status()) {
            case EMPTY -> null;
            case DEAD -> "dead";
            case HARVEST -> "harvest";
            case GROWING -> "stage-" + appearance.stage();
        };
        return state == null ? null : model(appearance.key(), state);
    }
}
