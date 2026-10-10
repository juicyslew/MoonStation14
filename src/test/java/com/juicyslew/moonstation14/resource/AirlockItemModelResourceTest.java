package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirlockItemModelResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void airlockItemUsesAcceptedClosedPoseCandidateAndItsDisplayTransforms() {
        JsonObject item = read("models/item/airlock.json");
        assertEquals("moonstation14:item/airlock_vertical_standard_basic", item.get("parent").getAsString());
        JsonObject candidate = read("models/item/airlock_vertical_standard_basic.json");
        assertEquals("moonstation14:block/airlock/vertical_all/standard_basic/poses/closed_opaque",
                candidate.get("parent").getAsString());
        JsonObject block = read("models/block/airlock/vertical_all/standard_basic/poses/closed_opaque.json");
        assertTrue(block.has("display"), "inventory/held rendering should reuse authored transforms");
        assertFalse(block.getAsJsonArray("elements").isEmpty());
    }

    @Test
    void onlyAuthoredStatusLampElementsAreUnshaded() {
        for (String component : new String[]{"fixed", "header_lamps", "upper_leaf", "lower_leaf"}) {
            var elements = read("models/block/airlock/vertical_all/standard_basic/components/" + component + ".json")
                    .getAsJsonArray("elements");
            var unshaded = new java.util.ArrayList<String>();
            for (var element : elements) {
                var part = element.getAsJsonObject();
                if (part.has("shade") && !part.get("shade").getAsBoolean())
                    unshaded.add(part.get("name").getAsString());
            }
            assertEquals(component.equals("header_lamps")
                    ? java.util.List.of("lamp_0_front", "lamp_0_back", "lamp_1_front", "lamp_1_back")
                    : java.util.List.of(), unshaded);
        }
    }

    private static JsonObject read(String resource) {
        try (var stream = AirlockItemModelResourceTest.class.getResourceAsStream(ROOT + resource)) {
            if (stream == null) throw new AssertionError("Missing resource " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Unable to read " + resource, exception);
        }
    }
}
