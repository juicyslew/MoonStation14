package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PopupMovementIntentTest {
    @Test void heldOnOpenReleaseAndFocusLoss() {
        var input = new PopupMovementIntent();
        assertEquals(new PopupMovementIntent.State(1, 1, true, true, false),
                input.sample(true, true, false, true, false, true, true, false));
        assertEquals(new PopupMovementIntent.State(0, 1, false, false, false),
                input.sample(true, true, false, false, false, false, false, false));
        assertEquals(PopupMovementIntent.State.IDLE,
                input.sample(false, true, false, false, false, false, false, false));
        input.sample(true, false, true, false, true, false, false, true);
        input.clear();
        assertEquals(PopupMovementIntent.State.IDLE, input.state());
    }

    @Test void modifierDistinctionAndOppositeDirections() {
        assertFalse(PopupMovementIntent.modifiersMatch(org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT,
                org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL));
        assertTrue(PopupMovementIntent.modifiersMatch(org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT,
                org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT));
        assertEquals(0, new PopupMovementIntent().sample(true, true, true, true, true, false, false, false).forward());
    }
}
