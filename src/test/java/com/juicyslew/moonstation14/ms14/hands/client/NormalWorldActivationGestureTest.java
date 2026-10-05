package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalWorldActivationGestureTest {
    @Test void ordinaryWorldUseActivatesBlocksInsteadOfUsingHeldItem() {
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.BLOCK_ACTIVATE,
                BodyInputGesturePolicy.normalWorldRoute(false, false, true));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.BLOCK_ACTIVATE,
                BodyInputGesturePolicy.normalWorldRoute(false, false, false));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.TARGET_USE,
                BodyInputGesturePolicy.normalWorldRoute(false, true, true));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.NONE,
                BodyInputGesturePolicy.normalWorldRoute(false, true, false));
        assertEquals(BodyInputGesturePolicy.NormalWorldRoute.STORAGE,
                BodyInputGesturePolicy.normalWorldRoute(true, true, true));
    }
}
