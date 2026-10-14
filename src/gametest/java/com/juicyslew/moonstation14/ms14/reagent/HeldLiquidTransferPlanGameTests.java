package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Native helper fixture for exact, detached held-liquid transfer staging. */
@GameTestTags({"botany", "botany-liquid-plan"})
@PrefixGameTestTemplate(false)
public final class HeldLiquidTransferPlanGameTests {
    private HeldLiquidTransferPlanGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void proportionalTransferIsExactAndCapacityLimited(GameTestHelper helper) {
        var water = ModReagents.createKey("water");
        var nutrient = ModReagents.createKey("eznutrient");
        var source = new ReagentAttachment(ReagentComponent.fromCents(Map.of(water, 10_000L, nutrient, 20_000L)));
        var target = new ReagentAttachment();
        var sourceBefore = source.snapshotUnits();
        var targetBefore = target.snapshotUnits();

        var plan = HeldLiquidTransferPlan.plan(source, target, 20_000L, 200f, 500L, (ServerLevel) helper.getLevel());

        helper.assertTrue(plan.admittedCents() == 500L, "requested half-unit is admitted exactly");
        helper.assertTrue(plan.sourceAfter().snapshotUnits().equals(Map.of(water, 9_834L, nutrient, 19_666L)),
                "proportional split conserves exact cent shares");
        helper.assertTrue(plan.recipientAfter().snapshotUnits().equals(Map.of(water, 166L, nutrient, 334L)),
                "recipient receives exact proportional cent shares");
        helper.assertTrue(source.snapshotUnits().equals(sourceBefore) && target.snapshotUnits().equals(targetBefore),
                "planning leaves original attachments unchanged");
        helper.assertTrue(plan.sourceAfter().totalUnits() + plan.recipientAfter().totalUnits()
                        == source.totalUnits() + target.totalUnits(), "exact source and recipient total is conserved");

        var limited = HeldLiquidTransferPlan.plan(source, target, 200L, 200f, 500L, (ServerLevel) helper.getLevel());
        helper.assertTrue(limited.admittedCents() == 200L
                        && limited.sourceAfter().totalUnits() + limited.recipientAfter().totalUnits() == source.totalUnits(),
                "recipient capacity limits admission without losing source cents");
        helper.succeed();
    }
}
