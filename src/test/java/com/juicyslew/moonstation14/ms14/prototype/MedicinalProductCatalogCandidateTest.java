package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MedicinalProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void medicinalProductsResolveIndependentFoodExtractionProduceAndSeedCompositionWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources(
                "item_food_solution_profile", "lingzhi", "galaxythistle"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "lingzhi", "galaxythistle"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "lingzhi", "galaxythistle"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "lingzhi", "galaxythistle"),
                 ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key,
                    ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size()); assertEquals(2, extractables.size()); assertEquals(2, sources.size());
        assertEquals(Map.of(id("ultravasculine"), 2000L, id("epinephrine"), 2000L),
                sources.get(id("lingzhi")).contentsCents());
        assertEquals(4000, sources.get(id("lingzhi")).capacityCents());
        assertEquals(Map.of(id("stellibinin"), 2500L), sources.get(id("galaxythistle")).contentsCents());
        assertEquals(2500, sources.get(id("galaxythistle")).capacityCents());
        for (String name : new String[]{"lingzhi", "galaxythistle"}) {
            var edible = food.get(id(name)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks());
            assertEquals(60, edible.forceFeedDelayTicks()); assertTrue(edible.destroyOnEmpty());
            assertFalse(edible.requiresSpecialDigestion()); assertTrue(edible.trash().isEmpty());
            assertEquals(id(name), produce.get(id(name)).seed());
            assertEquals(1f, produce.get(id(name)).potencyVisuals().minimumScale());
            assertEquals(2f, produce.get(id(name)).potencyVisuals().maximumScale());
            assertEquals("food", extractables.get(id(name)).grindableSolutionName());
            assertNull(extractables.get(id(name)).juiceSolution());
            var seed = BotanyCatalogCandidateTest.resources("seed_profile", name).get(id(name));
            assertEquals(id(name).toString(), seed.get("product").getAsString());
        }
        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_food_solution_profile"}) {
            for (String name : new String[]{"lingzhi", "galaxythistle"}) {
                String path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
                try (var resource = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertNotNull(resource, "promoted production resource must be published: " + path);
                } catch (java.io.IOException e) {
                    throw new AssertionError("Could not close resource stream: " + path, e);
                }
            }
        }
    }
}
