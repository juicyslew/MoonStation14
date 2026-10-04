package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallMountBlockstateOrientationTest {
    private static final String ASSETS = "/assets/moonstation14/";
    private static final List<String> BLOCKS = List.of("gas_vent", "gas_scrubber", "air_alarm");

    @Test
    void allWallMountVariantsHaveCorrectVerticalRotationAndThinPlateBounds() {
        for (String block : BLOCKS) {
            JsonObject variants = read(ASSETS + "blockstates/" + block + ".json").getAsJsonObject("variants");
            assertEquals(0, variants.getAsJsonObject("facing=north").has("y")
                    ? variants.getAsJsonObject("facing=north").get("y").getAsInt() : 0);
            assertEquals(90, variants.getAsJsonObject("facing=east").get("y").getAsInt());
            assertEquals(180, variants.getAsJsonObject("facing=south").get("y").getAsInt());
            assertEquals(270, variants.getAsJsonObject("facing=west").get("y").getAsInt());

            JsonObject model = read(ASSETS + "models/block/" + block + ".json");
            JsonObject faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
            assertEquals(6, faces.size());
            assertTrue(faces.has("north") && faces.has("south") && faces.has("east")
                    && faces.has("west") && faces.has("up") && faces.has("down"));

            JsonObject from = model.getAsJsonArray("elements").get(0).getAsJsonObject();
            int zMin = from.getAsJsonArray("from").get(2).getAsInt();
            int zMax = from.getAsJsonArray("to").get(2).getAsInt();
            double depth = (zMax - zMin) / 16.0;
            // MinecraftBlockModelRotation negates X: x=270 rotates the north-facing
            // plate onto UP; x=90 rotates it onto DOWN. Compare bounds with runtime's
            // UP [0, depth] and DOWN [1-depth, 1] mounting planes.
            assertEquals(270, variants.getAsJsonObject("facing=up").get("x").getAsInt());
            assertEquals(90, variants.getAsJsonObject("facing=down").get("x").getAsInt());
            double upMin = 1.0 - zMax / 16.0;
            double upMax = 1.0 - zMin / 16.0;
            double downMin = zMin / 16.0;
            double downMax = zMax / 16.0;
            assertEquals(0.0, upMin, 1e-9);
            assertEquals(depth, upMax, 1e-9);
            assertEquals(1.0 - depth, downMin, 1e-9);
            assertEquals(1.0, downMax, 1e-9);
        }
    }

    private static JsonObject read(String path) {
        try (var stream = WallMountBlockstateOrientationTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
