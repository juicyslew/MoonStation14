package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestTags({"reagent-publication", "timed-interaction"})
@PrefixGameTestTemplate(false)
public final class StackReagentPublicationGameTests {
    private StackReagentPublicationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void replacesAndClearsCompleteStackSolution(GameTestHelper helper) {
        var water = ModReagents.createKey("water");
        var nutrient = ModReagents.createKey("eznutrient");
        ItemStack stack = new ItemStack(ModItems.BEAKER.get());
        ItemStack unrelated = new ItemStack(Items.STICK);
        MS14Provider.update(stack, ReagentSystem.bridgeForHolder(stack),
                new ReagentAttachment(Map.of(water, 3f, nutrient, 2f)));
        MS14Provider.update(unrelated, ReagentSystem.bridgeForHolder(unrelated),
                new ReagentAttachment(Map.of(nutrient, 7f)));

        helper.assertTrue(BodyLiquidInteraction.publishStackReagents(stack, Map.of(water, 1_250L)),
                "supported beaker accepts complete replacement");
        helper.assertTrue(MS14Provider.getDetached(stack, ReagentSystem.bridgeForHolder(stack)).snapshotUnits()
                        .equals(Map.of(water, 1_250L)),
                "replacement contains planned reagent only, not prior prototype/current contents");
        helper.assertTrue(MS14Provider.getDetached(unrelated, ReagentSystem.bridgeForHolder(unrelated)).snapshotUnits()
                        .equals(Map.of(nutrient, 700L)), "unrelated stack attachment is unchanged");

        helper.assertTrue(BodyLiquidInteraction.publishStackReagents(stack, Map.of()), "empty solution publishes");
        helper.assertTrue(MS14Provider.getDetached(stack, ReagentSystem.bridgeForHolder(stack)).isEmpty(),
                "empty replacement clears prior solution");
        ItemStack unsupported = new ItemStack(Items.STICK);
        helper.assertTrue(!BodyLiquidInteraction.publishStackReagents(unsupported, Map.of()),
                "non-reagent stack is rejected");
        helper.assertTrue(MS14Provider.getDetached(unsupported, ReagentSystem.bridgeForHolder(unsupported)).isEmpty(),
                "rejected stack remains unchanged");
        helper.succeed();
    }
}
