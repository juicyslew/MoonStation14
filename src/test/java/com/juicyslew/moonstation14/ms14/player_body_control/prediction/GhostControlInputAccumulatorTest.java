package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostControlInputAccumulatorTest {
    private static GhostControlState state(boolean jump) {
        return new GhostControlState((short) 0, (short) 0, (byte) 0, jump ? GhostControlPayloads.BUTTON_JUMP : 0, 0, 0);
    }
    @Test void pulseSurvivesPressReleaseAndOnlyFirstValidTickConsumesIt() {
        var a = new GhostControlInputAccumulator();
        assertFalse(a.consumeForTick(0).receivedInput());
        a.accept(state(true), 1); a.accept(state(false), 2);
        var first = a.consumeForTick(1);
        assertFalse(first.heldState().jumpHeld()); assertTrue(first.effectiveState().jumpHeld());
        assertEquals(2, first.sequence());
        assertNull(a.consumeForTick(1)); assertNull(a.consumeForTick(0));
        assertFalse(a.consumeForTick(2).effectiveState().jumpHeld());
    }
    @Test void heldJumpPersistsAndSeedDoesNotCreatePulse() {
        var a = new GhostControlInputAccumulator(); a.seed(state(true), 7);
        assertTrue(a.consumeForTick(0).effectiveState().jumpHeld());
        a.accept(state(true), 8);
        assertTrue(a.consumeForTick(1).effectiveState().jumpHeld());
        assertEquals(8, a.latestSettledSequence());
    }
    @Test void emptyConsumeGuardsTickButDefersLaterInputUntilNextTick() {
        var a = new GhostControlInputAccumulator(); a.consumeForTick(5); a.accept(state(true), 1);
        assertNull(a.consumeForTick(5)); assertTrue(a.consumeForTick(6).effectiveState().jumpHeld());
        a.reset(); assertFalse(a.consumeForTick(0).receivedInput());
    }
}
