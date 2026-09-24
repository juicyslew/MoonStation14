package com.juicyslew.moonstation14.ms14.metabolism;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetabolismFoundationTest {
    private static final ResourceKey<ReagentData> A = ModReagents.createKey("foundation-a");
    private static final ResourceKey<ReagentData> B = ModReagents.createKey("foundation-b");

    @Test
    void stagesUseClosedCanonicalLowercaseCodec() {
        for (MetabolismStage stage : MetabolismStage.values()) {
            var json = MetabolismStage.CODEC.encodeStart(JsonOps.INSTANCE, stage).getOrThrow();
            assertEquals(stage, MetabolismStage.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            assertEquals(stage.serializedName(), json.getAsString());
        }
        assertTrue(MetabolismStage.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"anatomy\"")).error().isPresent());
    }

    @Test
    void absentMetabolismsStayEmptyAndFlatStagesRoundTrip() {
        ReagentData absent = ReagentData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"empty\"}")).getOrThrow();
        assertTrue(absent.metabolisms().isEmpty());

        var json = JsonParser.parseString("""
                {"id":"staged","metabolisms":{
                  "respiration":{"metabolismrate":1.0},
                  "digestion":{"metabolites":{"foundation-a":1.5}},
                  "bloodstream":{},"metabolites":{}}
                }""").getAsJsonObject();
        ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(4, decoded.metabolisms().size());
        assertEquals(decoded, ReagentData.CODEC.parse(JsonOps.INSTANCE,
                ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).getOrThrow());
        assertTrue(ReagentData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"bad\",\"metabolisms\":{\"anatomy\":{}}}"))
                .error().isPresent());
    }

    @Test
    void metabolismMathUsesActualRemovalAndCopiesProduction() {
        Map<String, Float> ratios = Map.of("a", 1.5f, "b", 0.5f);
        MetabolismResult<String> result = MetabolismMath.metabolize(2f, 4f, ratios);
        assertEquals(2f, result.actualRemoved());
        assertEquals(0.5f, result.scale());
        assertEquals(3f, result.requestedProduction().get("a"));
        assertEquals(1f, result.requestedProduction().get("b"));
        assertThrows(UnsupportedOperationException.class, () -> result.requestedProduction().put("x", 1f));
        assertEquals(0f, MetabolismMath.metabolize(0f, 4f, ratios).actualRemoved());
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(-1f, 4f, ratios));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 0f, ratios));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 4f, Map.of("a", -1f)));
    }

    @Test
    void metabolismMathCoversFullZeroAndMultipleProductScaling() {
        Map<String, Float> ratios = new HashMap<>();
        ratios.put("a", 1.5f);
        ratios.put("b", 2f);
        MetabolismResult<String> full = MetabolismMath.metabolize(4f, 2f, ratios);
        assertEquals(2f, full.actualRemoved());
        assertEquals(1f, full.scale());
        assertEquals(3f, full.production().get("a"));
        assertEquals(4f, full.production().get("b"));

        MetabolismResult<String> partial = MetabolismMath.metabolize(1f, 2f, ratios);
        assertEquals(1f, partial.actualRemoved());
        assertEquals(0.5f, partial.scale());
        assertEquals(1.5f, partial.production().get("a"));
        assertEquals(2f, partial.production().get("b"));

        MetabolismResult<String> zero = MetabolismMath.metabolize(0f, 2f, ratios);
        assertEquals(0f, zero.actualRemoved());
        assertEquals(0f, zero.scale());
        assertEquals(0f, zero.production().get("a"));
        assertEquals(0f, zero.production().get("b"));

        ratios.put("a", 99f);
        assertEquals(1.5f, partial.production().get("a"));
        assertThrows(UnsupportedOperationException.class, () -> partial.production().put("new", 1f));
    }

    @Test
    void metabolismMathRejectsEveryInvalidFiniteDomainValue() {
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(-1f, 1f, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(Float.NaN, 1f, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(Float.POSITIVE_INFINITY, 1f, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(Float.NEGATIVE_INFINITY, 1f, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, -1f, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 0f, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, Float.NaN, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, Float.POSITIVE_INFINITY, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, Float.NEGATIVE_INFINITY, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 1f, Map.of("nan", Float.NaN)));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 1f, Map.of("infinity", Float.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 1f, Map.of("negative-infinity", Float.NEGATIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> MetabolismMath.metabolize(1f, 1f, Map.of("negative", -1f)));
    }

    @Test
    void attachmentReturnsActualRemovalAndCapacityAccounting() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 3f));
        assertEquals(2f, attachment.removeUpTo(A, 2f));
        assertEquals(1f, attachment.getMap().get(A));
        assertEquals(1f, attachment.removeUpTo(A, 5f));
        assertTrue(attachment.getMap().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> attachment.removeUpTo(A, -1f));

        var result = attachment.addCapacitySafe(Map.of(A, 1f, B, 2f), 1.5f);
        assertEquals(1.5f, attachment.getMap().values().stream().mapToDouble(Float::doubleValue).sum(), 0.00001);
        assertEquals(0.5f, result.retained().get(A), 0.00001);
        assertEquals(1f, result.retained().get(B), 0.00001);
        assertEquals(0.5f, result.excess().get(A), 0.00001);
        assertEquals(1f, result.excess().get(B), 0.00001);
        assertThrows(UnsupportedOperationException.class, () -> result.retained().put(A, 1f));
        assertThrows(IllegalArgumentException.class, () -> attachment.addCapacitySafe(Map.of(A, -1f), 3f));
    }

    @Test
    void capacityCorrectionOnlyRemovesNewQuantityFromAnExistingKey() {
        // The first request is below the representable increment at the
        // existing value. The second request puts the aggregate exactly on a
        // float boundary where the final correction is required.
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 0.2f));
        Map<ResourceKey<ReagentData>, Float> before = new HashMap<>(attachment.getMap());

        var result = attachment.addCapacitySafe(Map.of(A, 0.00000001f, B, 0.5f), 0.7f);

        assertTrue(attachment.getMap().get(A) >= before.get(A));
        assertEquals(before.get(A), attachment.getMap().get(A));
        assertEquals(0f, result.retained().get(A));
        assertEquals(0.5f, result.retained().get(B), 0.000001f);
        assertEquals(0f, result.excess().get(A), 0f);
        assertEquals(0f, result.excess().get(B), 0.000001f);
    }

    @Test
    void capacityValidationIsTransactionalAndEqualCapacityRetainsNothing() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 1f));
        Map<ResourceKey<ReagentData>, Float> before = new HashMap<>(attachment.getMap());
        assertThrows(IllegalArgumentException.class,
                () -> attachment.addCapacitySafe(Map.of(A, 1f), 0.5f));
        assertEquals(before, attachment.getMap());

        assertThrows(IllegalArgumentException.class,
                () -> attachment.addCapacitySafe(Map.of(A, 1f, B, Float.NaN), 2f));
        assertEquals(before, attachment.getMap());

        var result = attachment.addCapacitySafe(Map.of(A, 1f, B, 2f), 1f);
        assertEquals(before, attachment.getMap());
        assertEquals(0f, result.retained().get(A));
        assertEquals(0f, result.retained().get(B));
        assertEquals(1f, result.excess().get(A));
        assertEquals(2f, result.excess().get(B));
        assertTrue(result.requested().keySet().containsAll(List.of(A, B)));
    }

    @Test
    void emptyAndZeroCapacityAddsAreSafeNoOpsWithImmutableFiniteResults() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 1f));
        Map<ResourceKey<ReagentData>, Float> before = new HashMap<>(attachment.getMap());
        var empty = attachment.addCapacitySafe(Map.of(), 1f);
        var zero = attachment.addCapacitySafe(Map.of(A, 0f), 1f);
        assertEquals(before, attachment.getMap());
        assertTrue(empty.requested().isEmpty());
        assertEquals(0f, zero.retained().get(A));
        assertEquals(0f, zero.excess().get(A));
        assertThrows(UnsupportedOperationException.class, () -> zero.excess().put(A, 1f));
    }

    @Test
    void metabolismDataDefensivelyCopiesAndValidates() {
        var effects = new java.util.ArrayList<com.juicyslew.moonstation14.component.codec.json.EffectData>();
        var metabolites = new java.util.HashMap<ResourceKey<ReagentData>, Float>();
        metabolites.put(A, 2f);
        MetabolismData data = new MetabolismData(effects, metabolites, 0.5f);
        effects.add(null);
        metabolites.put(B, 1f);
        assertTrue(data.effects().isEmpty());
        assertEquals(1, data.metabolites().size());
        assertThrows(IllegalArgumentException.class, () -> new MetabolismData(List.of(), Map.of(), 0f));
        assertThrows(IllegalArgumentException.class, () -> new MetabolismData(List.of(), Map.of(A, -1f), 1f));
        assertThrows(IllegalArgumentException.class, () -> new MetabolismData(List.of(), Map.of(A, Float.NaN), 1f));
        assertThrows(IllegalArgumentException.class, () -> new MetabolismData(List.of(), Map.of(), Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MetabolismData(List.of(), Map.of(), Float.POSITIVE_INFINITY));
    }
}
