package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
        var wheatProfile = ModProduceProfiles.serverCatalog().get(wheatId);
        helper.assertTrue(wheatProfile != null && wheatProfile.sourceSolution() != null,
                "wheat has resolved produce source defaults");
        helper.assertTrue(ModIngestionProfiles.serverLookup(wheatId) == null,
                "ordinary wheat is not assigned an ingestion profile");
        var wheatSource = wheatProfile.sourceSolution();
        helper.assertTrue(wheatSource.capacityCents() == 1000
                        && wheatSource.contentsCents().get(ResourceLocation.fromNamespaceAndPath("moonstation14", "flour")) == 1000L,
                "wheat source inherits the 10-unit flour default");
        helper.assertTrue(wheatItem.getCapacity() == wheatSource.capacityCents() / 100f,
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
        PrototypeEdibleItem.initializeSource(emptied, level);
        helper.assertTrue(emptied.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(),
                "an emptied harvest is never refilled from prototype source defaults");
        helper.succeed();
    }
}
