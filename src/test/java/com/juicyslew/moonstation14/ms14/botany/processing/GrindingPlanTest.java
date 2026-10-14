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
    private static ReagentAttachment amount(long cents) { return new ReagentAttachment(java.util.Map.of(A, cents / 100f)); }

    @Test void wholeSourcesSkipNonfittingAndContinueToLaterFitAndRetainEmpty() {
        var empty = new GrindingPlan.Source(new ReagentAttachment(), true);
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(amount(2000), true),
                new GrindingPlan.Source(amount(2000), true), new GrindingPlan.Source(amount(500), true), empty),
                new ReagentAttachment(), 2500, ignored -> { });
        assertEquals(List.of(0, 2), result.consumedSourceIndexes());
        assertEquals(2500, result.beaker().totalUnits());
    }

    @Test void emptyAndIncapableSourcesAndInsufficientCapacityAreNotConsumed() {
        var result = GrindingPlan.prepare(List.of(new GrindingPlan.Source(new ReagentAttachment(), true),
                new GrindingPlan.Source(amount(100), false), new GrindingPlan.Source(amount(200), true)), amount(900), 1000,
                ignored -> { });
        assertTrue(result.consumedSourceIndexes().isEmpty());
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
}
