package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtmosManifoldRendererTest {
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");
    private static final List<String> ENDS = List.of("north", "south");
    private static final List<String> PORTS = List.of("primary", "secondary", "tertiary");

    @Test void everyMaskSelectsExactlyItsFacingSpecificImportedModels() {
        assertEquals(24, AtmosManifoldRenderer.additionalModels().size());
        assertEquals(24, AtmosManifoldRenderer.additionalModels().stream().distinct().count());
        for (String facing : FACINGS) {
            Direction direction = Direction.byName(facing);
            assertResource("/assets/moonstation14/models/block/atmos_manifold/" + facing + "_base.json");
            for (int mask = 0; mask < 64; mask++) {
                var selected = AtmosManifoldRenderer.connectedModels(mask, direction);
                assertEquals(Integer.bitCount(mask), selected.size(), facing + " mask " + mask);
                for (int bit = 0; bit < 6; bit++) {
                    String file = facing + "_" + ENDS.get(bit / 3) + "_" + PORTS.get(bit % 3) + ".json";
                    var expected = net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "block/atmos_manifold/" + file.substring(0, file.length() - 5)));
                    boolean present = selected.contains(expected);
                    assertEquals((mask & (1 << bit)) != 0, present, file + " mask " + mask);
                    assertResource("/assets/moonstation14/models/block/atmos_manifold/" + file);
                }
            }
            assertTrue(AtmosManifoldRenderer.connectedModels(0, direction).isEmpty());
            assertEquals(6, AtmosManifoldRenderer.connectedModels(63, direction).size());
        }
    }

    @Test void baseBlockstateSelectsImportedFacingModelsWithoutExtraRotation() {
        var parts = read("/assets/moonstation14/blockstates/gas_pipe_manifold.json").getAsJsonArray("multipart");
        for (String facing : FACINGS) {
            var part = parts.asList().stream().map(e -> e.getAsJsonObject()).filter(e -> e.getAsJsonObject("when").get("facing").getAsString().equals(facing)).findFirst().orElseThrow();
            var variant = part.getAsJsonObject("apply");
            assertEquals("moonstation14:block/atmos_manifold/" + facing + "_base", variant.get("model").getAsString());
            assertFalse(variant.has("y"));
            assertNotNull(getClass().getResourceAsStream("/assets/moonstation14/models/block/atmos_manifold/" + facing + "_base.json"));
        }
    }

    private static com.google.gson.JsonObject read(String path) {
        try (var stream = AtmosManifoldRendererTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError("Unable to read " + path, e); }
    }

    private static void assertResource(String path) {
        try (var stream = AtmosManifoldRendererTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing manifold model resource " + path);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read manifold model resource " + path, exception);
        }
    }
}
