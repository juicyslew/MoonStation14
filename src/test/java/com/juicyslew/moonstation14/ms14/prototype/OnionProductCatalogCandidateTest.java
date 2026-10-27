package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OnionProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void onionsAndSlicesResolveWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "onion", "onion_red", "onion_slice", "onion_red_slice"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "onion", "onion_red"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "produce_slice_base", "onion", "onion_red", "onion_slice", "onion_red_slice"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "onion", "onion_red", "onion_slice", "onion_red_slice")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(4, food.size()); assertEquals(4, extractables.size());
        for (String name : new String[]{"onion", "onion_red"}) {
            var edible = food.get(id(name)).edible();
            assertEquals(Map.of(id("nutriment"),1000L,id("vitamin"),400L,id("allicin"),400L), sources.get(id(name)).contentsCents());
            assertEquals(1800, sources.get(id(name)).capacityCents());
            assertEdibleDefaults(edible);
            assertEquals(id(name.equals("onion") ? "onion" : "onionred"), produce.get(id(name)).seed());
            assertEquals(1f, produce.get(id(name)).potencyVisuals().minimumScale()); assertEquals(2f, produce.get(id(name)).potencyVisuals().maximumScale());
        }
        for (String name : new String[]{"onion_slice", "onion_red_slice"}) {
            var edible = food.get(id(name)).edible();
            assertEquals(Map.of(id("nutriment"),200L,id("allicin"),100L,id("vitamin"),100L), sources.get(id(name)).contentsCents());
            assertEquals(500, sources.get(id(name)).capacityCents()); assertEdibleDefaults(edible);
            assertEquals("food", extractables.get(id(name)).grindableSolutionName());
            assertNull(extractables.get(id(name)).juiceSolution());
        }
        for (String name : new String[]{"onion", "onion_red"}) {
            assertEquals("food", extractables.get(id(name)).grindableSolutionName());
            assertNull(extractables.get(id(name)).juiceSolution());
            var processing = ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    BotanyCatalogCandidateTest.resources("item_processing_profile", name).get(id(name))).getOrThrow();
            assertEquals(id(name.equals("onion") ? "onion_slice" : "onion_red_slice"), processing.sliceableFood().output());
            assertEquals(5, processing.sliceableFood().count()); assertEquals(1f, processing.sliceableFood().delaySeconds());
        }
        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_processing_profile"})
            for (String name : new String[]{"onion", "onion_red", "onion_slice", "onion_red_slice"}) {
                String path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
                try (var resource = getClass().getClassLoader().getResourceAsStream(path)) { assertNull(resource, path); }
                catch (java.io.IOException e) { throw new AssertionError(e); }
            }
    }

    private static void assertEdibleDefaults(IngestionProfile.Edible edible) {
        assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
        assertFalse(edible.requiresSpecialDigestion()); assertTrue(edible.trash().isEmpty());
    }
}
