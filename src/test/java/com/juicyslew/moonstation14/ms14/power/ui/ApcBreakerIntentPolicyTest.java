package com.juicyslew.moonstation14.ms14.power.ui;

import org.junit.jupiter.api.Test;

import static com.juicyslew.moonstation14.ms14.power.ui.ApcBreakerIntentPolicy.Decision.DENY;
import static com.juicyslew.moonstation14.ms14.power.ui.ApcBreakerIntentPolicy.Decision.NO_CHANGE;
import static com.juicyslew.moonstation14.ms14.power.ui.ApcBreakerIntentPolicy.Decision.TOGGLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApcBreakerIntentPolicyTest {
    @Test
    void staleRevisionIsDeniedEvenWhenDesiredStateAlreadyMatches() {
        assertEquals(DENY, ApcBreakerIntentPolicy.decide(3, 4, true, true));
        assertEquals(DENY, ApcBreakerIntentPolicy.decide(5, 4, false, true));
    }

    @Test
    void matchingDesiredStateNeedsNoMutation() {
        assertEquals(NO_CHANGE, ApcBreakerIntentPolicy.decide(2, 2, true, true));
        assertEquals(NO_CHANGE, ApcBreakerIntentPolicy.decide(2, 2, false, false));
    }

    @Test
    void matchingRevisionAllowsOneToggleInEitherDirection() {
        assertEquals(TOGGLE, ApcBreakerIntentPolicy.decide(2, 2, true, false));
        assertEquals(TOGGLE, ApcBreakerIntentPolicy.decide(2, 2, false, true));
    }

    @Test
    void negativeAndLongBoundaryRevisions() {
        assertEquals(DENY, ApcBreakerIntentPolicy.decide(-1, -1, true, true));
        assertEquals(DENY, ApcBreakerIntentPolicy.decide(0, -1, false, true));
        assertEquals(NO_CHANGE, ApcBreakerIntentPolicy.decide(0, 0, true, true));
        assertEquals(DENY, ApcBreakerIntentPolicy.decide(0, Long.MAX_VALUE, true, false));
        assertEquals(DENY, ApcBreakerIntentPolicy.decide(Long.MAX_VALUE, 0, true, false));
        // Intent is valid at MAX_VALUE; the device may still refuse the actual mutation.
        assertEquals(TOGGLE, ApcBreakerIntentPolicy.decide(Long.MAX_VALUE, Long.MAX_VALUE, true, false));
        assertEquals(NO_CHANGE, ApcBreakerIntentPolicy.decide(Long.MAX_VALUE, Long.MAX_VALUE, false, false));
    }
}
