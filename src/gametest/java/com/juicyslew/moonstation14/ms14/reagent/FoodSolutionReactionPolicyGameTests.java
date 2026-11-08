package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

@GameTestTags({"reagent-reaction-policy"})
@PrefixGameTestTemplate(false)
public final class FoodSolutionReactionPolicyGameTests {
    private FoodSolutionReactionPolicyGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 20, batch = "catalog-food-reaction-policy-tomato")
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining"})
    public static void destinationPolicyControlsReaction(GameTestHelper helper) {
        var manager = PrototypeRuntime.serverManager();
        var type = ModItemFoodSolutionProfiles.TYPE;
        var old = new HashMap<>(manager.snapshot(type).asMap());
        try {
            var profiles = new HashMap<>(old);
            var tomatoId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
            var previous = profiles.get(tomatoId);
            if (previous == null) throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "registered tomato food-solution profile exists");
            profiles.put(tomatoId,
                    new ItemFoodSolutionProfile(previous.capacityCents(), previous.contentsCents(), false));
            manager.publishDecoded(type, profiles);

            var sodium = ModReagents.createKey("sodium");
            var chlorine = ModReagents.createKey("chlorine");
            var tablesalt = ModReagents.createKey("tablesalt");
            ItemStack food = new ItemStack(ModItems.TOMATO.get());
            food.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(sodium, 1f)));
            ItemStack donor = new ItemStack(ModItems.BEAKER_LARGE.get());
            donor.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(chlorine, 1f)));

            ReagentSystem.handleTransfer(((IReagentTrait) donor.getItem()).toHandle(donor),
                    ((IReagentTrait) food.getItem()).toHandle(food), helper.getLevel(), 1f);
            var foodContents = food.get(ModDataComponents.REAGENT.get()).centContents();
            helper.assertTrue(donor.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(), "donor emptied");
            helper.assertTrue(foodContents.getOrDefault(sodium, 0L) == 100L
                            && foodContents.getOrDefault(chlorine, 0L) == 100L
                            && !foodContents.containsKey(tablesalt),
                    "non-reacting food destination preserves sodium and chlorine without tablesalt");

            ItemStack beaker = new ItemStack(ModItems.BEAKER_LARGE.get());
            ReagentSystem.handleTransfer(((IReagentTrait) food.getItem()).toHandle(food),
                    ((IReagentTrait) beaker.getItem()).toHandle(beaker), helper.getLevel(), 2f);
            var beakerContents = beaker.get(ModDataComponents.REAGENT.get()).centContents();
            helper.assertTrue(beakerContents.getOrDefault(tablesalt, 0L) == 200L
                            && !beakerContents.containsKey(sodium) && !beakerContents.containsKey(chlorine)
                            && food.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(),
                    "reacting beaker destination reacts contents even when source food cannot react");
            helper.succeed();
        } finally {
            manager.publishDecoded(type, old);
        }
    }
}
