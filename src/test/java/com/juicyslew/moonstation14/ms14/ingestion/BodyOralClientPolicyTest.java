package com.juicyslew.moonstation14.ms14.ingestion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyOralClientPolicyTest {
    @Test void eligibleRayItemPrecedesPuddleButUnrelatedOrUnsupportedItemsDoNot() {
        assertEquals(BodyOralClientPolicy.AlternateTarget.ITEM,
                BodyOralClientPolicy.alternateTarget(true, true));
        assertEquals(BodyOralClientPolicy.AlternateTarget.PUDDLE,
                BodyOralClientPolicy.alternateTarget(false, true));
        assertEquals(BodyOralClientPolicy.AlternateTarget.NONE,
                BodyOralClientPolicy.alternateTarget(false, false));
    }
    @Test void handlessAlternateRequiresCommittedCharacterMouthAndNoHands() {
        assertTrue(BodyOralClientPolicy.mayUseHandlessAlternate(true, true, false, false));
        assertFalse(BodyOralClientPolicy.mayUseHandlessAlternate(false, true, false, false));
        assertFalse(BodyOralClientPolicy.mayUseHandlessAlternate(true, false, false, false));
        assertFalse(BodyOralClientPolicy.mayUseHandlessAlternate(true, true, true, false));
        assertFalse(BodyOralClientPolicy.mayUseHandlessAlternate(true, true, false, true));
    }

    @Test void sequenceGateRejectsDuplicatesAndStaleEpochRequests() {
        var gate = new BodyOralSequenceGate();
        assertTrue(gate.admit(2, 1));
        assertFalse(gate.admit(2, 1));
        assertFalse(gate.admit(2, 0));
        assertTrue(gate.admit(3, 1));
        assertFalse(gate.admit(2, 2));
    }
}
