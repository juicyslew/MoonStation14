package com.juicyslew.moonstation14.ms14.construction.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionHudLanguageTest {
    @Test void everySharedHudKeyHasEnglishText() throws Exception {
        try (var reader = new InputStreamReader(ConstructionHudLanguageTest.class.getResourceAsStream(
                "/assets/moonstation14/lang/en_us.json"), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
        for(String key:Set.of("moonstation14.construction.guidance.title","moonstation14.construction.hud.ready",
                "moonstation14.construction.hud.hold","moonstation14.construction.hud.updating",
                "moonstation14.construction.hud.insufficient","moonstation14.construction.hud.blocked",
                "moonstation14.construction.hud.palette","moonstation14.construction.hud.palette_controls",
                "moonstation14.construction.hud.multiple_materials"))
            assertTrue(json.has(key),"missing en_us key "+key);
        }
    }
}
