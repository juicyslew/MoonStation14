package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleDevelopmentModeTest {
    @Test void returnRequiresGateExactTrackedOperatorCreativeAndMatchingOfflineProfile() {
        assertTrue(eligible(true, true, true, GameType.CREATIVE, true));
        assertFalse(eligible(false, true, true, GameType.CREATIVE, true));
        assertFalse(eligible(true, false, true, GameType.CREATIVE, true));
        assertFalse(eligible(true, true, false, GameType.CREATIVE, true));
        assertFalse(eligible(true, true, true, GameType.SURVIVAL, true));
        assertFalse(eligible(true, true, true, GameType.ADVENTURE, true));
        assertFalse(eligible(true, true, true, GameType.CREATIVE, false));
    }

    @Test void failedPreActivationReturnKeepsDetachedStateAndSuccessfulReturnCanClearIt() {
        boolean detached = true;
        assertTrue(LifecycleDevelopmentMode.failedReturnKeepsDetached(true, detached));
        assertFalse(LifecycleDevelopmentMode.failedReturnKeepsDetached(false, detached));
        detached = false; // success clears the exact tracked player
        assertFalse(detached);
    }

    @Test void staleOrDuplicateReturnIsDeniedByExactTrackedCarrierRequirement() {
        assertFalse(eligible(true, false, true, GameType.CREATIVE, true));
        assertFalse(eligible(true, true, true, GameType.CREATIVE, false));
    }

    @Test void deathExplanationRequiresExactCreativeOperatorAndUniqueCurrentPrimaryDeadProfile() {
        var dead = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM;
        assertTrue(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, true, true, GameType.CREATIVE, dead, true));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                false, true, true, GameType.CREATIVE, dead, true));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, false, true, GameType.CREATIVE, dead, true));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, true, false, GameType.CREATIVE, dead, true));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, true, true, GameType.SURVIVAL, dead, true));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, true, true, GameType.CREATIVE, dead, false));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, true, true, GameType.CREATIVE,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE,
                true));
        assertFalse(LifecycleDevelopmentMode.deadClaimReturnMessageEligible(
                true, true, true, GameType.CREATIVE, null, true));
        assertTrue(LifecycleDevelopmentMode.DEAD_RETURN_MESSAGE.contains("Your character has died"));
        assertFalse(LifecycleDevelopmentMode.DEAD_RETURN_MESSAGE.contains("dead claim"));
    }

    private static boolean eligible(boolean gates, boolean tracked, boolean operator, GameType mode, boolean profile) {
        return LifecycleDevelopmentMode.returnEligibilityDecision(gates, tracked, operator, mode, profile);
    }
}
