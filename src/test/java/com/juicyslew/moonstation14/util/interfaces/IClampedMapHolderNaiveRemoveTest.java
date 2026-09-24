package com.juicyslew.moonstation14.util.interfaces;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IClampedMapHolderNaiveRemoveTest {
    @Test
    void emptyAndZeroOnlySourcesAreCleanedWithoutNaN() {
        Holder empty = new Holder(Map.of());
        assertTrue(empty.naiveRemove(1f).isEmpty());
        assertTrue(empty.map.isEmpty());

        Holder zeroOnly = new Holder(Map.of("zero", 0f));
        Map<String, Float> removed = zeroOnly.naiveRemove(80f / 6f);
        assertTrue(removed.isEmpty());
        assertTrue(zeroOnly.map.isEmpty());
        assertFinite(removed);
    }

    @Test
    void zeroRequestPreservesPositiveValuesAndTrimsZeros() {
        Holder holder = new Holder(Map.of("positive", 2f, "zero", 0f));

        assertTrue(holder.naiveRemove(0f).isEmpty());

        assertEquals(Map.of("positive", 2f), holder.map);
    }

    @Test
    void partialRequestRemovesProportionallyAndPreservesMass() {
        Holder holder = new Holder(Map.of("a", 2f, "b", 6f, "zero", 0f));

        Map<String, Float> removed = holder.naiveRemove(2f);

        assertEquals(0.5f, removed.get("a"), 0.000001f);
        assertEquals(1.5f, removed.get("b"), 0.000001f);
        assertTrue(!removed.containsKey("zero"));
        assertEquals(1.5f, holder.map.get("a"), 0.000001f);
        assertEquals(4.5f, holder.map.get("b"), 0.000001f);
        assertTrue(!holder.map.containsKey("zero"));
        assertEquals(2f, total(removed), 0.000001f);
        assertEquals(6f, total(holder.map), 0.000001f);
        assertFinite(removed);
        assertFinite(holder.map);
    }

    @Test
    void largeSourceTinyRequestReportsActualRepresentableRemoval() {
        float requested = 0.01f;
        Holder holder = new Holder(Map.of("large", 200000f));

        double sourceBefore = total(holder.map);
        Map<String, Float> removed = holder.naiveRemove(requested);
        double sourceDepletion = sourceBefore - total(holder.map);
        double removedTotal = total(removed);

        assertEquals(sourceDepletion, removedTotal);
        assertTrue(Double.isFinite(sourceDepletion));
        assertTrue(Double.isFinite(removedTotal));
        assertTrue(removedTotal <= requested);
    }

    @Test
    void fullAndExcessRequestsReturnOriginalQuantitiesAndEmptySource() {
        Holder holder = new Holder(Map.of("a", 2f, "b", 3f, "zero", 0f));

        Map<String, Float> removed = holder.naiveRemove(10f);

        assertEquals(Map.of("a", 2f, "b", 3f), removed);
        assertTrue(holder.map.isEmpty());
    }

    @Test
    void invalidRequestsAreRejectedWithoutMutation() {
        for (float amount : new float[]{-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            Holder holder = new Holder(Map.of("a", 2f, "zero", 0f));
            Map<String, Float> before = new HashMap<>(holder.map);

            assertThrows(IllegalArgumentException.class, () -> holder.naiveRemove(amount));
            assertEquals(before, holder.map);
        }
    }

    @Test
    void invalidStoredStateFailsTransactionally() {
        Map<String, Float> values = new HashMap<>();
        values.put("valid", 2f);
        values.put("nan", Float.NaN);
        values.put("negative", -1f);
        Holder holder = new Holder(values);
        Map<String, Float> before = new HashMap<>(holder.map);

        assertThrows(IllegalArgumentException.class, () -> holder.naiveRemove(1f));

        assertEquals(before, holder.map);
    }

    @Test
    void vomitPreconditionCleansZeroReagentSource() {
        ResourceKey<ReagentData> key = ModReagents.createKey("naive-remove-vomit");
        ReagentAttachment attachment = new ReagentAttachment(Map.of(key, 0f));

        Map<ResourceKey<ReagentData>, Float> removed = attachment.naiveRemove(80f / 6f);

        assertTrue(removed.isEmpty());
        assertTrue(attachment.getMap().isEmpty());
        assertFinite(removed);
    }

    private static double total(Map<?, Float> values) {
        return values.values().stream().mapToDouble(Float::doubleValue).sum();
    }

    private static void assertFinite(Map<?, Float> values) {
        assertTrue(values.values().stream().allMatch(value -> value != null && Float.isFinite(value)));
    }

    private static final class Holder implements IClampedMapHolder<String> {
        private final Map<String, Float> map;

        private Holder(Map<String, Float> values) {
            this.map = new HashMap<>(values);
        }

        @Override
        public Map<String, Float> getMap() {
            return map;
        }
    }
}
