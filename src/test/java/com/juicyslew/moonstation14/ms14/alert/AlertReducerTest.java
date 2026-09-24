package com.juicyslew.moonstation14.ms14.alert;

import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class AlertReducerTest {
    private static final ResourceKey<AlertData> FIRST = ModAlerts.createKey("first");
    private static final ResourceKey<AlertData> SECOND = ModAlerts.createKey("second");

    @Test void allClearAndTimeCombinations() {
        assertTrue(AlertReducer.apply(Map.of(FIRST, persistent()), FIRST, true, 0, false, 10).isEmpty());
        assertEquals(20, AlertReducer.apply(Map.of(), FIRST, true, 10, true, 10).get(FIRST).deadline().getAsLong());
        assertTrue(AlertReducer.apply(Map.of(), FIRST, false, 0, false, 10).get(FIRST).deadline().isEmpty());
        assertEquals(20, AlertReducer.apply(Map.of(), FIRST, false, 10, false, 10).get(FIRST).deadline().getAsLong());
    }

    @Test void exactDeadlineReplacementAndCoexistence() {
        var state = AlertReducer.apply(Map.of(), FIRST, false, 10, true, 100);
        assertEquals(state, AlertReducer.expire(state, 109));
        assertFalse(AlertReducer.expire(state, 110).containsKey(FIRST));
        state = AlertReducer.apply(state, FIRST, false, 3, true, 200);
        assertEquals(203, state.get(FIRST).deadline().getAsLong());
        state = AlertReducer.apply(state, SECOND, false, 0, false, 200);
        assertEquals(2, state.size());
    }

    @Test void persistenceRetainsCooldownAndRejectsInvalidDeadline() {
        AlertInstance value = new AlertInstance(OptionalLong.of(25), true);
        var encoded = AlertInstance.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, AlertInstance.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(AlertInstance.CODEC.parse(JsonOps.INSTANCE,
                JsonOps.INSTANCE.createMap(Map.of(JsonOps.INSTANCE.createString("deadline"), JsonOps.INSTANCE.createLong(0),
                        JsonOps.INSTANCE.createString("show_cooldown"), JsonOps.INSTANCE.createBoolean(true)))).error().isPresent());
    }

    @Test void categoryReplacementIsDeterministicAndLeavesUncategorizedAlertsAlone() {
        var category = net.minecraft.resources.ResourceLocation.parse("moonstation14:toxins");
        var otherCategory = net.minecraft.resources.ResourceLocation.parse("moonstation14:medical");
        var state = Map.of(FIRST, persistent(), SECOND, persistent());
        var result = AlertReducer.replaceCategory(state, FIRST, java.util.Optional.of(category), key ->
                key.equals(FIRST) || key.equals(SECOND) ? java.util.Optional.of(category) : java.util.Optional.empty());
        assertEquals(java.util.Set.of(FIRST), result.keySet());
        assertEquals(state, AlertReducer.replaceCategory(state, FIRST, java.util.Optional.of(otherCategory),
                key -> java.util.Optional.of(category)));
    }

    @Test void secondsConversionIsFiniteNonnegativeAndScaleIndependent() {
        assertEquals(0, AlertSystem.secondsToTicks(0));
        assertEquals(1, AlertSystem.secondsToTicks(0.0001f));
        assertEquals(30, AlertSystem.secondsToTicks(1.5f));
        assertThrows(IllegalArgumentException.class,
                () -> AlertSystem.secondsToTicks(Float.NaN));
        assertThrows(ArithmeticException.class,
                () -> AlertSystem.secondsToTicks(Float.MAX_VALUE));
    }

    @Test void capacityRejectsOnlyWhenFinalStateWouldExceedLimit() {
        var full = new java.util.HashMap<ResourceKey<AlertData>, AlertInstance>();
        for (int i = 0; i < AlertComponent.MAX_ENTRIES - 1; i++) {
            full.put(ModAlerts.createKey("capacity_" + i), persistent());
        }
        full.put(FIRST, persistent());
        assertThrows(IllegalArgumentException.class,
                () -> AlertReducer.apply(full, ModAlerts.createKey("overflow"), false, 0, false, 1));
        assertEquals(AlertComponent.MAX_ENTRIES,
                AlertReducer.apply(full, FIRST, false, 0, true, 1).size(), "same-key replacement stays within capacity");
        var category = net.minecraft.resources.ResourceLocation.parse("moonstation14:replaceable");
        var replaced = AlertReducer.replaceCategory(full, SECOND, java.util.Optional.of(category), key ->
                key.equals(FIRST) ? java.util.Optional.of(category) : java.util.Optional.empty());
        assertEquals(AlertComponent.MAX_ENTRIES - 1, replaced.size());
        assertEquals(AlertComponent.MAX_ENTRIES,
                AlertReducer.apply(replaced, SECOND, false, 0, false, 1).size(), "category replacement frees capacity first");
    }

    @Test void alertActivityIsNeededOnlyForTimedEntries() {
        assertFalse(com.juicyslew.moonstation14.ms14.MS14Bridges.ALERT.activityBinding().orElseThrow()
                .needsTicking().test(new AlertAttachment(new AlertComponent(Map.of(FIRST, persistent())))));
        var timed = new AlertInstance(OptionalLong.of(100), true);
        assertTrue(com.juicyslew.moonstation14.ms14.MS14Bridges.ALERT.activityBinding().orElseThrow()
                .needsTicking().test(new AlertAttachment(new AlertComponent(Map.of(FIRST, timed)))));
    }

    private static AlertInstance persistent() { return new AlertInstance(OptionalLong.empty(), false); }
}
