package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageReducerTest {
    @Test
    void deltaIsClampedZeroElidedAndMixedInOneReduction() {
        Map<String, Float> state = Map.of("blunt", 2f, "slash", 1f);
        assertEquals(Map.of("blunt", 3f, "slash", 0.5f),
                DamageReducer.applyDelta(state, Map.of("blunt", 1f, "slash", -0.5f)));
        assertEquals(Map.of("blunt", 2f),
                DamageReducer.applyDelta(state, Map.of("slash", -4f)));
        assertEquals(Map.of(), DamageReducer.applyDelta(state, Map.of("blunt", -2f, "slash", -1f)));
        assertEquals(state, DamageReducer.applyDelta(state, Map.of("blunt", 0f)));
    }

    @Test
    void stateAndDeltaRejectInvalidValuesAndKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new DamageMap(Map.of("pierce", 1f)));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageMap(Map.of("structural", 1f)));
        assertThrows(IllegalArgumentException.class,
                () -> DamageReducer.applyDelta(Map.of("blunt", Float.NaN), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageSpecifierData(Map.of("blunt", Float.POSITIVE_INFINITY)));
    }

    @Test
    void proportionalAllocationIsCanonicalAndDeterministic() {
        assertEquals(Map.of("blunt", 2f, "slash", 1f),
                DamageReducer.proportionalAllocation(Map.of("slash", 1f, "blunt", 2f), 3f));
        assertEquals(Map.of("blunt", 1f, "slash", 0.5f),
                DamageReducer.proportionalAllocation(Map.of("blunt", 2f, "slash", 1f), 1.5f));
    }

    @Test
    void postMitigationPositiveAllocationIsSeparateFromHealingAndCancellation() {
        assertEquals(Map.of("blunt", 2f, "slash", 1f),
                DamageReducer.mitigatedPositiveDelta(Map.of("blunt", 4f, "slash", 2f), 0.6f));
        assertEquals(Map.of(), DamageReducer.mitigatedPositiveDelta(Map.of("blunt", 4f), 0f));
        assertEquals(Map.of("blunt", 2f), DamageReducer.applyMitigated(Map.of("blunt", 4f),
                Map.of("blunt", 4f), 0f, Map.of("blunt", -2f)));
    }

    @Test
    void evenHealingRedistributesExcessAndKeepsGroupsIsolated() {
        Map<String, Float> state = Map.of("blunt", 2f, "piercing", 10f, "heat", 4f);
        assertEquals(Map.of("piercing", 6f, "heat", 4f),
                DamageReducer.healGroups(state, Map.of("brute", -6f)));
        assertEquals(Map.of("blunt", 2f, "piercing", 10f, "heat", 1f),
                DamageReducer.healGroups(state, Map.of("burn", -3f)));
        assertEquals(state, DamageReducer.healGroups(state, Map.of("brute", 0f, "burn", 2f)));
    }

    @Test
    void evenHealingExhaustsSmallMemberBeforeRedistributingToSurvivors() {
        Map<String, Float> state = Map.of("blunt", 1f, "piercing", 10f, "slash", 10f);
        assertEquals(Map.of("piercing", 7.5f, "slash", 7.5f),
                DamageReducer.healGroups(state, Map.of("brute", -6f)));
    }

    @Test
    void vanillaHealingAmountIsAggregateAcrossAllDamageKeys() {
        Map<String, Float> state = Map.of("blunt", 5f, "slash", 10f);
        assertEquals(Map.of("blunt", 2.5f, "slash", 7.5f),
                DamageReducer.healEvenly(state, DamageKeys.ORDER, 5f));
    }

    @Test
    void threeWayEvenHealingPlacesFloatResidueOnFinalCanonicalKey() {
        Map<String, Float> state = Map.of("blunt", 1f, "slash", 1f, "heat", 1f);
        Map<String, Float> healed = DamageReducer.healEvenly(state, DamageKeys.ORDER, 1f);
        assertEquals(0.6666666f, healed.get("blunt"), 0.000001f);
        assertEquals(0.6666666f, healed.get("slash"), 0.000001f);
        assertEquals(0.6666667f, healed.get("heat"), 0.000001f);
        assertEquals(1f, DamageReducer.total(state) - DamageReducer.total(healed), 0.000001f);
    }

    @Test
    void transactionMatchingUsesSourceIdentityAndCleansNestedEntries() {
        DamageTransactionStack<Object, String> stack = new DamageTransactionStack<>();
        Object outer = new Object();
        Object unrelated = new Object();
        stack.push(outer, "outer");
        stack.push(unrelated, "nested");

        assertEquals("nested", stack.removeMatching(unrelated));
        assertTrue(stack.contains(outer));
        assertEquals("outer", stack.removeMatching(outer));
        assertTrue(stack.isEmpty());
        assertNull(stack.removeMatching(new Object()));
    }

    @Test
    void damageMapCodecRoundTripsCanonicalState() {
        DamageMap original = new DamageMap(Map.of("blunt", 2.5f, "holy", 1f));
        var encoded = DamageMap.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, DamageMap.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(DamageMap.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"pierce\":1}")).result().isEmpty());
    }
}
