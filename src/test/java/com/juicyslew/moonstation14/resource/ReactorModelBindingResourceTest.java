package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReactorModelBindingResourceTest {
    @Test void everyEndCapFacingAndVisualStateUsesTheAvailableStaticCompletedModel() throws Exception {
        var blockstate = json("/assets/moonstation14/blockstates/pa_end_cap.json");
        var variants = blockstate.getAsJsonObject("variants");
        for (String facing : new String[]{"north", "east", "south", "west"})
            for (String visual : new String[]{"default", "completed", "unlitp", "unlitp0", "unlitp1", "unlitp2", "unlitp3"}) {
                var variant = variants.getAsJsonObject("facing=" + facing + ",visual=" + visual);
                assertNotNull(variant, facing + "/" + visual);
                assertEquals("moonstation14:block/reactor_bindings/pa_end_cap/completed", variant.get("model").getAsString());
                assertTrue(exists("/assets/moonstation14/models/block/reactor_bindings/pa_end_cap/completed.json"));
            }
        assertEquals(28, variants.size());
    }

    @Test void heldRadiationEquipmentTranslationKeysHaveEnglishLabels() throws Exception {
        var language = json("/assets/moonstation14/lang/en_us.json");
        assertEquals("Radiation Suit", language.get("item.moonstation14.suit_rad").getAsString());
        assertEquals("Radiation Hood", language.get("item.moonstation14.hood_rad").getAsString());
    }

    @Test void canisterProxyItemModelResolvesToAnImportedModel() throws Exception {
        var item = json("/assets/moonstation14/models/item/gas_canister_proxy.json");
        assertEquals("moonstation14:item/plasma_tank", item.get("parent").getAsString());
        assertTrue(exists("/assets/moonstation14/models/item/plasma_tank.json"));
    }

    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = ReactorModelBindingResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static boolean exists(String path) {
        return ReactorModelBindingResourceTest.class.getResource(path) != null;
    }
}
