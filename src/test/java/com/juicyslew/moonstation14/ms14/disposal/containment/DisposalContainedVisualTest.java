package com.juicyslew.moonstation14.ms14.disposal.containment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposalContainedVisualTest {
    @Test
    void renderGateUsesOnlyExplicitPresentationMarker() {
        assertTrue(DisposalContainedVisual.shouldHide(true));
        assertFalse(DisposalContainedVisual.shouldHide(false));
    }
}
