package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalUseGesturePolicyTest {
    @Test void inHandUseDistinguishesScannerSelfScanAndToggle() {
        assertEquals(MedicalUseGesturePolicy.InHand.SELF_SCAN,
                MedicalUseGesturePolicy.inHand(MedicalUseGesturePolicy.Kind.HEALTH_ANALYZER, true, true));
        assertEquals(MedicalUseGesturePolicy.InHand.TOGGLE_ANALYZER,
                MedicalUseGesturePolicy.inHand(MedicalUseGesturePolicy.Kind.HEALTH_ANALYZER, false, true));
        assertEquals(MedicalUseGesturePolicy.InHand.SELF_TREAT,
                MedicalUseGesturePolicy.inHand(MedicalUseGesturePolicy.Kind.HEALING, false, true));
        assertEquals(MedicalUseGesturePolicy.InHand.NONE,
                MedicalUseGesturePolicy.inHand(MedicalUseGesturePolicy.Kind.NONE, true, true));
    }

    @Test void primaryBodyUseRequiresMedicalTargetAndDoesNotPreemptPullOrWindow() {
        assertTrue(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.HEALING, true, false, false));
        assertTrue(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.HEALTH_ANALYZER, true, false, false));
        assertFalse(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.HEALING, true, true, false));
        assertFalse(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.HEALTH_ANALYZER, true, false, true));
        assertFalse(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.NONE, true, false, false));
        assertFalse(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.HEALING, false, false, false));
    }
}
