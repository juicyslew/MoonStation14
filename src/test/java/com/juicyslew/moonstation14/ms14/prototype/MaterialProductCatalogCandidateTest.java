package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static JsonObject read(String family, String name) {
        String path = "botany-catalog-candidate/" + family + "/" + name + ".json";
        try (var stream = MaterialProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    @Test void materialProduceAndFoodSourcesResolveWithInheritedDefaults() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produced = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var foods = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extraction = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "cotton_bol", "pyrotton_bol"));
        manager.stage(Map.of(ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "papercane", "log", "steel_log", "cotton_bol", "pyrotton_bol"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "cotton_bol", "pyrotton_bol"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "papercane", "log", "steel_log", "cotton_bol", "pyrotton_bol")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produced.put(key,
                    ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> foods.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extraction.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot(), "staging must not publish candidate catalogs");
        assertEquals(5, produced.size());
        assertEquals(2, foods.size());
        for (String name : new String[]{"papercane", "log", "steel_log", "cotton_bol", "pyrotton_bol"}) {
            var profile = produced.get(id(name));
            assertNotNull(profile);
            assertEquals("food", extraction.get(id(name)).grindableSolutionName(), "grindability is an extractable capability, not fresh source presence");
            assertEquals(1f, profile.potencyVisuals().minimumScale());
            assertEquals(2f, profile.potencyVisuals().maximumScale());
            assertNull(extraction.get(id(name)).juiceSolution(), "none of these source entities has a configured juice channel");
        }
        assertEquals(id("papercane"), produced.get(id("papercane")).seed());
        assertEquals(id("towercap"), produced.get(id("log")).seed());
        assertEquals(id("steelcap"), produced.get(id("steel_log")).seed());
        assertEquals(id("cotton"), produced.get(id("cotton_bol")).seed());
        assertEquals(id("pyrotton"), produced.get(id("pyrotton_bol")).seed());
        assertNull(foods.get(id("log")), "empty inherited solution managers are not edible food sources");
        assertNull(foods.get(id("papercane")));
        for (String name : new String[]{"cotton_bol", "pyrotton_bol"}) {
            var edible = foods.get(id(name)).edible();
            assertEquals(500, edible.doseCents());
            assertEquals(20, edible.delayTicks());
            assertEquals(60, edible.forceFeedDelayTicks());
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertTrue(edible.requiresSpecialDigestion());
            assertTrue(edible.trash().isEmpty());
            assertEquals(1000, sources.get(id(name)).capacityCents());
        }
        assertEquals(Map.of(id("fiber"), 1000L), sources.get(id("cotton_bol")).contentsCents());
        assertEquals(Map.of(id("fiber"), 500L, id("phlogiston"), 500L), sources.get(id("pyrotton_bol")).contentsCents());
    }

    @Test void logOutputsRetainExactLocalMaterialIdentitiesWithoutClaimingRegistryValidation() {
        var expected = Map.of("papercane", Map.entry("paper", 2), "log", Map.entry("wood", 2),
                "steel_log", Map.entry("steel", 1), "cotton_bol", Map.entry("cotton_material", 2),
                "pyrotton_bol", Map.entry("pyrotton_material", 2));
        expected.forEach((profile, output) -> {
            var raw = read("item_processing_profile", profile);
            var log = com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow().log();
            assertEquals(id(output.getKey()), log.output());
            assertEquals(output.getValue(), log.count());
        });
        var inheritedDefaults = com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.Log.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertEquals(id("wood"), inheritedDefaults.output());
        assertEquals(2, inheritedDefaults.count());
    }

}
