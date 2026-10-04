package com.juicyslew.moonstation14.ms14.disposal.containment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalContainedActionPolicyTest {
    @Test void onlyExactLiveOwnerRelationBlocksExternalMutations() {
        assertTrue(DisposalContainedActionPolicy.deniesMembership(true));
        assertFalse(DisposalContainedActionPolicy.deniesMembership(false));
    }
}
