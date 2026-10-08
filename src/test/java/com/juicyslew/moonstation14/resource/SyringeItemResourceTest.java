package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyringeItemResourceTest {
    @Test void syringeModelAndCopiedSourceMetadataExist() throws Exception {
        try (var model = getClass().getResourceAsStream("/assets/moonstation14/models/item/syringe.json");
             var metadata = getClass().getResourceAsStream("/assets/moonstation14/textures/item/syringe/meta.json");
             var footprint = getClass().getResourceAsStream("/data/moonstation14/moonstation14/item_footprint/syringe.json")) {
            assertNotNull(model);
            assertNotNull(metadata);
            assertNotNull(footprint);
            var predicates = JsonParser.parseString(new String(model.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("overrides");
            assertTrue(predicates.toString().contains("moonstation14:fill"));
            assertTrue(predicates.toString().contains("moonstation14:syringe_mode"));
            var sourceMeta = JsonParser.parseString(new String(metadata.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("CC-BY-SA-3.0", sourceMeta.get("license").getAsString());
            assertTrue(sourceMeta.get("copyright").getAsString().contains("CEV-Eris"));
            assertEquals("moonstation14:tiny", JsonParser.parseString(new String(footprint.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                    .get("parent").getAsString());
        }
    }
}
