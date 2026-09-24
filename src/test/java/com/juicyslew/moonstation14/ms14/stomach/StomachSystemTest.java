package com.juicyslew.moonstation14.ms14.stomach;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StomachSystemTest {
    private static final ResourceKey<ReagentData> WATER = key("water");
    private static final ResourceKey<ReagentData> SUGAR = key("sugar");

    @Test
    void boundedAdmissionRetainsMixturesAndReportsOverfullRemainder() {
        ReagentAttachment stomach = new ReagentAttachment(Map.of(WATER, 49f));
        var result = stomach.addCapacitySafe(Map.of(WATER, 2f, SUGAR, 2f), StomachSystem.CAPACITY);
        assertEquals(50f, total(stomach.getMap()), 0.0001f);
        assertEquals(1f, total(result.retained()), 0.0001f);
        assertEquals(3f, total(result.excess()), 0.0001f);
        assertEquals(.5f, result.retained().get(WATER), 0.0001f);
        assertEquals(.5f, result.retained().get(SUGAR), 0.0001f);
    }

    @Test
    void invalidMapsAndCapacityFailWithoutMutation() {
        ReagentAttachment stomach = new ReagentAttachment(Map.of(WATER, 49f));
        assertThrows(IllegalArgumentException.class,
                () -> stomach.addCapacitySafe(Map.of(WATER, Float.NaN), StomachSystem.CAPACITY));
        assertThrows(IllegalArgumentException.class,
                () -> stomach.addCapacitySafe(Map.of(WATER, 1f), Float.POSITIVE_INFINITY));
        assertEquals(Map.of(WATER, 49f), stomach.getMap());
        assertThrows(IllegalArgumentException.class, () -> new ReagentAttachment(Map.of(WATER, -1f)));
        assertThrows(IllegalArgumentException.class, () -> new ReagentAttachment(Map.of(WATER, Float.POSITIVE_INFINITY)));
    }

    @Test
    void zeroAndAlreadyFullAdmissionRetainNothing() {
        ReagentAttachment stomach = new ReagentAttachment();
        var zero = stomach.addCapacitySafe(Map.of(WATER, 0f), StomachSystem.CAPACITY);
        assertEquals(0f, total(zero.retained()));
        stomach.addCapacitySafe(Map.of(WATER, StomachSystem.CAPACITY), StomachSystem.CAPACITY);
        var full = stomach.addCapacitySafe(Map.of(SUGAR, 1f), StomachSystem.CAPACITY);
        assertEquals(0f, total(full.retained()));
        assertEquals(1f, total(full.excess()));
        assertEquals(StomachSystem.CAPACITY, total(stomach.getMap()));
    }

    @Test
    void integerCentsAdmitPartialSipAndConserveAwkwardSameKeyAmounts() {
        Map<ResourceKey<ReagentData>, Long> source = ReagentUnits.fromMap(Map.of(WATER, 10.1f));
        Map<ResourceKey<ReagentData>, Long> stomach = ReagentUnits.fromMap(Map.of(WATER, 49.9f));
        assertEquals(1_010L, ReagentUnits.total(source.values()));
        assertEquals(4_990L, ReagentUnits.total(stomach.values()));
        long accepted = ReagentUnits.admit(ReagentUnits.fromFloat(.1f), 5_000L - ReagentUnits.total(stomach.values()));
        Map<ResourceKey<ReagentData>, Long> removed = ReagentUnits.split(source, accepted);
        assertEquals(10L, accepted);
        assertEquals(removed, Map.of(WATER, 10L));
        assertEquals(1_000L, source.get(WATER) - removed.get(WATER));
        assertEquals(5_000L, stomach.get(WATER) + removed.get(WATER));
    }

    @Test
    void proportionalIntegerSplitUsesCanonicalRemainderAndExactTotal() {
        Map<ResourceKey<ReagentData>, Long> source = Map.of(SUGAR, 1_010L, WATER, 1_010L);
        Map<ResourceKey<ReagentData>, Long> split = ReagentUnits.split(source, 10L);
        assertEquals(Map.of(WATER, 5L, SUGAR, 5L), split);
        assertEquals(10L, ReagentUnits.total(split.values()));

        Map<ResourceKey<ReagentData>, Long> uneven = ReagentUnits.split(Map.of(WATER, 1L, SUGAR, 9L), 5L);
        assertEquals(0L, uneven.get(WATER));
        assertEquals(5L, uneven.get(SUGAR), "first canonical key receives the deterministic remainder cent");
        assertEquals(5L, ReagentUnits.total(uneven.values()));
    }

    @Test
    void conversionFloorsSubcentOnceAndRejectsInvalidOrOverflowingQuantities() {
        assertEquals(0L, ReagentUnits.fromFloat(.005f));
        assertEquals(1L, ReagentUnits.fromFloat(.01f));
        assertEquals(0L, ReagentUnits.fromFloat(Float.MIN_VALUE), "sub-cent source quantity floors away");
        assertThrows(IllegalArgumentException.class, () -> ReagentUnits.fromFloat(Float.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> ReagentUnits.total(java.util.List.of(ReagentUnits.MAX_CENTS, 1L)));
    }

    @Test
    void repeatedFractionalSipsCannotCreateSubcentMaterial() {
        long source = ReagentUnits.fromFloat(10f);
        long stomach = 0L;
        for (int i = 0; i < 100; i++) {
            long accepted = ReagentUnits.admit(ReagentUnits.fromFloat(.015f), source);
            source -= accepted;
            stomach = Math.addExact(stomach, accepted);
        }
        assertEquals(900L, source);
        assertEquals(100L, stomach, "each requested 1.5 cents floors to one cent; none is created");
    }

    @Test
    void freeSpaceAndSourceBelowOneCentAdmitNothing() {
        assertEquals(0L, ReagentUnits.admit(100L, 0L));
        assertEquals(0L, ReagentUnits.admit(0L, 10L));
        assertEquals(0L, ReagentUnits.fromFloat(.005f));
        assertEquals(4_999L, ReagentUnits.total(ReagentUnits.fromMap(Map.of(WATER, 49.999f)).values()));
    }

    @Test
    void reagentCodecRoundTripsStomachContents() {
        ReagentComponent original = new ReagentComponent(Map.of(WATER, 12.5f, SUGAR, 3.25f));
        assertEquals(original, ReagentComponent.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE,
                        ReagentComponent.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, original).getOrThrow())
                .getOrThrow());
        assertEquals(original, new ReagentAttachment(original).toComponent());
    }

    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    private static float total(Map<?, Float> values) {
        return (float) values.values().stream().mapToDouble(Float::doubleValue).sum();
    }

}
