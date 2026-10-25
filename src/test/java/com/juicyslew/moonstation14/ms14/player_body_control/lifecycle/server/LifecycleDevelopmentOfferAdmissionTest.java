package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class LifecycleDevelopmentOfferAdmissionTest {
    @Test
    void normalRoleAdmissionCannotUseDevelopmentPermissionAsGhostRole() {
        assertFalse(LifecycleGhostSessionControl.offerOriginAllowed(false, true, false));
        assertTrue(LifecycleGhostSessionControl.offerOriginAllowed(false, false, true));
        assertFalse(LifecycleGhostSessionControl.offerOriginAllowed(true, false, false));
        assertTrue(LifecycleGhostSessionControl.offerOriginAllowed(true, true, false));
    }

    @Test
    void developmentCommitRechecksOperatorAndExactLivingUnclaimedSameLevelTarget() {
        assertTrue(LifecycleGhostSessionControl.developmentCommitAllowed(true, true, true, true, true, true));
        assertFalse(LifecycleGhostSessionControl.developmentCommitAllowed(true, false, true, true, true, true));
        assertFalse(LifecycleGhostSessionControl.developmentCommitAllowed(true, true, true, false, true, true));
        assertFalse(LifecycleGhostSessionControl.developmentCommitAllowed(true, true, true, true, false, true));
        assertFalse(LifecycleGhostSessionControl.developmentCommitAllowed(true, true, true, true, true, false));
        assertFalse(LifecycleGhostSessionControl.developmentCommitAllowed(true, true, false, true, true, true));
        assertTrue(LifecycleGhostSessionControl.developmentCommitAllowed(false, false, false, false, false, false));
    }

    @Test
    void sharedAdoptionPreflightOnlyBypassesGhostRoleForAuthorizedOperatorOrigin() {
        assertFalse(LifecycleCharacterSessionControl.roleAdoptionPrototypeEligible(false, false));
        assertTrue(LifecycleCharacterSessionControl.roleAdoptionPrototypeEligible(true, false));
        assertTrue(LifecycleCharacterSessionControl.roleAdoptionPrototypeEligible(false, true));
    }
}
