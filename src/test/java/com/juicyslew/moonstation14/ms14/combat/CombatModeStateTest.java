package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatModeStateTest {
    @Test void startsOffAndMutatesOnlyWhenExplicitlySet() {
        var state = new CombatModeAttachment();
        assertFalse(state.inCombatMode());
        state.set(true);
        assertTrue(state.inCombatMode());
        state.set(false);
        assertFalse(state.inCombatMode());
    }

    @Test void booleanCodecRoundTripsBothValues() {
        for (boolean value : new boolean[]{false, true}) {
            var encoded = CombatModeAttachment.CODEC.encodeStart(JsonOps.INSTANCE, new CombatModeAttachment(value)).getOrThrow();
            assertEquals(value, CombatModeAttachment.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().inCombatMode());
        }
    }
}
