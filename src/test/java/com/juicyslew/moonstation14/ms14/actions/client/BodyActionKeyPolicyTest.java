package com.juicyslew.moonstation14.ms14.actions.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionKeyPolicyTest {
    @Test void eventModifiersMustExactlyMatchTheConfiguredChord() {
        var none = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
        var shift = net.neoforged.neoforge.client.settings.KeyModifier.SHIFT;
        var control = net.neoforged.neoforge.client.settings.KeyModifier.CONTROL;
        assertTrue(BodyActionKeyPolicy.exactModifiers(0, none));
        assertFalse(BodyActionKeyPolicy.exactModifiers(org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT, none));
        assertTrue(BodyActionKeyPolicy.exactModifiers(org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT, shift));
        assertFalse(BodyActionKeyPolicy.exactModifiers(0, shift));
        assertFalse(BodyActionKeyPolicy.exactModifiers(org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL, none));
        assertTrue(BodyActionKeyPolicy.exactModifiers(org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL, control));
        assertFalse(BodyActionKeyPolicy.exactModifiers(org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL | org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT, control));
    }
    @Test void invalidEventChordIsDrainedButConfiguredShiftChordRemainsValid() {
        var none = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
        var shift = net.neoforged.neoforge.client.settings.KeyModifier.SHIFT;
        int shiftMask = org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
        assertTrue(BodyActionKeyPolicy.shouldDrainInvalidChord(true, shiftMask, none));
        assertFalse(BodyActionKeyPolicy.shouldDrainInvalidChord(true, shiftMask, shift));
        assertTrue(BodyActionKeyPolicy.shouldDrainInvalidChord(true, 0, shift));
        assertFalse(BodyActionKeyPolicy.shouldDrainInvalidChord(false, shiftMask, none));
    }
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
