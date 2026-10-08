package com.juicyslew.moonstation14.ms14.medical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthAnalyzerActivationPolicyTest {
    @Test void defaultItemToggleOpensWithoutChangingScanState() {
        assertEquals(HealthAnalyzerActivationPolicy.Decision.OPEN_ONLY,
                HealthAnalyzerActivationPolicy.decide(false, false));
        assertEquals(HealthAnalyzerActivationPolicy.Decision.OPEN_ONLY,
                HealthAnalyzerActivationPolicy.decide(false, true));
    }

    @Test void enabledItemToggleUsesCurrentActiveState() {
        assertEquals(HealthAnalyzerActivationPolicy.Decision.TOGGLE_ON_OPEN,
                HealthAnalyzerActivationPolicy.decide(true, false));
        assertEquals(HealthAnalyzerActivationPolicy.Decision.TOGGLE_OFF,
                HealthAnalyzerActivationPolicy.decide(true, true));
    }
}
