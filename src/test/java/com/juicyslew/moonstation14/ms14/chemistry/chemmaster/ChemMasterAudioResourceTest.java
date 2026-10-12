package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterAudioResourceTest {
    @Test void allChemMasterEventsResolveToExistingSamples() throws Exception {
        try (var in = new InputStreamReader(getClass().getResourceAsStream("/assets/moonstation14/sounds.json"), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(in).getAsJsonObject();
            assertTrue(json.has("chemistry/chemmaster/click"));
            assertTrue(json.has("chemistry/chemmaster/insert"));
            assertTrue(json.has("chemistry/chemmaster/eject"));
            assertNotNull(getClass().getResource("/assets/moonstation14/sounds/apc/machine_switch.ogg"));
            assertNotNull(getClass().getResource("/assets/moonstation14/sounds/chemistry/chemmaster/revolver_magin.ogg"));
            assertNotNull(getClass().getResource("/assets/moonstation14/sounds/chemistry/chemmaster/revolver_magout.ogg"));
        }
    }
}
