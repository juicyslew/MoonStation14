package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/** Exhaustive runtime catalog/factory composition; does not exercise player harvest or extraction. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany-catalog", "botany-catalog-registration"})
public final class BotanyCatalogRuntimeGameTests {
    private static final Set<String> UNAVAILABLE = Set.of("killer_tomato", "anomaly_berry", "gatfruit",
            "fake_capfruit", "real_capfruit", "spacemans_trumpet");

    private BotanyCatalogRuntimeGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=100)
    public static void everySupportedSeedResolvesThroughLiveFactories(GameTestHelper helper) {
        var seeds = ModSeedProfiles.serverCatalog();
        var produce = ModProduceProfiles.serverCatalog();
        var expected = Set.of("aloe", "ambrosia_deus", "ambrosia_vulgaris", "apple", "banana", "berries",
                "blood_tomato", "bloonion", "blue_pumpkin", "blue_tomato", "bungo", "cabbage", "cannabis",
                "carrots", "chanterelle", "cherry", "chili", "chilly", "cocoa", "corn", "cotton", "death_nettle",
                "eggplant", "eggy", "extradimensional_orange", "fly_amanita", "galaxythistle", "garlic", "glasstle",
                "golden_apple", "grape", "holymelon", "koibean", "laughin_pea", "lemon", "lemoon", "lily", "lime",
                "lingzhi", "meatwheat", "mimana", "nettle", "oat", "onion", "onionred", "orange", "papercane",
                "pea", "pineapple", "poppy", "potato", "pumpkin", "pyrotton", "rainbow_cannabis", "rice", "soybeans",
                "steelcap", "sugarcane", "tea_plant", "tobacco", "tomato", "towercap", "watermelon", "wheat", "world_pea");
        helper.assertTrue(expected.size() == 65, "explicit supported pinned leaf set has 65 entries");
        var resolved = seeds.keys().stream().filter(id -> !id.getPath().equals("defaults")
                && !UNAVAILABLE.contains(id.getPath())).map(ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(resolved.equals(expected), "resolved intended catalog matches explicit expected leaves; missing="
                + difference(expected, resolved) + ", unexpected=" + difference(resolved, expected));
        var availability = BotanySpeciesAvailability.live();
        for (String name : expected) {
            var id = ResourceLocation.fromNamespaceAndPath("moonstation14", name);
            var seed = seeds.get(id);
            helper.assertTrue(seed != null, "resolved seed profile exists: " + id);
            var packetItem = BuiltInRegistries.ITEM.get(seed.packet());
            var productItem = BuiltInRegistries.ITEM.get(seed.product());
            helper.assertTrue(packetItem != Items.AIR, id + " packet is registered: " + seed.packet());
            helper.assertTrue(productItem != Items.AIR, id + " product is registered: " + seed.product());
            helper.assertTrue(produce.get(seed.product()) != null, id + " has resolved Produce: " + seed.product());
            helper.assertTrue(productItem instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait,
                    id + " product has the required reagent source trait: " + seed.product());
            var source = ProduceSourceComposition.resolve(productItem, null);
            if (source != null) helper.assertTrue(Float.isFinite(source.capacityCents()),
                    id + " product static source capacity resolves: " + seed.product());
            helper.assertTrue(availability.test(id), id + " is accepted by live availability");
            var genetics = new GeneticValue(id, seed);
            var reference = new PlantSeedReference(genetics, true);
            var harvested = ProduceFactory.createHarvest(reference, seed.lifecycle().potency());
            helper.assertTrue(harvested.is(productItem) && harvested.get(ModDataComponents.GENETIC_VALUE.get()).species().equals(id),
                    id + " factory product and captured genetics: " + seed.product());
            helper.assertTrue(harvested.has(ModDataComponents.REAGENT.get())
                            && harvested.has(ModDataComponents.PRODUCE_INSTANCE.get()),
                    id + " factory captures generated chemistry: " + seed.product());
            var packet = GeneticCarriers.createSeedPacket(reference, null);
            var roundtrip = GeneticCarriers.resolveSeedPacket(packet);
            helper.assertTrue(!packet.isEmpty() && roundtrip != null && roundtrip.genetics() == genetics
                            && roundtrip.genetics().species().equals(id),
                    id + " packet roundtrip preserves carried reference and species: " + seed.packet());
        }
        helper.assertTrue(seeds.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "pea")).packet()
                        .equals(seeds.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "world_pea")).packet())
                        && !GeneticCarriers.resolveSeedPacket(GeneticCarriers.createSeedPacket(new PlantSeedReference(
                                new GeneticValue(ResourceLocation.fromNamespaceAndPath("moonstation14", "pea"), seeds.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "pea"))), true), null))
                                .genetics().species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14", "world_pea")),
                "shared pea packet retains distinct carried pea identity");
        var holyId = ResourceLocation.fromNamespaceAndPath("moonstation14", "holymelon");
        var holyReference = new PlantSeedReference(new GeneticValue(holyId, seeds.get(holyId)), true);
        var holy = ProduceFactory.createHarvest(holyReference, seeds.get(holyId).lifecycle().potency());
        helper.assertTrue(seeds.get(holyId).product().getPath().equals("holymelon")
                        && ProduceSourceComposition.resolve(BuiltInRegistries.ITEM.get(seeds.get(holyId).product()), null) != null
                        && holy.get(ModDataComponents.PLANT_SEED_REFERENCE.get()).current().species().equals(holyId),
                "holymelon fallback source does not replace its generated product genetics");
        var looseProfiles = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverLooseObjectProfiles();
        var authoredLoose = Set.of("bloonion", "blue_pumpkin", "death_nettle", "egg", "glasstle", "holymelon",
                "nettle", "pumpkin", "shard_glass", "trash_banana_peel", "trash_mimana_peel", "watermelon");
        for (String name : authoredLoose) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("moonstation14", name));
            helper.assertTrue(item != Items.AIR, name + " registered catalog item exists");
            var stack = new ItemStack(item);
            var expectedProfile = ResourceLocation.fromNamespaceAndPath("moonstation14", name);
            helper.assertTrue(expectedProfile.equals(stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get())),
                    name + " default loose-object component references authored profile");
            helper.assertTrue(com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, false)
                            == looseProfiles.get(expectedProfile), name + " resolves its named server profile");
        }
        var shardId = ResourceLocation.fromNamespaceAndPath("moonstation14", "shard_glass");
        var shard = BuiltInRegistries.ITEM.get(shardId);
        helper.assertTrue(shard instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait,
                "glass shard exposes its source material solution without food behavior");
        var shardStack = new ItemStack(shard);
        var shardTrait = (com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) shard;
        var shardSolution = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(shardId);
        helper.assertTrue(shardSolution != null && shardSolution.capacityCents() == 500
                        && shardSolution.contentsCents().equals(java.util.Map.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "silicon"), 500L))
                        && !shardSolution.refillable() && shardTrait.solutionName(shardStack, null).equals("shardglass")
                        && shardTrait.getCapacity(shardStack, null) == 5f,
                "glass shard grinder solution resolves source capacity, contents, and name");
        helper.assertTrue(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.serverCatalog().get(shardId) != null
                        && produce.get(shardId) == null && seeds.get(shardId) == null
                        && com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.serverCatalog().get(shardId) == null,
                "glass shard is extractable material, not produce, seed, or ingestible food");
        for (var tomato : java.util.Map.of("blood_tomato", ModItems.BLOOD_TOMATO.get(),
                "blue_tomato", ModItems.BLUE_TOMATO.get()).entrySet()) {
            var stack = new ItemStack(tomato.getValue());
            var tomatoProfile = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
            helper.assertTrue(tomatoProfile.equals(stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()))
                            && com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, false)
                            == looseProfiles.get(tomatoProfile), tomato.getKey() + " reuses tomato loose profile");
        }
        for (var item : java.util.List.of(ModItems.CARROT.get(), ModItems.EGGSHELLS.get())) {
            var stack = new ItemStack(item);
            helper.assertTrue(stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()) == null
                            && com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, false)
                            == looseProfiles.get(com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.GENERIC_ID),
                    item + " intentionally falls back to generic loose profile");
        }
        helper.succeed();
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        var result = new java.util.HashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
