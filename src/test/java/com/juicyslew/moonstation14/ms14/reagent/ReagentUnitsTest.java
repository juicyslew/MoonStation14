package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReagentUnitsTest {
    private static final ResourceKey<ReagentData> A = key("a");
    private static final ResourceKey<ReagentData> B = key("b");

    @Test
    void convertsCanonicalDecimalValuesAtHundredthPrecisionWithoutEpsilon() {
        assertEquals(0, ReagentUnits.fromFloat(0f));
        assertEquals(0, ReagentUnits.fromFloat(.005f));
        assertEquals(0, ReagentUnits.fromFloat(.009f));
        assertEquals(0, ReagentUnits.fromFloat(.00999999f));
        assertEquals(0, ReagentUnits.fromFloat(.009999999f));
        assertEquals(1, ReagentUnits.fromFloat(.01f));
        assertEquals(99, ReagentUnits.fromFloat(.9999999f));
        assertEquals(12, ReagentUnits.fromFloat(.125f));
        assertEquals(4990, ReagentUnits.fromFloat(49.9f));
        assertEquals(20_000_001, ReagentUnits.fromDouble(200_000.01d));
        assertEquals(0, ReagentUnits.fromFloat(Float.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.fromFloat(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.fromFloat(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.fromFloat(-.01f));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.fromFloat(Float.MAX_VALUE));
    }

    @Test
    void repeatedSubcentPublicInputsNeverAccumulateIntoACent() {
        ReagentAttachment attachment = new ReagentAttachment();
        for (int i = 0; i < 100; i++) {
            attachment.specificAdd(A, .00999999f, 1f);
        }
        assertEquals(Map.of(), attachment.snapshotUnits());
        assertEquals(0, attachment.totalUnits());
    }

    @Test
    void roundTripsFloatRepresentableCentsThroughTwentyMillionWithinOneCent() {
        for (long cents = 0; cents <= 20_000_000; cents++) {
            long roundTrip = ReagentUnits.fromFloat(ReagentUnits.toFloat(cents));
            // Above the exact float-cent range, float ULP itself exceeds one cent.
            assertTrue(Math.abs(roundTrip - cents) <= 2, "cents=" + cents + ", roundTrip=" + roundTrip);
        }
    }

    @Test
    void roundTripsEveryCentAtTheStomachCapacityExactly() {
        for (long cents = 0; cents <= 5_000; cents++) {
            assertEquals(cents, ReagentUnits.fromFloat(ReagentUnits.toFloat(cents)), "cents=" + cents);
        }
    }

    @Test
    void snapshotsFloatMapAsImmutableLocationSortedCentMap() {
        Map<ResourceKey<ReagentData>, Float> source = new HashMap<>();
        source.put(B, .03f);
        source.put(A, .01f);
        Map<ResourceKey<ReagentData>, Long> snapshot = ReagentUnits.fromMap(source);
        assertEquals(List.of(A, B), List.copyOf(snapshot.keySet()));
        assertEquals(Map.of(A, 1L, B, 3L), snapshot);
        source.put(A, 2f);
        assertEquals(1L, snapshot.get(A));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(A, 9L));
    }

    @Test
    void proportionalSplitConservesRequestedCentsAndUsesStableRemainderOrder() {
        Map<ResourceKey<ReagentData>, Long> reversed = new LinkedHashMap<>();
        reversed.put(B, 3L);
        reversed.put(A, 1L);
        Map<ResourceKey<ReagentData>, Long> split = ReagentUnits.split(reversed, 2);
        assertEquals(List.of(A, B), List.copyOf(split.keySet()));
        assertEquals(Map.of(A, 1L, B, 1L), split);
        assertEquals(2, ReagentUnits.total(split.values()));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.split(reversed, 5));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.split(reversed, -1));
    }

    @Test
    void enforcesBoundedCapacityAndOverflowAndDefinesLossyScaling() {
        assertEquals(ReagentUnits.MAX_CENTS, ReagentUnits.total(List.of(ReagentUnits.MAX_CENTS)));
        assertTrue(Float.isFinite(ReagentUnits.toFloat(ReagentUnits.MAX_CENTS)));
        assertThrows(IllegalArgumentException.class,
                () -> ReagentUnits.total(List.of(ReagentUnits.MAX_CENTS, 1L)));
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.total(List.of(-1L)));
        assertEquals(5, ReagentUnits.admit(10, 5));
        assertEquals(10, ReagentUnits.admit(10, 20));
        assertEquals(3, ReagentUnits.scale(7, .5));
        assertEquals(0, ReagentUnits.scale(1, .5));
    }

    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }
}
