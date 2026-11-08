package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GhostRoleMouseTranslationTest {
    @Test void mouseEntityTranslationExistsInPackagedLanguageResource() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/moonstation14/lang/en_us.json")) {
            assertNotNull(stream, "Missing English language resource");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("Mouse", json.get("entity.moonstation14.mouse").getAsString());
        }
    }
}
