package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.reagent.HeldLiquidTransferPlan;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UdderTransferPlanTest {
    private static final ResourceKey<ReagentData> MILK = ModReagents.createKey("milk");
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");

    @Test void limitedRecipientGetsMilkAndSourceDepletesExactlyOnceWhenCallerCommitsPlan() {
        var source = new ReagentAttachment(Map.of(MILK, 25f));
        var held = new ReagentAttachment(Map.of(WATER, 98f));
        var beforeSource = source.snapshotUnits(); var beforeHeld = held.snapshotUnits();
        var plan = HeldLiquidTransferPlan.planDetached(source, held, 10_000, 100f, 10_000);
        assertEquals(200, plan.admittedCents());
        assertEquals(beforeSource, source.snapshotUnits());
        assertEquals(beforeHeld, held.snapshotUnits());
        assertEquals(98f, plan.recipientAfter().getMap().get(WATER));
        assertEquals(2f, plan.recipientAfter().getMap().get(MILK));
        assertEquals(23f, plan.sourceAfter().getMap().get(MILK));
    }

    @Test void fullOrEmptySourceDoesNotStageAnyMutation() {
        var empty = new ReagentAttachment();
        var full = new ReagentAttachment(Map.of(WATER, 100f));
        var emptyPlan = HeldLiquidTransferPlan.planDetached(empty, new ReagentAttachment(), 10_000, 100f, 10_000);
        var fullPlan = HeldLiquidTransferPlan.planDetached(new ReagentAttachment(Map.of(MILK, 25f)), full,
                10_000, 100f, 10_000);
        assertEquals(0, emptyPlan.admittedCents());
        assertEquals(0, fullPlan.admittedCents());
        assertTrue(empty.snapshotUnits().isEmpty());
        assertEquals(Map.of(WATER, 10_000L), full.snapshotUnits());
    }

    @Test void detachedTransferPlanAloneDoesNotMutateEitherOwner() {
        var source = new ReagentAttachment(Map.of(MILK, 25f));
        var held = new ReagentAttachment();
        var plan = HeldLiquidTransferPlan.planDetached(source, held, 10_000, 100f, 10_000);
        // This verifies staging, not an authority/timeout integration; only the owning live commit mutates either side.
        assertEquals(25f, source.getMap().get(MILK));
        assertTrue(held.getMap().isEmpty());
        assertTrue(plan.sourceAfter().getMap().isEmpty());
    }
}
