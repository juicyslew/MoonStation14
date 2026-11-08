package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceAppearance;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VentilationDeviceModelResourceTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final String[] FACINGS = {"north", "east", "south", "west", "up", "down"};
    private static final Map<String, String> VENT = Map.of("0", "vent_off", "1", "vent_out", "2", "vent_off", "3", "vent_off");
    private static final Map<String, String> SCRUBBER = Map.of("0", "scrub_off", "1", "scrub_on", "2", "scrub_on", "3", "scrub_purge");

    @Test void allVentilationFacingDisplayLayerVariantsResolveToExistingExpectedModels() {
        assertEquals(0, VentilationDeviceAppearance.display("off"));
        assertEquals(1, VentilationDeviceAppearance.display("vent_out"));
        assertEquals(2, VentilationDeviceAppearance.display("scrub_on"));
        assertEquals(3, VentilationDeviceAppearance.display("scrub_purge"));
        for (String block : new String[]{"gas_vent", "gas_scrubber"}) {
            var variants = read("blockstates/" + block + ".json").getAsJsonObject("variants");
            var states = block.equals("gas_vent") ? VENT : SCRUBBER;
            assertEquals(72, variants.size(), block + " selector count");
            for (String facing : FACINGS) for (int display = 0; display < 4; display++) for (int layer = 0; layer < 3; layer++) {
                String key = "facing=" + facing + ",display=" + display + ",pipe_layer=" + layer;
                var selector = variants.getAsJsonObject(key);
                assertNotNull(selector, block + " " + key);
                String expected = "block/atmos_interfaces/" + block + "/" + states.get("" + display)
                        + "_l" + layer + "_" + facing;
                String id = selector.get("model").getAsString();
                assertEquals("moonstation14:" + expected, id, key);
                assertNotNull(resource("models/" + expected + ".json"), "Missing model " + expected);
                assertFalse(selector.has("y"), key + " extra rotation");
            }
        }
    }

    private static com.google.gson.JsonObject read(String path) {
        try (var stream = resource(path)) {
            assertNotNull(stream, "Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError("Unable to read " + path, e); }
    }

    private static java.io.InputStream resource(String path) {
        return VentilationDeviceModelResourceTest.class.getResourceAsStream(ROOT + path);
    }
}
