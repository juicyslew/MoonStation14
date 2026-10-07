package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyConstructionOutcomePolicyTest {
    @Test void onlyCommittedMaterialRevisionAcknowledgesPlacement() {
        assertEquals(BodyBlockUseResult.Outcome.PLACED, BodyConstructionOutcomePolicy.outcome(true, 4, 5L));
        assertEquals(BodyBlockUseResult.Outcome.NO_CHANGE, BodyConstructionOutcomePolicy.outcome(true, 4, 4L));
        assertEquals(BodyBlockUseResult.Outcome.NO_CHANGE, BodyConstructionOutcomePolicy.outcome(true, 4, null));
        assertEquals(BodyBlockUseResult.Outcome.NO_CHANGE, BodyConstructionOutcomePolicy.outcome(true, 4, 3L));
        assertEquals(BodyBlockUseResult.Outcome.DENIED, BodyConstructionOutcomePolicy.outcome(false, 4, 5L));
    }
}
