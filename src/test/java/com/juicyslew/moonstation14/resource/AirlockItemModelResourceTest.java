package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirlockItemModelResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void itemUsesExportedDefaultBlockModelAndItsDisplayTransforms() {
        JsonObject item = read("models/item/airlock.json");
        assertEquals("moonstation14:block/airlock/default/closed", item.get("parent").getAsString());
        JsonObject block = read("models/block/airlock/default/closed.json");
        assertTrue(block.has("display"), "inventory/held rendering should reuse authored transforms");
        assertEquals(18, block.getAsJsonArray("elements").size());
    }

    @Test
    void onlyAuthoredStatusLampElementsAreUnshaded() {
        for (String component : new String[]{"leaf_left", "leaf_right"}) {
            var elements = read("models/block/airlock/default/components/" + component + ".json")
                    .getAsJsonArray("elements");
            var unshaded = new java.util.ArrayList<String>();
            for (var element : elements) {
                var part = element.getAsJsonObject();
                if (part.has("shade") && !part.get("shade").getAsBoolean())
                    unshaded.add(part.get("name").getAsString());
            }
            assertEquals(component.equals("leaf_left") ? java.util.List.of("status_left_front", "status_left_back")
                    : java.util.List.of("status_right_front", "status_right_back"), unshaded);
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
