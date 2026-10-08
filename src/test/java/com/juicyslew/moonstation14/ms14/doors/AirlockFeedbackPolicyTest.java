package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirlockFeedbackPolicyTest {
    @Test void unknownOrUnpoweredAttemptsAreSilentButPoweredDenialsRemainAudible() {
        assertFalse(AirlockFeedbackPolicy.deny(false, false, false));
        assertFalse(AirlockFeedbackPolicy.deny(false, true, false));
        assertFalse(AirlockFeedbackPolicy.deny(true, false, false));
        assertFalse(AirlockFeedbackPolicy.deny(true, true, true));
        assertTrue(AirlockFeedbackPolicy.deny(true, true, false));
    }
}
