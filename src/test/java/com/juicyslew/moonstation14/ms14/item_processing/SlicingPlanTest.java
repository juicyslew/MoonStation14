package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SlicingPlanTest {
    private static ResourceKey<ReagentData> key(String name) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.parse("test:" + name));
    }
    private static long total(Map<ResourceKey<ReagentData>, Long> contents) {
        return contents.values().stream().mapToLong(Long::longValue).sum();
    }

    @Test void floorRequestDropsSourceRemainderWhenFiveIndependentSlicesAreMade() {
        var five = SlicingPlan.prepare(Map.of(key("nutrient"), 101L), 5, 100L);
        assertEquals(5, five.size());
        assertTrue(five.stream().allMatch(slice -> total(slice) == 20));
        assertEquals(100, five.stream().mapToLong(SlicingPlanTest::total).sum());
        assertEquals(1, 101 - five.stream().mapToLong(SlicingPlanTest::total).sum());
        var three = SlicingPlan.prepare(Map.of(key("nutrient"), 103L), 5, 100L);
        assertTrue(three.stream().allMatch(slice -> total(slice) == 20));
        assertEquals(3, 103 - three.stream().mapToLong(SlicingPlanTest::total).sum());
    }

    @Test void mixedSolutionUsesCanonicalSplitAndCapacityCapsEachRequest() {
        var a = key("a"); var b = key("b");
        var slices = SlicingPlan.prepare(Map.of(a, 51L, b, 52L), 5, 10L);
        assertEquals(5, slices.size());
        for (var slice : slices) {
            assertEquals(10, total(slice));
            assertEquals(Map.of(a, 5L, b, 5L), slice);
        }
        assertTrue(SlicingPlan.prepare(Map.of(a, 1L), 5, 0L).stream().allMatch(slice -> total(slice) == 0));
    }

    @Test void sequentialExtractionNeverDuplicatesSpeciesAndCapacityLossIsNotReallocated() {
        var a = key("a"); var b = key("b");
        var tiny = SlicingPlan.prepare(Map.of(a, 2L, b, 1L), 3, 100L);
        assertEquals(java.util.List.of(Map.of(a, 1L, b, 0L), Map.of(a, 1L, b, 0L), Map.of(b, 1L)), tiny);
        assertEquals(2, tiny.stream().mapToLong(slice -> slice.getOrDefault(a, 0L)).sum());
        assertEquals(1, tiny.stream().mapToLong(slice -> slice.getOrDefault(b, 0L)).sum());

        var capped = SlicingPlan.prepare(Map.of(a, 3L, b, 1L), 2, 1L);
        assertEquals(java.util.List.of(Map.of(a, 1L, b, 0L), Map.of(a, 1L, b, 0L)), capped);
        assertEquals(2, capped.stream().mapToLong(slice -> slice.getOrDefault(a, 0L)).sum(),
                "extracted/capacity-overflow cents are lost rather than reallocated");
        assertEquals(0, capped.stream().mapToLong(slice -> slice.getOrDefault(b, 0L)).sum());
    }

    @Test void rejectsInvalidCountsAndReagentCentAmounts() {
        assertThrows(IllegalArgumentException.class, () -> SlicingPlan.prepare(Map.of(), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> SlicingPlan.prepare(Map.of(key("a"), -1L), 1, 10));
    }
}
