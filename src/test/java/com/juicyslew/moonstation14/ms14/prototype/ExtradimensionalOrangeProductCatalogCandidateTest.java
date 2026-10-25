package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only supported composition mapped from pinned SS14 produce.yml FoodExtradimensionalOrange. */
class ExtradimensionalOrangeProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void candidateResolvesSupportedCompositionWithoutPublishingAndGenericAppearanceUsesScopedSurrogate() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(
                BotanyCatalogCandidateTest.resources("item_food_solution_profile", "extradimensional_orange"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "extradimensional_orange"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "extradimensional_orange"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "extradimensional_orange"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile", "extradimensional_orange")
        ), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> ingestion.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        var key = id("extradimensional_orange");
        assertEquals(key, produce.get(key).seed());
        assertEquals(.5f, produce.get(key).potencyVisuals().minimumScale());
        assertEquals(1f, produce.get(key).potencyVisuals().maximumScale());
        assertEquals(IngestionProfile.Kind.FOOD, ingestion.get(key).edible().kind());
        assertEquals(500, ingestion.get(key).edible().doseCents());
        assertEquals(20, ingestion.get(key).edible().delayTicks());
        assertEquals(60, ingestion.get(key).edible().forceFeedDelayTicks());
        assertFalse(ingestion.get(key).edible().requiresSpecialDigestion());
        assertTrue(ingestion.get(key).edible().trash().isEmpty());
        assertEquals(1400, sources.get(key).capacityCents());
        assertEquals(Map.of(id("haloperidol"), 500L, id("nutriment"), 500L, id("vitamin"), 400L), sources.get(key).contentsCents());
        assertEquals(0, sources.get(key).capacityCents() - sources.get(key).contentsCents().values().stream().mapToLong(Long::longValue).sum());
        assertEquals("food", extractables.get(key).grindableSolutionName());
        assertEquals(Map.of(reagent("juiceorange"), 1000L), extractables.get(key).juiceSolution().centContents());
        assertTrue(extractables.get(key).juiceSolution().centContents().values().stream().allMatch(value -> value % 1000 == 0));
        for (String family : new String[]{"produce_profile", "ingestion_profile", "item_food_solution_profile", "extractable_profile",
                "item_processing_profile", "seed_item_profile"}) assertMainAbsent(family);

        // Orange has no registered Minecraft item. Exercise the existing appearance consumer on wheat as a temporary generic-item surrogate.
        // This verifies only the shared potency scaling boundary, not an orange item binding or runtime harvest path.
        var client = PrototypeRuntime.clientManager();
        var prior = client.snapshot(ModProduceProfiles.TYPE);
        var priorRaw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        prior.asMap().forEach((profileId, profile) -> priorRaw.put(profileId,
                ModProduceProfiles.TYPE.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, profile).getOrThrow().getAsJsonObject()));
        try {
            var visualResources = BotanyCatalogCandidateTest.mainResources("produce_profile", "produce_base", "wheat_bushel");
            visualResources.get(id("wheat_bushel")).add("potency_visuals", com.google.gson.JsonParser.parseString(
                    "{\"minimum_scale\":0.5,\"maximum_scale\":1.0}"));
            client.reload(ModProduceProfiles.TYPE, visualResources);
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WHEAT_BUSHEL.get());
            for (int potency : new int[]{0, 100}) {
                stack.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),
                        new com.juicyslew.moonstation14.ms14.botany.ProduceInstanceData(key, potency, 0));
                assertEquals(potency == 0 ? .5f : 1f, com.juicyslew.moonstation14.ms14.botany.ProduceWorldAppearance.scale(stack));
            }
        } finally {
            client.reload(ModProduceProfiles.TYPE, priorRaw);
            assertEquals(prior.asMap(), client.snapshot(ModProduceProfiles.TYPE).asMap(), "restore exact resolved client catalog state");
        }
    }

    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }
    private static void assertMainAbsent(String family) {
        try (var stream = ExtradimensionalOrangeProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/" + family + "/extradimensional_orange.json")) {
            assertNull(stream, "candidate remains TEST-only: " + family);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
