package com.juicyslew.moonstation14.ms14.containment;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalContainedActionPolicyTest {
    @Test void bodyLocalActionsPassButWorldActionsRemainDenied() {
        assertTrue(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.DROP));
        assertTrue(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.USE_HELD));
        assertTrue(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.USE_IN_HAND));
        assertTrue(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.PRIME_GRENADE));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.THROW_ITEM));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.THROW_GRENADE));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.USE_TARGET));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.ACTIVATE_TARGET));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.TOGGLE_PULL));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.PICKUP));
    }
}
