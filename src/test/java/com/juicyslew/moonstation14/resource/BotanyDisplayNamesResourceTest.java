package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BotanyDisplayNamesResourceTest {
    @Test void registeredBotanyItemsHaveDisplayNames() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/moonstation14/lang/en_us.json")) {
            assertNotNull(stream);
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : new String[]{"mimana", "trash_mimana_peel", "trash_banana_peel", "golden_apple",
                    "lime", "lemoon", "lemon", "orange", "extradimensional_orange", "cabbage", "carrot", "banana", "apple",
                    "carrots_seeds", "cabbage_seeds", "lemon_seeds", "lemoon_seeds", "lime_seeds", "orange_seeds",
                    "extradimensional_orange_seeds", "apple_seeds", "golden_apple_seeds", "banana_seeds", "mimana_seeds"}) {
                String key = "item.moonstation14." + id;
                assertTrue(lang.has(key), key);
                String value = lang.get(key).getAsString();
                assertFalse(value.isBlank(), key);
                assertNotEquals(id, value, key);
            }
        }
    }
}
