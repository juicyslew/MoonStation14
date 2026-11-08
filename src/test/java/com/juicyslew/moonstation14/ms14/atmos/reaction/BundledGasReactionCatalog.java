package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Test-only fully resolved catalog loaded from the bundled reaction prototypes. */
public final class BundledGasReactionCatalog {
    private static final java.util.List<String> NAMES = java.util.List.of("frezon_production", "ammonia_oxygen", "frezon_coolant",
            "n2o_decomposition", "tritium_fire", "plasma_fire");

    private BundledGasReactionCatalog() { }

    public static java.util.List<String> names() { return NAMES; }

    public static PrototypeCatalog<GasReactionData> load() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        for (String name : NAMES) {
            String path = "/data/moonstation14/moonstation14/gas_reaction/" + name + ".json";
            try (var stream = BundledGasReactionCatalog.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                definitions.put(ResourceLocation.parse("moonstation14:" + name),
                        JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, definitions);
        return manager.snapshot(ModGasReactions.GAS_REACTION_TYPE);
    }
}
