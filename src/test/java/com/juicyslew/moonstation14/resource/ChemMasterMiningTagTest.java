package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemMasterMiningTagTest {
    @Test
    void pickaxeTagAddsChemMasterWithoutReplacingExistingEntries() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/minecraft/tags/block/mineable/pickaxe.json")) {
            assertTrue(stream != null, "Missing pickaxe mining tag");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean(), "Mod tag must extend the vanilla tag");
            var values = json.getAsJsonArray("values").asList().stream()
                    .map(element -> element.getAsString()).toList();
            assertTrue(values.contains("moonstation14:chem_master"));
            assertTrue(values.contains("moonstation14:steel_wall_block"));
            assertTrue(values.contains("moonstation14:steel_wall_girder_block"));
        }
    }
}
