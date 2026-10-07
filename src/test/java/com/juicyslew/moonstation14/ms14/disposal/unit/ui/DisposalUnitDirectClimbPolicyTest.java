package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisposalUnitDirectClimbPolicyTest {
    @Test void directEntryNeedsFreshReachableActingBodyButNotMenuPowerOrEmptyHands() {
        assertTrue(DisposalUnitMenuService.directEntryAdmissionAllowed(true, true, true, true, false));
        assertFalse(DisposalUnitMenuService.directEntryAdmissionAllowed(false, true, true, true, false));
        assertFalse(DisposalUnitMenuService.directEntryAdmissionAllowed(true, false, true, true, false));
        assertFalse(DisposalUnitMenuService.directEntryAdmissionAllowed(true, true, false, true, false));
        assertFalse(DisposalUnitMenuService.directEntryAdmissionAllowed(true, true, true, false, false));
        assertFalse(DisposalUnitMenuService.directEntryAdmissionAllowed(true, true, true, true, true));
    }

    @Test void directClimbIsASeparateAuthenticatedIntent() {
        var request = new BodyBlockUseRequest(1, 1, 0, "left", "held-token",
                new net.minecraft.core.BlockPos(1, 2, 3), net.minecraft.core.Direction.UP, false,
                BodyBlockUseRequest.Intent.CLIMB_INSIDE);
        assertEquals(BodyBlockUseRequest.Intent.CLIMB_INSIDE, request.intent());
    }
}
