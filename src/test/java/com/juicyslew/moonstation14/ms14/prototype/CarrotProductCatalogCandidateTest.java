package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Source mapping: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91, FoodCarrot inherits FoodProduceBase -> FoodInjectableBase -> FoodBase -> EdibleBase -> BaseItem. */
class CarrotProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

    @Test void carrotFoodAndProduceResolveIndependentSourceAndStaticJuice() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE); manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var extraction = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "carrot"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "carrot"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "carrot"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources("seed_profile", "defaults", "carrots"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "carrot")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extraction.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        var profile = produce.get(id("carrot"));
        assertEquals(id("carrots"), profile.seed());
        assertEquals(1f, profile.potencyVisuals().minimumScale()); assertEquals(2f, profile.potencyVisuals().maximumScale());
        assertEquals(1, extraction.size());
        assertEquals("food", extraction.get(id("carrot")).grindableSolutionName());
        assertEquals(Map.of(reagent("juicecarrot"), 1000L, reagent("oculine"), 200L), extraction.get(id("carrot")).juiceSolution().centContents());
        var edible = food.get(id("carrot")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, edible.kind()); assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks());
        assertEquals(60, edible.forceFeedDelayTicks()); assertFalse(edible.requiresSpecialDigestion());
        assertEquals(1200, sources.get(id("carrot")).capacityCents());
        assertEquals(Map.of(id("juicecarrot"), 500L, id("vitamin"), 400L, id("oculine"), 300L), sources.get(id("carrot")).contentsCents());
        assertTrue(sources.get(id("carrot")).capacityCents() >= sources.get(id("carrot")).contentsCents().values().stream().mapToLong(Long::longValue).sum());
        try (var stream = CarrotProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/item_food_solution_profile/carrot.json")) {
            assertNotNull(stream, "activated production profile");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        assertEquals(id("carrot"), seeds.get(id("carrots")).product());
        assertEquals("carrot", seeds.get(id("carrots")).visualKey());
    }
}
