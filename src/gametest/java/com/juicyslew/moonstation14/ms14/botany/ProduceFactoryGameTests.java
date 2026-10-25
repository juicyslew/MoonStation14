package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.botany.GeneticValue;
import io.netty.buffer.Unpooled;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"botany", "botany-produce-factory"})
@PrefixGameTestTemplate(false)
public final class ProduceFactoryGameTests {
    private ProduceFactoryGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void harvestUsesResolvedSourceCapacityAndReplacesContents(GameTestHelper helper) {
        var level = helper.getLevel();
        var wheatItem = ModItems.WHEAT_BUSHEL.get();
        var wheatId = ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat_bushel");
        var wheatProfile = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(wheatId);
        helper.assertTrue(wheatProfile != null,
                "wheat has resolved independent item food source defaults");
        var ingestion = ModIngestionProfiles.serverLookup(wheatId);
        helper.assertTrue(ingestion != null && ingestion.edible().requiresSpecialDigestion()
                        && ingestion.edible().kind() == com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.Kind.FOOD,
                "wheat resolves as food requiring special digestion");
        helper.assertTrue(!com.juicyslew.moonstation14.ms14.ingestion.TimedIngestionAccess.specialDigestionAllowed(wheatId),
                "ordinary local bodies are denied wheat absent a stomach whitelist capability");
        helper.assertTrue(wheatProfile.capacityCents() == 1000
                        && wheatProfile.contentsCents().get(ResourceLocation.fromNamespaceAndPath("moonstation14", "flour")) == 1000L,
                "wheat source inherits the 10-unit flour default");
        helper.assertTrue(wheatItem.getCapacity() == wheatProfile.capacityCents() / 100f,
                "registered wheat item's reagent trait consumes resolved source capacity");

        var wheat = ProduceFactory.createHarvest(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5);
        helper.assertTrue(wheat.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents() == 1650,
                "wheat harvest capacity preserves 1000 initial plus 650 generated cents");
        helper.assertTrue(ReagentUnits.total(wheat.get(ModDataComponents.REAGENT.get()).centContents().values()) == 650L
                        && wheat.get(ModDataComponents.REAGENT.get()).centContents().get(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("flour")) == 525L,
                "harvest contents replace, rather than merge with, prototype flour");

        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var tomato = ProduceFactory.createHarvest(tomatoId, 10);
        helper.assertTrue(tomato.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents() == 1841,
                "tomato capacity preserves resolved 1400 initial plus 441 generated cents");
        helper.assertTrue(ReagentUnits.total(tomato.get(ModDataComponents.REAGENT.get()).centContents().values()) == 441L,
                "tomato harvest contents contain generated chemistry only");
        var emptied = tomato.copy();
        emptied.set(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(emptied, level);
        helper.assertTrue(emptied.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(),
                "an emptied harvest is never refilled from prototype source defaults");
        helper.succeed();
    }

    @GameTestTags({"botany-genetics"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void geneticValueRegisteredComponentPersistsAndStreamsCompleteProfile(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "genetic_test");
        var profile = new SeedProfile(ResourceLocation.fromNamespaceAndPath("moonstation14", "genetic_packet"),
                ResourceLocation.fromNamespaceAndPath("moonstation14", "genetic_produce"), "genetic_visual",
                new SeedProfile.Biology(37, 1.25f, 2.5f, 294, 11, 82, 122, 6, 12, 5),
                new SeedProfile.Lifecycle(7, 31, 12, 54, 3, 83, SeedProfile.Harvest.NO_REPEAT),
                java.util.Map.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "genetic_reagent"),
                        new SeedProfile.Chemical(2, 9, 4)), "moonstation14:genetic_test");
        var expected = new GeneticValue(id, profile);
        var stack = new ItemStack(Items.WHEAT_SEEDS);
        stack.set(ModDataComponents.GENETIC_VALUE.get(), expected);

        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var persisted = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        helper.assertTrue(expected.equals(persisted.get(ModDataComponents.GENETIC_VALUE.get())),
                "registered component retains all persistent genetics fields");

        var component = ModDataComponents.GENETIC_VALUE.get();
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            component.streamCodec().encode(wire, expected);
            var received = component.streamCodec().decode(wire);
            helper.assertTrue(expected.equals(received), "registered component stream codec retains complete genetics profile");
        } finally {
            wire.release();
        }
        helper.succeed();
    }
}
