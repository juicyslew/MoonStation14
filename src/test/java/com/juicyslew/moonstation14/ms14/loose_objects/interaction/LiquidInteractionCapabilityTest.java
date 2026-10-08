package com.juicyslew.moonstation14.ms14.loose_objects.interaction;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiquidInteractionCapabilityTest {
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");
    private static final ResourceKey<ReagentData> SUGAR = ModReagents.createKey("sugar");

    @Test
    void transfersFiveUnitDose() {
        var plan = LiquidInteractionCapability.plan(Map.of(WATER, 1_000L), Map.of(), 10_000);
        assertEquals(500, plan.transferredCents());
        assertEquals(Map.of(WATER, 500L), plan.sourceAfter());
        assertEquals(Map.of(WATER, 500L), plan.destinationAfter());
    }

    @Test
    void limitsDoseToPartialSource() {
        var plan = LiquidInteractionCapability.plan(Map.of(WATER, 125L), Map.of(), 10_000);
        assertEquals(125, plan.transferredCents());
        assertTrue(plan.sourceAfter().isEmpty());
        assertEquals(Map.of(WATER, 125L), plan.destinationAfter());
    }

    @Test
    void selectedSyringeDoseDoesNotOverdrawSource() {
        var plan = LiquidInteractionCapability.plan(Map.of(WATER, 1_500L), Map.of(), 1_500L, 500L);
        assertEquals(500L, plan.transferredCents());
        assertEquals(Map.of(WATER, 1_000L), plan.sourceAfter());
        assertEquals(Map.of(WATER, 500L), plan.destinationAfter());
    }

    @Test
    void selectedSyringeDoseCanExceedDefaultFiveUnits() {
        var plan = LiquidInteractionCapability.plan(Map.of(WATER, 2_000L), Map.of(), 1_500L, 1_500L);
        assertEquals(1_500L, plan.transferredCents());
        assertEquals(500L, plan.sourceAfter().get(WATER));
        assertEquals(1_500L, plan.destinationAfter().get(WATER));
    }

    @Test
    void nonDoseTransferUsesResolvedDestinationCapacity() {
        // Models a server-resolved syringe capacity smaller than its compatibility/client capacity.
        var plan = LiquidInteractionCapability.plan(Map.of(WATER, 500L), Map.of(SUGAR, 1_000L), 1_500L);
        assertEquals(500L, plan.transferredCents());
        assertEquals(Map.of(WATER, 500L, SUGAR, 1_000L), plan.destinationAfter());
    }

    @Test
    void fullDestinationAdmitsNothing() {
        var source = Map.of(WATER, 1_000L);
        var destination = Map.of(SUGAR, 10_000L);
        var plan = LiquidInteractionCapability.plan(source, destination, 10_000);
        assertEquals(0, plan.transferredCents());
        assertEquals(source, plan.sourceAfter());
        assertEquals(destination, plan.destinationAfter());
    }

    @Test
    void conservesMixedReagentCentsExactly() {
        var source = Map.of(WATER, 333L, SUGAR, 777L);
        var destination = Map.of(WATER, 41L);
        var plan = LiquidInteractionCapability.plan(source, destination, 10_000);
        assertEquals(500, plan.transferredCents());
        for (var key : java.util.Set.of(WATER, SUGAR)) {
            assertEquals(source.getOrDefault(key, 0L) + destination.getOrDefault(key, 0L),
                    plan.sourceAfter().getOrDefault(key, 0L) + plan.destinationAfter().getOrDefault(key, 0L));
        }
        assertEquals(1_151, total(plan.sourceAfter()) + total(plan.destinationAfter()));
    }

    @Test
    void malformedNegativeAmountsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> LiquidInteractionCapability.plan(Map.of(WATER, -1L), Map.of(), 10_000));
    }

    private static long total(Map<ResourceKey<ReagentData>, Long> amounts) {
        return amounts.values().stream().mapToLong(Long::longValue).sum();
    }
}
