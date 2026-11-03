package com.juicyslew.moonstation14.ms14.actions.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionKeyPolicyTest {
    @Test void bindingsWithDifferentModifiersAreDistinctAndDuplicateChordKeepsFirstRegistration() {
        String[] bindings = {"key.keyboard.f:SHIFT", "key.keyboard.f:NONE", "key.keyboard.g:NONE", "key.keyboard.f:NONE"};
        assertEquals(1, BodyActionKeyPolicy.bindingWinner(bindings, 1));
        assertEquals(1, BodyActionKeyPolicy.bindingWinner(bindings, 3));
        assertEquals(2, BodyActionKeyPolicy.bindingWinner(bindings, 2));
    }
    @Test void f3DiscardsAllTwentyIncludingShiftBank() {
        int[] clicks = new int[BodyActionHotkeyDefaults.MAX_SLOTS];
        assertTrue(BodyActionKeyPolicy.consumeDebugChord(true, () -> java.util.Arrays.fill(clicks, 0)));
        assertEquals(20, clicks.length);
    }
}
