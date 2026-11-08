package com.juicyslew.moonstation14.ms14.actions.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionHotkeyDefaultsTest {
    @Test void directBindingsFollowTwentySlotOrder() {
        for (int i = 1; i <= 20; i++) assertEquals(i, BodyActionHotkeyDefaults.forSlot(i).slot());
        assertEquals("9", BodyActionHotkeyDefaults.forSlot(9).compactLabel());
        assertEquals("0", BodyActionHotkeyDefaults.forSlot(10).compactLabel());
        assertEquals("S+1", BodyActionHotkeyDefaults.forSlot(11).compactLabel());
        assertEquals("S+0", BodyActionHotkeyDefaults.forSlot(20).compactLabel());
        assertFalse(BodyActionHotkeyDefaults.forSlot(10).shift());
        assertTrue(BodyActionHotkeyDefaults.forSlot(11).shift());
        assertNull(BodyActionHotkeyDefaults.forSlot(0));
        assertNull(BodyActionHotkeyDefaults.forSlot(21));
    }
}
