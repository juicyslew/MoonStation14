package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaReducer;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaState;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaminaReducerTest {
    @Test void criticalEntryRecoveryPlanAndDefaultsRoundTrip() {
        var policy = StaminaPrototypeComponent.DEFAULT;
        assertEquals(new StaminaPrototypeComponent(100, 3, 3, 6, 5, policy.slowdownThresholds()),
                StaminaPrototypeComponent.CODEC.parse(JsonOps.INSTANCE, StaminaPrototypeComponent.CODEC.encodeStart(JsonOps.INSTANCE, policy).getOrThrow()).getOrThrow());
        StaminaState state = StaminaState.EMPTY;
        boolean entry = false;
        for (int i = 0; i < 4; i++) {
            var result = StaminaReducer.applyDelta(policy, state, 25, i);
            state = result.state();
            entry |= result.enteredCritical();
        }
        assertTrue(entry);
        assertTrue(state.critical());
        assertEquals(100, state.damage());
        assertEquals(183, state.nextUpdateTick());
        assertEquals(state, StaminaReducer.applyDelta(policy, state, -50, 10).state());
        assertTrue(StaminaReducer.recoveryPlan(policy, state, 182).isEmpty());
        var plan = StaminaReducer.recoveryPlan(policy, state, 183).orElseThrow();
        assertFalse(plan.state().critical());
        assertTrue(plan.state().afterCritical());
        assertEquals(-15, plan.requestedDelta());
        assertEquals(203, plan.state().nextUpdateTick());
    }

    @Test void slowdownAndNormalRecoveryUseDeadlineAndDeltaPath() {
        var p = StaminaPrototypeComponent.DEFAULT;
        var state = StaminaReducer.applyDelta(p, StaminaState.EMPTY, 60, 0).state();
        assertEquals(60, state.nextUpdateTick());
        assertEquals(.7, StaminaReducer.slowdownMultiplier(p, state));
        assertTrue(StaminaReducer.recoveryPlan(p, state, 59).isEmpty());
        var recovery = StaminaReducer.recoveryPlan(p, state, 60).orElseThrow();
        assertEquals(-3, recovery.requestedDelta());
        assertEquals(80, recovery.state().nextUpdateTick());
        var after = StaminaReducer.applyDelta(p, recovery.state(), recovery.requestedDelta(), 60).state();
        assertEquals(57, after.damage());
        assertEquals(1, StaminaReducer.slowdownMultiplier(p, after));
        assertEquals(1, StaminaReducer.slowdownMultiplier(p, new StaminaState(0, false, false, 0)));
    }

    @Test void additionsExtendDeadlineAndHealingFloorsAtZero() {
        var p = StaminaPrototypeComponent.DEFAULT;
        var first = StaminaReducer.applyDelta(p, StaminaState.EMPTY, 20, 2).state();
        assertEquals(62, first.nextUpdateTick());
        var second = StaminaReducer.applyDelta(p, first, 1, 30).state();
        assertEquals(90, second.nextUpdateTick());
        var healed = StaminaReducer.applyDelta(p, second, -100, 31).state();
        assertEquals(0, healed.damage());
    }

    @Test void typedComponentResolvesThroughExistingCharacterInheritanceAndRegistry() {
        var parent = JsonParser.parseString("""
                {"abstract":true,"components":[{"type":"Stamina","decay":4}]}
                """).getAsJsonObject();
        var child = JsonParser.parseString("""
                {"parent":"stamina_parent","components":[{"type":"Stamina","cooldown_seconds":2}]}
                """).getAsJsonObject();
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        var parentId = ResourceLocation.parse("moonstation14:stamina_parent");
        var childId = ResourceLocation.parse("moonstation14:stamina_child");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, childId, child));
        var resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId).component(StaminaPrototypeComponent.class).orElseThrow();
        assertEquals(4, resolved.decay());
        assertEquals(2, resolved.cooldownSeconds());
        assertEquals(100, resolved.baseCritThreshold());
    }
}
