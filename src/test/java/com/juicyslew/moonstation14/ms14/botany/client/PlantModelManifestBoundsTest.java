package com.juicyslew.moonstation14.ms14.botany.client;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlantModelManifestBoundsTest {
    private static final String JSON = "{\"formatVersion\":1,\"plants\":{\"x\":{\"stage-1\":\"moonstation14:block/botany/plants/x/stage-1\"}},\"render_bounds\":%s}";
    private static PlantModelManifest parse(String bounds) {
        return PlantModelManifest.parse(JsonParser.parseString(JSON.formatted(bounds)).getAsJsonObject());
    }

    @Test void loadedCatalogPreservesLegacyAppearancesAndDefault() {
        var manifest = PlantModelManifest.load();
        assertEquals(69, manifest.plants().size());
        for (String legacy : new String[]{"wheat", "tomato", "potato", "poppy", "aloe", "meatwheat"})
            assertTrue(manifest.plants().containsKey(legacy), legacy);
        var expectedModel = net.minecraft.resources.ResourceLocation.parse("moonstation14:block/botany/plants/wheat/stage-1");
        var actualModel = manifest.model("wheat", "stage-1");
        assertEquals(expectedModel, actualModel.id());
        assertEquals(net.minecraft.client.resources.model.ModelResourceLocation.standalone(expectedModel), actualModel);
        assertNull(manifest.renderBounds("wheat"));
    }

    @Test void acceptsSyntheticOversizedLocalEnvelopeAndUnionsThenTranslatesOnce() {
        var manifest = parse("{\"x\":[-0.26,0.02,-0.19,1.26,1.99,1.20]}");
        assertEquals(new AABB(9.74, 20, 29.81, 11.26, 22, 31.20),
                manifest.renderBoundingBox("x", true, new BlockPos(10, 20, 30)));
    }

    @Test void missingEnvelopeAndEmptyStaleAppearanceRetainLegacyWorldBox() {
        var manifest = parse("{}");
        var legacy = new AABB(10, 20, 30, 11, 22, 31);
        assertEquals(legacy, manifest.renderBoundingBox("x", true, new BlockPos(10, 20, 30)));
        var bounded = parse("{\"x\":[-0.26,0.02,-0.19,1.26,1.99,1.20]}");
        assertEquals(legacy, bounded.renderBoundingBox("x", false, new BlockPos(10, 20, 30)));
    }

    @Test void rejectsBadAndUnknownBounds() {
        for (String bounds : new String[]{"{\"x\":[0,0,0,1,1]}", "{\"x\":[0,0,0,1,1,\"NaN\"]}",
                "{\"x\":[1,0,0,0,1,1]}", "{\"missing\":[0,0,0,1,1,1]}"})
            assertThrows(RuntimeException.class, () -> parse(bounds), bounds);
    }

    @Test void catalogUsesAuthoredBoundsWithoutRuntimeScaling() {
        var manifest = PlantModelManifest.load();
        for (String key : new String[]{"apple","golden_apple","lemon","lemoon","lime","orange","extradimensional_orange","banana","mimana"}) {
            assertEquals(manifest.renderBounds(key), manifest.effectiveRenderBounds(key));
        }
        var root = JsonParser.parseString(readManifest()).getAsJsonObject();
        assertFalse(root.has("height_scales"));
    }

    private static String readManifest() {
        try (var input = PlantModelManifestBoundsTest.class.getResourceAsStream("/assets/moonstation14/botany/plant-models.json")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
}
