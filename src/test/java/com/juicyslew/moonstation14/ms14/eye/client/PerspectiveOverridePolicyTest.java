package com.juicyslew.moonstation14.ms14.eye.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerspectiveOverridePolicyTest {
    @Test void capturesOnceMarksManualChoiceAndRestoresOrRebases() {
        var policy = new PerspectiveOverridePolicy<String>();
        policy.enter("first");
        policy.enter("forced");
        assertEquals("first", policy.baseline());
        policy.manualChange();
        assertNull(policy.restore());
        assertFalse(policy.active());

        policy.enter("first");
        policy.manualChange();
        policy.rebase("front");
        assertFalse(policy.manuallyChanged());
        assertEquals("front", policy.restore());
    }

    @Test void inactiveRestoreIsEmptyAndClearDropsBaseline() {
        var policy = new PerspectiveOverridePolicy<String>();
        assertNull(policy.restore());
        policy.enter("first");
        policy.clear();
        assertNull(policy.baseline());
        assertNull(policy.restore());
    }
}
