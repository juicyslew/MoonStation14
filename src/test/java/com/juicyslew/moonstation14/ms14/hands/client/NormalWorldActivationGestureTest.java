package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalWorldActivationGestureTest {
    @Test void transparentPointerCanvasRoutesOnlyOutsideUiAndUnfocusedSearchToExistingBodyActionPath() {
        assertTrue(BodyHandClientPolicy.allowPointerWorldAction(true,false,false)); // locked browser remains gameplay-active
        assertFalse(BodyHandClientPolicy.allowPointerWorldAction(true,true,false)); // browser or storage window owns hit
        assertFalse(BodyHandClientPolicy.allowPointerWorldAction(true,false,true)); // text entry owns keyboard
        assertFalse(BodyHandClientPolicy.allowPointerWorldAction(false,false,false)); // unrelated screens unchanged
    }
    @Test void ordinaryWorldUseActivatesBlocksInsteadOfUsingHeldItem() {
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.BLOCK_ACTIVATE,
                BodyInputGesturePolicy.normalWorldRoute(false, false, true));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.BLOCK_ACTIVATE,
                BodyInputGesturePolicy.normalWorldRoute(false, false, false));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.TARGET_USE,
                BodyInputGesturePolicy.normalWorldRoute(false, true, true));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.ACTIVATE_TARGET,
                BodyInputGesturePolicy.normalWorldRoute(false, true, false));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.STORAGE,
                BodyInputGesturePolicy.normalWorldRoute(true, true, true));
    }
}
