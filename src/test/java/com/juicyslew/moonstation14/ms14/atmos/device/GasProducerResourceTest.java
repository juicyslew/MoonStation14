package com.juicyslew.moonstation14.ms14.atmos.device;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasProducerResourceTest {
    private static final String ROOT = "assets/moonstation14/";

    @Test
    void eachGasHasProducerBlockstateModelsAndLocalizedName() throws IOException {
        JsonObject language = json("lang/en_us.json");
        Set<String> gasIds = java.util.Arrays.stream(GasType.values()).map(GasType::id).collect(Collectors.toSet());
        assertEquals(9, gasIds.size());

        for (GasType gas : GasType.values()) {
            String id = "atmos_" + gas.id() + "_producer";
            JsonObject state = json("blockstates/" + id + ".json");
            assertEquals(4, state.getAsJsonObject("variants").size(), id);
            assertEquals("moonstation14:block/" + id,
                    state.getAsJsonObject("variants").getAsJsonObject("facing=north").get("model").getAsString(), id);
            assertEquals("minecraft:block/cube_all", json("models/block/" + id + ".json").get("parent").getAsString(), id);
            assertEquals("moonstation14:block/" + id, json("models/item/" + id + ".json").get("parent").getAsString(), id);
            assertTrue(language.has("block.moonstation14." + id), "Missing label for " + id);
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (var stream = GasProducerResourceTest.class.getClassLoader().getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new IOException("Missing atmosphere producer resource " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
