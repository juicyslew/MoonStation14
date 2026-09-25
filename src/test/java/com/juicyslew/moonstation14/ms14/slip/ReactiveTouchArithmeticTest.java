package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveTouchArithmeticTest {
    private static ResourceKey<ReagentData> key(String name) {
        return ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("test", name));
    }

    @Test
    void pinnedHalfChanceBoundaryIsExplicit() {
        assertTrue(ReactiveTouchSystem.chanceAccepted(0f));
        assertTrue(ReactiveTouchSystem.chanceAccepted(Math.nextDown(.5f)));
        assertFalse(ReactiveTouchSystem.chanceAccepted(.5f));
        assertFalse(ReactiveTouchSystem.chanceAccepted(1f));
        assertFalse(ReactiveTouchSystem.chanceAccepted(Float.NaN));
    }

    @Test
    void fifteenPercentUsesRawCentsAndPinnedTruncationEpsilon() {
        assertEquals(300, ReactiveTouchSystem.touchRequestCents(2_000));
        assertEquals(1, ReactiveTouchSystem.touchRequestCents(10));
        assertEquals(0, ReactiveTouchSystem.touchRequestCents(6));
        assertEquals(15_000, ReactiveTouchSystem.touchRequestCents(100_000));
        // Golden values immediately on either side of the one-cent request transition.
        assertEquals(0, ReactiveTouchSystem.touchRequestCents(6));
        assertEquals(1, ReactiveTouchSystem.touchRequestCents(7));
        assertEquals(2, ReactiveTouchSystem.touchRequestCents(19));
        assertEquals(3, ReactiveTouchSystem.touchRequestCents(20));
    }

    @Test
    void splitPreservesEveryReagentAndConservesTheRequestedDose() {
        ResourceKey<ReagentData> acid = key("acid");
        ResourceKey<ReagentData> lube = key("lube");
        Map<ResourceKey<ReagentData>, Long> initial = new LinkedHashMap<>();
        initial.put(acid, 400L);
        initial.put(lube, 1_600L);
        long requested = ReactiveTouchSystem.touchRequestCents(ReagentUnits.total(initial.values()));
        ReagentAttachment attachment = new ReagentAttachment();
        initial.forEach((reagent, cents) -> attachment.specificAdd(reagent,
                ReagentUnits.toFloat(cents), 100f));
        Map<ResourceKey<ReagentData>, Long> removed = attachment.splitUnits(requested);
        assertEquals(300L, ReagentUnits.total(removed.values()));
        assertEquals(60L, removed.get(acid));
        assertEquals(240L, removed.get(lube));
        assertEquals(340L, initial.get(acid) - removed.get(acid));
        assertEquals(1_360L, initial.get(lube) - removed.get(lube));
        assertEquals(1_700L, initial.values().stream().mapToLong(Long::longValue).sum()
                - removed.values().stream().mapToLong(Long::longValue).sum());
    }
}
