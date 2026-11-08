package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedItemProfile;
import com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogProductionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    // Intended species whose MAIN product compositions are currently registered for acquisition.
    // Keep this explicit: deriving the expected set from registrations alone would hide omissions.
    private static final String[] REGISTERED_FRUIT_SPECIES = {
            "aloe", "ambrosia_deus", "ambrosia_vulgaris", "apple", "banana", "berries", "blood_tomato", "bloonion", "blue_pumpkin", "blue_tomato", "bungo", "cabbage", "cannabis", "carrots", "chanterelle", "cherry", "chili", "chilly", "cocoa", "corn", "cotton", "death_nettle", "eggplant", "eggy", "extradimensional_orange", "fly_amanita", "galaxythistle", "garlic", "glasstle", "golden_apple", "grape", "holymelon", "koibean", "laughin_pea", "lemon", "lemoon", "lily", "lime", "lingzhi", "meatwheat", "mimana", "nettle", "oat", "onion", "onionred", "orange", "papercane", "pea", "pineapple", "poppy", "potato", "pumpkin", "pyrotton", "rainbow_cannabis", "rice", "soybeans", "steelcap", "sugarcane", "tea_plant", "tobacco", "tomato", "towercap", "watermelon", "wheat", "world_pea"
    };

    @Test void registeredFruitItemsMatchProductionSeedLinks() {
        for (var species : REGISTERED_FRUIT_SPECIES) {
            var seed = BotanyCatalogCandidateTest.mainResources("seed_profile", species).get(id(species));
            var product = seed.get("product").getAsString().substring("moonstation14:".length());
            var packet = seed.get("packet").getAsString().substring("moonstation14:".length());
            var produceRows = BotanyCatalogCandidateTest.mainResourcesAll("produce_profile");
            var resolvedProduce = PrototypeResolver.resolve(produceRows, ModProduceProfiles.TYPE.mergeStrategy().orElse(null)).asMap();
            assertNotNull(resolvedProduce.get(id(product)), species + " resolved MAIN product key " + product);
            assertNotNull(ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    resolvedProduce.get(id(product))).getOrThrow(), species + " typed MAIN product " + product);
            assertNotNull(BotanyCatalogCandidateTest.mainResources("seed_item_profile", packet).get(id(packet)), species + " packet=" + packet);
        }
        assertEquals(65, REGISTERED_FRUIT_SPECIES.length);
        assertEquals("moonstation14:pea_seeds", BotanyCatalogCandidateTest.mainResources("seed_profile", "world_pea").get(id("world_pea")).get("packet").getAsString());
    }

    @Test void registeredSeedPacketsAndMainProductsAreListedExactlyOnceInCreativeTab() {
        var tab = com.juicyslew.moonstation14.item.ModCreativeModeTabs.MOONSTATION_ITEM_ENTRIES;
        var seedRows = BotanyCatalogCandidateTest.mainResourcesAll("seed_profile");
        var seen = new java.util.HashSet<ResourceLocation>();
        for (var species : REGISTERED_FRUIT_SPECIES) {
            var seed = seedRows.get(id(species));
            assertNotNull(seed, species + " production seed profile");
            var packet = ResourceLocation.parse(seed.get("packet").getAsString());
            var product = ResourceLocation.parse(seed.get("product").getAsString());
            if (seen.add(packet)) assertCreativeEntryExactlyOnce(tab, packet);
            if (seen.add(product)) assertCreativeEntryExactlyOnce(tab, product);
        }
        assertEquals(65, REGISTERED_FRUIT_SPECIES.length);
        assertEquals(id("pea_seeds"), ResourceLocation.parse(seedRows.get(id("world_pea")).get("packet").getAsString()));
    }

    private static void assertCreativeEntryExactlyOnce(java.util.List<java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike>> tab,
                                                       ResourceLocation id) {
        var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.getHolder(id).orElseThrow(() ->
                new AssertionError("registered botany acquisition item missing: " + id));
        assertEquals(1, tab.stream().filter(entry -> entry.get().asItem() == holder.value()).count(), id.toString());
    }

    @Test void carrotAndCabbageMainCompositionsResolveAndLink() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModSeedItemProfiles.TYPE); manager.register(ModItemProcessingProfiles.TYPE);
        var produces = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var ingestions = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var extracts = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var food = new LinkedHashMap<ResourceLocation, ItemFoodSolutionProfile>();
        var seedItems = new LinkedHashMap<ResourceLocation, SeedItemProfile>();
        var processing = new LinkedHashMap<ResourceLocation, ItemProcessingProfile>();
        Map<PrototypeType<?>, Map<ResourceLocation, com.google.gson.JsonObject>> profiles = new LinkedHashMap<>();
        profiles.put(ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("produce_profile"));
        profiles.put(ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("ingestion_profile"));
        profiles.put(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("seed_profile"));
        profiles.put(ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("extractable_profile"));
        profiles.put(ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("item_food_solution_profile"));
        profiles.put(ModSeedItemProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("seed_item_profile"));
        profiles.put(ModItemProcessingProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("item_processing_profile"));
        manager.stage(profiles, catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v) -> produces.put(k, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((k,v) -> ingestions.put(k, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((k,v) -> seeds.put(k, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((k,v) -> extracts.put(k, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).forEach((k,v) -> food.put(k, ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModSeedItemProfiles.TYPE.typeId()).forEach((k,v) -> seedItems.put(k, SeedItemProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModItemProcessingProfiles.TYPE.typeId()).forEach((k,v) -> processing.put(k, ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
        });
        assertEquals(65, REGISTERED_FRUIT_SPECIES.length);
        for (String species : REGISTERED_FRUIT_SPECIES) {
            SeedProfile seed = seeds.get(id(species));
            assertNotNull(seed, species + " resolved seed");
            assertNotNull(produces.get(seed.product()), species + " resolved product " + seed.product());
            var packet = seedItems.get(seed.packet());
            assertNotNull(packet, species + " packet " + seed.packet());
            if (species.equals("world_pea")) assertEquals(id("pea"), packet.seedId());
            else if (packet.seedId() != null) assertEquals(id(species), packet.seedId(), species + " carried packet identity");
        }
        for (var profile : processing.values()) {
            if (profile.sliceableFood() != null) assertNotNull(profile.sliceableFood().output());
            if (profile.butcherable() != null) profile.butcherable().outputs().forEach(o -> assertNotNull(o.output()));
            if (profile.spawnItemsOnUse() != null) profile.spawnItemsOnUse().outputs().forEach(o -> assertNotNull(o.output()));
        }
        for (String name : new String[]{"carrot", "cabbage"}) {
            var produce = produces.get(id(name)); var seedName = name.equals("carrot") ? "carrots" : "cabbage";
            var seed = seeds.get(id(seedName));
            assertNotNull(produce); assertNotNull(seed);
            assertEquals(id(seedName), produce.seed()); assertEquals(id(name), seed.product());
            assertEquals(id(name.equals("carrot") ? "carrots_seeds" : "cabbage_seeds"), seed.packet());
            assertEquals(.5f, produce.potencyVisuals().minimumScale()); assertEquals(4.5f, produce.potencyVisuals().maximumScale());
            assertEquals("food", extracts.get(id(name)).grindableSolutionName());
            assertEquals(500, ingestions.get(id(name)).edible().doseCents());
            assertEquals(20, ingestions.get(id(name)).edible().delayTicks());
            assertTrue(food.get(id(name)).capacityCents() >= food.get(id(name)).contentsCents().values().stream().mapToLong(Long::longValue).sum());
        }
        assertEquals(1000L, extracts.get(id("carrot")).juiceSolution().centContents().get(net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,id("juicecarrot"))));
        assertEquals(200L, extracts.get(id("carrot")).juiceSolution().centContents().get(net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,id("oculine"))));
        assertNull(extracts.get(id("cabbage")).juiceSolution());
        assertEquals(1200, food.get(id("carrot")).capacityCents()); assertEquals(1400, food.get(id("cabbage")).capacityCents());
        assertTrue(seeds.get(id("carrots")).chemicals().containsKey(id("juicecarrot")));
        assertTrue(seeds.get(id("cabbage")).chemicals().containsKey(id("nutriment")));
    }
}
