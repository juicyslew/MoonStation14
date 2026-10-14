package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReactorLabelsResourceTest {
    @Test void registeredSingularityNameUsedByShiftExamineHasEnglishTranslation() throws Exception {
        // ExamineClient's candidate preview uses Entity#getName(), which resolves
        // this registered type description key through the client's language table.
        String key = "entity.moonstation14.singularity";
        try (var in = getClass().getResourceAsStream("/assets/moonstation14/lang/en_us.json")) {
            assertNotNull(in);
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(lang.has(key), "missing registered singularity entity name translation");
            assertEquals("Singularity", lang.get(key).getAsString());
        }
    }

    @Test void reactorBlocksAndApcBreakerHaveReadableEnglishLabels() throws Exception {
        try (var in = getClass().getResourceAsStream("/assets/moonstation14/lang/en_us.json")) {
            assertNotNull(in);
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : new String[]{"block.moonstation14.pa_control_box", "block.moonstation14.pa_end_cap",
                    "block.moonstation14.pa_fuel_chamber", "block.moonstation14.pa_power_box",
                    "block.moonstation14.pa_emitter_port", "block.moonstation14.pa_emitter_fore",
                    "block.moonstation14.pa_emitter_starboard", "block.moonstation14.emitter",
                    "block.moonstation14.field_generator", "block.moonstation14.radiation_collector",
                    "block.moonstation14.singularity_generator", "gui.moonstation14.apc.breaker_on",
                    "gui.moonstation14.apc.breaker_off", "gui.moonstation14.apc.overload_tripped"}) {
                assertTrue(lang.has(key), "missing English translation " + key);
                assertFalse(lang.get(key).getAsString().isBlank(), "blank English translation " + key);
            }
            assertEquals("On", lang.get("gui.moonstation14.apc.breaker_on").getAsString());
            assertEquals("Off", lang.get("gui.moonstation14.apc.breaker_off").getAsString());
            assertEquals("Overload tripped", lang.get("gui.moonstation14.apc.overload_tripped").getAsString());
        }
    }
}
