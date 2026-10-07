package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisposalUnitContainedActivationPolicyTest {
    @Test
    void containedExceptionRequiresExactOwnerAndAimedLiveUnit() {
        assertTrue(DisposalUnitMenuService.containedBlockUseAllowed(true, true,
                BodyBlockUseRequest.Intent.ACTIVATE_WORLD));
        assertTrue(DisposalUnitMenuService.containedBlockUseAllowed(true, true,
                BodyBlockUseRequest.Intent.USE));
        assertFalse(DisposalUnitMenuService.containedBlockUseAllowed(false, true,
                BodyBlockUseRequest.Intent.ACTIVATE_WORLD));
        assertFalse(DisposalUnitMenuService.containedBlockUseAllowed(true, false,
                BodyBlockUseRequest.Intent.ACTIVATE_WORLD));
        assertFalse(DisposalUnitMenuService.containedBlockUseAllowed(true, true,
                null));
    }
}
