package com.juicyslew.moonstation14.ms14.containment;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalContainedActionPolicyTest {
    @Test void bodyLocalActionsPassButWorldActionsRemainDenied() {
        assertTrue(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.DROP));
        assertTrue(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.USE_HELD));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.THROW));
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(BodyHandActionRequest.Action.PICKUP));
    }
}
