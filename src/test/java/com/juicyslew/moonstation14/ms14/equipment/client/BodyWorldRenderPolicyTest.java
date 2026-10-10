package com.juicyslew.moonstation14.ms14.equipment.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyWorldRenderPolicyTest {
    @Test
    void onlyOwnedCameraBoundBodyIsHiddenInFirstPerson() {
        assertFalse(BodyWorldRenderPolicy.allowOrdinaryPass(true, true, true));
        assertTrue(BodyWorldRenderPolicy.allowOrdinaryPass(false, true, true)); // observers still see others
        assertTrue(BodyWorldRenderPolicy.allowOrdinaryPass(true, false, true));
        assertTrue(BodyWorldRenderPolicy.allowOrdinaryPass(true, true, false)); // F5 third person
    }
}
