package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GrapeBerryProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

    @Test void grapeAndBerriesResolveFoodProduceAndSeedLinksWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE); manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "grape", "berries"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "grape", "berries"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "grape", "berries"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "grape", "berries")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key,
                    ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size());
        assertEquals(2, extractables.size());
        assertEquals(Map.of(id("nutriment"), 500L, id("vitamin"), 400L), sources.get(id("grape")).contentsCents());
        assertEquals(900, sources.get(id("grape")).capacityCents());
        assertEquals(Map.of(id("nutriment"), 500L, id("vitamin"), 400L), sources.get(id("berries")).contentsCents());
        assertEquals(1100, sources.get(id("berries")).capacityCents());
        for (String name : new String[]{"grape", "berries"}) {
            var source = sources.get(id(name));
            assertTrue(source.capacityCents() >= source.contentsCents().values().stream().mapToLong(Long::longValue).sum(), name + " source contents fit");
            try (var stream = GrapeBerryProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/item_food_solution_profile/" + name + ".json")) {
                assertNull(stream, "candidate source remains TEST-only: " + name);
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        for (String product : new String[]{"grape", "berries"}) {
            var edible = food.get(id(product)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks());
            assertEquals(60, edible.forceFeedDelayTicks()); assertFalse(edible.requiresSpecialDigestion()); assertTrue(edible.trash().isEmpty());
            var profile = produce.get(id(product));
            assertEquals(id(product), profile.seed());
            assertEquals("food", extractables.get(id(product)).grindableSolutionName());
            assertEquals(1f, profile.potencyVisuals().minimumScale()); assertEquals(2f, profile.potencyVisuals().maximumScale());
        }
        assertEquals(Map.of(reagent("juicegrape"), 1000L), extractables.get(id("grape")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juiceberry"), 1000L), extractables.get(id("berries")).juiceSolution().centContents());
        for (String seed : new String[]{"grape", "berries"}) {
            try (var stream = getClass().getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/reagent/" + (seed.equals("grape") ? "juicegrape" : "juiceberry") + ".json")) {
                assertNotNull(stream, "bundled juice reagent " + seed);
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
    }
}
