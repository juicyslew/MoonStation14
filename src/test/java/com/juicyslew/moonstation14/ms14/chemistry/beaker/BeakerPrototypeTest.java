package com.juicyslew.moonstation14.ms14.chemistry.beaker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.item.custom.BeakerItem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BeakerPrototypeTest {
    @Test void emptyModelsHaveOnlyBareBaseAndFilledModelsRetainLiquidLayer() {
        assertModelFamily("beaker", "moonstation14:item/beaker/beaker", "moonstation14:item/beaker/beaker1");
        assertModelFamily("beaker_large", "moonstation14:item/beaker_large/beakerlarge", "moonstation14:item/beaker_large/beakerlarge1");
    }

    private static void assertModelFamily(String base, String baseTexture, String liquidTexture) {
        JsonObject empty = model(base + ".json");
        assertEquals("minecraft:item/generated", empty.get("parent").getAsString());
        assertEquals(baseTexture, empty.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals(1, empty.getAsJsonObject("textures").size(), "empty model must not bake the liquid layer");
        assertEquals(6, empty.getAsJsonArray("overrides").size());
        for (int tier = 1; tier <= 6; tier++) {
            JsonObject variant = model(base + "_" + tier + ".json");
            assertEquals("minecraft:item/generated", variant.get("parent").getAsString());
            assertEquals(baseTexture, variant.getAsJsonObject("textures").get("layer0").getAsString());
            assertEquals(liquidTexture.substring(0, liquidTexture.length() - 1) + tier,
                    variant.getAsJsonObject("textures").get("layer1").getAsString());
        }
    }

    private static JsonObject model(String name) {
        try (var stream = BeakerPrototypeTest.class.getResourceAsStream(
                "/assets/moonstation14/models/item/" + name)) {
            assertNotNull(stream, "missing registered beaker model " + name);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new AssertionError("could not read model " + name, e);
        }
    }

    @Test void inheritedPolicyAndChildCapacityOverrideResolve() {
        var catalog = resolve(
                entry("base", "{\"solutionContainer\":{\"solutionName\":\"beaker\",\"maxVolume\":50},\"fitsInDispenser\":false,\"examinableSolution\":{\"enabled\":true,\"solutionName\":\"beaker\",\"exactVolume\":true},\"drainableSolution\":{\"enabled\":true,\"solutionName\":\"beaker\"}}"),
                entry("standard", "{\"parent\":\"base\"}"),
                entry("large", "{\"parent\":\"base\",\"solutionContainer\":{\"maxVolume\":100}}"),
                entry("approx", "{\"parent\":\"base\",\"examinableSolution\":{\"exactVolume\":false}}"),
                entry("drain-disabled", "{\"parent\":\"base\",\"drainableSolution\":{\"enabled\":false}}"));
        assertEquals(50, catalog.get(id("standard")).solutionContainer().maxVolume());
        assertTrue(catalog.get(id("standard")).drainableSolution().enabled());
        assertEquals("beaker", catalog.get(id("standard")).drainableSolution().solutionName());
        assertEquals("beaker", BeakerItem.drainableSolutionFromResolved(catalog.get(id("standard"))).solutionName());
        assertEquals("beaker", catalog.get(id("large")).drainableSolution().solutionName());
        assertFalse(catalog.get(id("drain-disabled")).drainableSolution().enabled());
        assertEquals("full", com.juicyslew.moonstation14.ms14.examine.SolutionExamineFormatter.volumeCategory(5000,
                catalog.get(id("standard")).solutionContainer().maxVolume()));
        assertEquals("half-full", com.juicyslew.moonstation14.ms14.examine.SolutionExamineFormatter.volumeCategory(5000,
                catalog.get(id("large")).solutionContainer().maxVolume()));
        assertFalse(catalog.get(id("standard")).fitsInDispenser());
        assertTrue(catalog.get(id("standard")).examinableSolution().enabled());
        assertTrue(catalog.get(id("standard")).examinableSolution().exactVolume());
        assertFalse(catalog.get(id("standard")).examinableSolution().heldOnly());
        assertTrue(catalog.get(id("standard")).examinableSolution().examinableWhileClosed());
        assertTrue(catalog.get(id("approx")).examinableSolution().enabled());
        assertEquals("beaker", catalog.get(id("approx")).examinableSolution().solutionName());
        assertFalse(catalog.get(id("approx")).examinableSolution().exactVolume());
        assertEquals(50f, BeakerItem.capacityFromResolved(catalog.get(id("standard"))));
        assertFalse(BeakerItem.fitsInDispenserFromResolved(catalog.get(id("standard"))));
        assertEquals(100, catalog.get(id("large")).solutionContainer().maxVolume());
        assertFalse(catalog.get(id("large")).fitsInDispenser());
        assertEquals(100f, BeakerItem.capacityFromResolved(catalog.get(id("large"))));
        assertEquals(50, catalog.get(id("standard")).solutionContainer().maxVolume());
        var optOut = resolve(entry("optout", "{\"examinableSolution\":{\"enabled\":false}}"));
        assertFalse(optOut.get(id("optout")).examinableSolution().enabled());
        var noExamine = resolve(entry("no-examine", "{}"));
        assertFalse(noExamine.get(id("no-examine")).examinableSolution().enabled());
        var standalone = resolve(entry("standalone", "{\"examinableSolution\":{\"enabled\":true}}"));
        assertTrue(standalone.get(id("standalone")).examinableSolution().enabled());
        assertFalse(standalone.get(id("standalone")).examinableSolution().exactVolume());
        assertFalse(standalone.get(id("standalone")).examinableSolution().heldOnly());
        assertTrue(standalone.get(id("standalone")).examinableSolution().examinableWhileClosed());
    }

    @Test void visualPolicyInheritsAndLargeOverridesAssetStatesAndCapacity() {
        var catalog = resolve(entry("base", "{\"solutionContainer\":{\"solutionName\":\"beaker\",\"maxVolume\":50},\"solutionVisuals\":{\"solutionName\":\"beaker\",\"baseState\":\"beaker\",\"fillStatePrefix\":\"beaker\"}}"),
                entry("standard", "{\"parent\":\"base\"}"),
                entry("large", "{\"parent\":\"base\",\"solutionContainer\":{\"maxVolume\":100},\"solutionVisuals\":{\"baseState\":\"beakerlarge\",\"fillStatePrefix\":\"beakerlarge\"}}"),
                entry("mismatch", "{\"parent\":\"base\",\"solutionContainer\":{\"solutionName\":\"other\"}}"),
                entry("unknown", "{\"parent\":\"base\",\"solutionContainer\":{\"solutionName\":\"other\"},\"solutionVisuals\":{\"solutionName\":\"other\"}}"),
                entry("no-policy", "{}"),
                entry("no-tint", "{\"parent\":\"base\",\"solutionVisuals\":{\"changeColor\":false}}"));
        var standard = catalog.get(id("standard"));
        var large = catalog.get(id("large"));
        assertEquals("beaker", standard.solutionVisuals().solutionName());
        assertEquals("beaker", standard.solutionVisuals().baseState());
        assertEquals("beakerlarge", large.solutionVisuals().baseState());
        assertEquals("beaker", large.solutionVisuals().solutionName());
        assertFalse(com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.supported(catalog.get(id("mismatch"))));
        assertFalse(com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.supported(catalog.get(id("unknown"))));
        assertFalse(com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.supported(catalog.get(id("no-policy"))));
        assertFalse(catalog.get(id("no-tint")).solutionVisuals().changeColor());
        var smallEmpty = new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent();
        assertEquals(0, com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.fillTier(smallEmpty, standard));
        assertEquals(-1, com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.color(smallEmpty, standard, null));
        assertEquals(-1, com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.color(
                smallEmpty, catalog.get(id("no-tint")), null));
        assertEquals(0, com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.fillTier(
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(
                        net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                                ResourceLocation.fromNamespaceAndPath("moonstation14", "water")), 1000L)), catalog.get(id("unknown"))));
        assertTier(standard, 1, 0.01f); assertTier(standard, 1, 10f); assertTier(standard, 2, 10.01f);
        assertTier(standard, 5, 49.99f); assertTier(standard, 6, 50f); assertTier(standard, 6, 75f);
        assertTier(large, 1, 0.01f); assertTier(large, 1, 20f); assertTier(large, 2, 20.01f);
        assertTier(large, 5, 99.99f); assertTier(large, 6, 100f); assertTier(large, 6, 150f);
    }

    private static void assertTier(BeakerData data, int expected, float units) {
        var key = net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
        var reagent = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(key, (long) Math.round(units * 100f)));
        assertEquals(expected, com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.fillTier(reagent, data));
    }

    @SafeVarargs private static PrototypeCatalog<BeakerData> resolve(Map.Entry<String,String>... entries) {
        Map<ResourceLocation,JsonObject> raw = new LinkedHashMap<>();
        for (var entry : entries) raw.put(id(entry.getKey()), JsonParser.parseString(entry.getValue()).getAsJsonObject());
        return decode(raw);
    }
    private static PrototypeCatalog<BeakerData> decode(Map<ResourceLocation,JsonObject> raw) {
        var resolved = PrototypeResolver.resolve(raw, new BeakerMergeStrategy());
        Map<ResourceLocation,BeakerData> values = new LinkedHashMap<>();
        resolved.asMap().forEach((key,json) -> values.put(key, BeakerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow()));
        return new PrototypeCatalog<>(values);
    }
    private static Map.Entry<String,String> entry(String id,String json) { return Map.entry(id,json); }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14",value); }
}
