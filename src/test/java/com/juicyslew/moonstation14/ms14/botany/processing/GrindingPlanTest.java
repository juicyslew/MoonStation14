package com.juicyslew.moonstation14.ms14.botany.processing;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GrindingPlanTest {
    private static final ResourceKey<ReagentData> A = ModReagents.createKey("grind_plan_a");
    private static final ResourceKey<ReagentData> B = ModReagents.createKey("grind_plan_b");
    private static ReagentAttachment amount(long cents) { return new ReagentAttachment(java.util.Map.of(A, cents / 100f)); }

    @Test void wholeSourcesSkipNonfittingContinueAfterEmptyAndConsumeEligibleEmpty() {
        var empty = new GrindingPlan.Source(new ReagentAttachment(), true);
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(amount(2000), true),
                new GrindingPlan.Source(amount(2000), true), new GrindingPlan.Source(amount(500), true), empty),
                new ReagentAttachment(), 2500, ignored -> { });
        assertEquals(List.of(0, 2, 3), result.consumedSourceIndexes());
        assertEquals(2500, result.beaker().totalUnits());
    }

    @Test void nonGrindableEmptySourcesAndInsufficientCapacityAreNotConsumed() {
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(new ReagentAttachment(), true),
                new GrindingPlan.Source(amount(100), false), new GrindingPlan.Source(amount(200), true)), amount(900), 1000,
                ignored -> { });
        assertEquals(List.of(0), result.consumedSourceIndexes());
        assertEquals(900, result.beaker().totalUnits());
    }

    @Test void callbackReactionsReleaseCapacityBeforeNextWholeSourceAndInputsStayUnchanged() {
        var first = amount(100); var second = amount(100);
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(first, true), new GrindingPlan.Source(second, true)),
                amount(900), 1000, staged -> { staged.removeUnits(A, 100); });
        assertEquals(List.of(0, 1), result.consumedSourceIndexes());
        assertEquals(900, result.beaker().totalUnits());
        assertEquals(100, first.totalUnits()); assertEquals(100, second.totalUnits());
    }

    @Test void expandingReactionRejectsOnlyThatSourceAndKeepsEarlierAcceptedBatch() {
        var first = amount(100); var expands = amount(100); var later = amount(50);
        var originalBeaker = amount(800);
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(first, true),
                        new GrindingPlan.Source(expands, true), new GrindingPlan.Source(later, true)),
                originalBeaker, 1000, staged -> {
                    if (staged.totalUnits() == 1000) staged.admitUnits(A, 100, 2000);
                });
        assertEquals(List.of(0, 2), result.consumedSourceIndexes());
        assertEquals(950, result.beaker().totalUnits());
        assertEquals(100, first.totalUnits());
        assertEquals(100, expands.totalUnits());
        assertEquals(50, later.totalUnits());
        assertEquals(800, originalBeaker.totalUnits());
    }

    @Test void fixedMultiReagentJuiceUsesWholeSourceCapacityAndContinuesAfterSkip() {
        var juice = new ReagentAttachment(java.util.Map.of(A, 6f, B, 4f));
        var later = amount(500);
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(juice, true),
                        new GrindingPlan.Source(amount(1100), true), new GrindingPlan.Source(later, true)),
                amount(400), 2000, ignored -> { });
        assertEquals(List.of(0, 2), result.consumedSourceIndexes());
        assertEquals(1900, result.beaker().totalUnits());
        assertEquals(1000, juice.totalUnits());
    }

    @Test void stackSourcesScaleAndReturnPartialCountsWhilePreservingInput() {
        var perItem = amount(300);
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(perItem, true, 30, true)),
                amount(100), 1000, ignored -> { });
        assertEquals(List.of(0), result.consumedSourceIndexes());
        assertEquals(3, result.consumedCount(0));
        assertEquals(1000, result.beaker().totalUnits());
        assertEquals(300, perItem.totalUnits());
    }

    @Test void stackCapacityToleranceConsumesOneWithoutPublishingRejectedAddition() {
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(amount(300), true, 30, true)),
                amount(701), 1000, ignored -> fail("rejected stack addition must not react"));
        assertEquals(List.of(0), result.consumedSourceIndexes());
        assertEquals(1, result.consumedCount(0));
        assertEquals(701, result.beaker().totalUnits());
    }

    @Test void mixedReagentStackScalesEachReagentAndReactsOnceWithAcceptedBatch() {
        var fiber = ModReagents.createKey("grind_plan_fiber");
        var phlogiston = ModReagents.createKey("grind_plan_phlogiston");
        var perItem = new ReagentAttachment(java.util.Map.of(fiber, 3f, phlogiston, 3f));
        var originalBeaker = new ReagentAttachment();
        int[] reactions = {0};
        var source = new GrindingPlan.Source(perItem, true, 30, true);
        var result = GrindingPlan.prepare(List.of(source),
                originalBeaker, 10_000, staged -> {
                    reactions[0]++;
                    assertEquals(4_800L, staged.snapshotUnits().get(fiber));
                    assertEquals(4_800L, staged.snapshotUnits().get(phlogiston));
                });
        assertEquals(List.of(0), result.consumedSourceIndexes());
        assertEquals(16, result.consumedCount(0));
        assertEquals(List.of(16), result.consumedCounts());
        assertEquals(4_800L, result.beaker().snapshotUnits().get(fiber));
        assertEquals(4_800L, result.beaker().snapshotUnits().get(phlogiston));
        assertEquals(30, source.count());
        assertEquals(600, perItem.totalUnits());
        assertEquals(300L, perItem.snapshotUnits().get(fiber));
        assertEquals(300L, perItem.snapshotUnits().get(phlogiston));
        assertEquals(0, originalBeaker.totalUnits());
        assertEquals(1, reactions[0]);
    }

    @Test void mixedSourcesPreserveOrderedPartialEmptyAndNoneligibleOutcomes() {
        var first = amount(100);
        var emptyStack = new GrindingPlan.Source(new ReagentAttachment(), true, 5, true);
        var partial = new GrindingPlan.Source(amount(300), true, 30, true);
        var emptyWhole = new GrindingPlan.Source(new ReagentAttachment(), true);
        var missing = new GrindingPlan.Source(amount(100), true);
        var ineligible = new GrindingPlan.Source(amount(100), false);
        var order = new java.util.ArrayList<Long>();
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(first, true), emptyStack, partial,
                        emptyWhole, missing, ineligible), new ReagentAttachment(), 1_000,
                staged -> order.add(staged.totalUnits()));
        assertEquals(List.of(0, 2, 3), result.consumedSourceIndexes());
        assertEquals(List.of(1, 3, 1), result.consumedCounts());
        assertEquals(1, result.consumedCount(0));
        assertEquals(0, result.consumedCount(1));
        assertEquals(3, result.consumedCount(2));
        assertEquals(1, result.consumedCount(3));
        assertEquals(0, result.consumedCount(4));
        assertEquals(0, result.consumedCount(5));
        assertEquals(1_000, result.beaker().totalUnits());
        assertEquals(List.of(100L, 1_000L), order);
    }

    @Test void emptyStacksRemainButEligibleEmptyNonstackSourcesAreConsumed() {
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(new ReagentAttachment(), true, 1, true),
                        new GrindingPlan.Source(new ReagentAttachment(), true)), new ReagentAttachment(), 1000, ignored -> { });
        assertEquals(List.of(1), result.consumedSourceIndexes());
        assertEquals(1, result.consumedCount(1));
    }

    @Test void stackWithNoFittingUnitIsRetained() {
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(amount(300), true, 30, true)),
                amount(999), 1000, ignored -> { });
        assertTrue(result.consumedSourceIndexes().isEmpty());
        assertEquals(999, result.beaker().totalUnits());
    }
}
