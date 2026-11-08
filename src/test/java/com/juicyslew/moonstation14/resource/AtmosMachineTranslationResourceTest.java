package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineTranslationResourceTest {
    private static final Map<String, String> MACHINES = Map.ofEntries(
            Map.entry("teg_center", "Thermo-Electric Generator"),
            Map.entry("teg_circulator", "Circulator"),
            Map.entry("gas_pressure_pump", "Gas Pump"),
            Map.entry("gas_volume_pump", "Volumetric Gas Pump"),
            Map.entry("gas_mixer", "Gas Mixer"),
            Map.entry("gas_filter", "Gas Filter"),
            Map.entry("gas_valve", "Manual Valve"),
            Map.entry("gas_thermo_heater", "Heater"),
            Map.entry("gas_thermo_freezer", "Freezer"),
            Map.entry("passive_heat_exchanger", "Radiator"));

    @Test
    void everyAtmosMachineBlockAndItemHasReadableTranslation() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/lang/en_us.json")) {
            assertNotNull(stream);
            JsonObject translations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            MACHINES.forEach((id, name) -> {
                for (String kind : new String[]{"block", "item"}) {
                    String key = kind + ".moonstation14." + id;
                    assertTrue(translations.has(key), "missing " + key);
                    assertEquals(name, translations.get(key).getAsString(), "wrong name for " + key);
                    assertNotEquals(id, translations.get(key).getAsString());
                }
            });
        }
    }
}
