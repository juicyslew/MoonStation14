package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import net.minecraft.world.level.GameType;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleDevelopmentModeTest {
    @Test void preparedAndCommittedGhostsBothRejectLateCreativeAndParkTransitionOnlyAllowsInternalCreative() {
        // Ownership, not the committed camera snapshot, drives the event decision.
        boolean preparedOwner = true, committedOwner = true;
        assertTrue(LifecycleDevelopmentMode.rejectRawGhostModeChange(preparedOwner, GameType.CREATIVE));
        assertTrue(LifecycleDevelopmentMode.rejectRawGhostModeChange(committedOwner, GameType.CREATIVE));
        assertTrue(LifecycleDevelopmentMode.rejectRawGhostModeChange(true, GameType.CREATIVE));
        assertTrue(LifecycleDevelopmentMode.rejectRawGhostModeChange(true, GameType.SURVIVAL));
        assertTrue(LifecycleDevelopmentMode.rejectRawGhostModeChange(true, GameType.ADVENTURE));
        assertFalse(LifecycleDevelopmentMode.rejectRawGhostModeChange(true, GameType.SPECTATOR));
        assertFalse(LifecycleDevelopmentMode.rejectRawGhostModeChange(false, GameType.CREATIVE));
        assertTrue(LifecycleDevelopmentMode.rejectParkingGhostModeChange(GameType.CREATIVE, false));
        assertFalse(LifecycleDevelopmentMode.rejectParkingGhostModeChange(GameType.CREATIVE, true));
        assertTrue(LifecycleDevelopmentMode.rejectParkingGhostModeChange(GameType.SURVIVAL, true));
        assertFalse(LifecycleDevelopmentMode.rejectParkingGhostModeChange(GameType.SPECTATOR, false));
        assertTrue(LifecycleDevelopmentMode.rejectParkedGhostModeChange(GameType.SPECTATOR, false));
        assertFalse(LifecycleDevelopmentMode.rejectParkedGhostModeChange(GameType.SPECTATOR, true));
        assertTrue(LifecycleDevelopmentMode.rejectParkedGhostModeChange(GameType.CREATIVE, false));
        assertFalse(LifecycleDevelopmentMode.rejectParkedGhostModeChange(GameType.CREATIVE, false, true));
        assertTrue(LifecycleDevelopmentMode.rejectParkedGhostModeChange(GameType.SURVIVAL, true));
    }

    @Test void modeTransitionCannotBeBorrowedByAnotherPlayerOrAnUnownedSpectator() {
        Object owner = new Object(), other = new Object();
        assertTrue(LifecycleDevelopmentMode.exactTransitionOwner(owner, owner));
        assertFalse(LifecycleDevelopmentMode.exactTransitionOwner(owner, other));
        assertFalse(LifecycleDevelopmentMode.exactTransitionOwner(null, owner));
        assertTrue(LifecycleDevelopmentMode.rejectParkingGhostModeChange(GameType.CREATIVE,
                LifecycleDevelopmentMode.exactTransitionOwner(owner, other)));
        assertTrue(LifecycleDevelopmentMode.rejectParkedGhostModeChange(GameType.SPECTATOR,
                LifecycleDevelopmentMode.exactTransitionOwner(owner, other)));
        assertTrue(LifecycleDevelopmentMode.rejectDetachedModeChange(GameType.SPECTATOR, false));
        assertFalse(LifecycleDevelopmentMode.rejectDetachedModeChange(GameType.SPECTATOR, true));
        assertTrue(LifecycleDevelopmentMode.rejectDetachedModeChange(GameType.CREATIVE, false));
        assertFalse(LifecycleDevelopmentMode.rejectDetachedModeChange(GameType.CREATIVE, false, true));
        assertTrue(LifecycleDevelopmentMode.rejectDetachedModeChange(GameType.SPECTATOR,
                LifecycleDevelopmentMode.exactTransitionOwner(owner, other)));
        assertTrue(LifecycleDevelopmentMode.rejectDetachedModeChange(GameType.SURVIVAL, true));
    }

    @Test void parkedLivingBodyOnlyAcceptsDeathClaimForExactSavedIdentity() {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), body = UUID.randomUUID();
        var offline = new SavedLifecycleProfile(1, account, "person", mind, body,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0),
                Map.of(), UUID.randomUUID(), 7, SavedLifecycleProfile.State.OFFLINE, 2, 1L);
        var dead = new SavedLifecycleProfile(1, account, "person", mind, body,
                offline.dimension(), offline.location(), offline.appearance(), offline.connectionEpoch(),
                8, SavedLifecycleProfile.State.DEAD_CLAIM, 3, 1L);
        assertTrue(LifecycleDevelopmentMode.verifiedParkedBodyDeathClaim(dead, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyDeathClaim(offline, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyDeathClaim(dead, null, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyDeathClaim(dead, offline, UUID.randomUUID()));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyDeathClaim(
                new SavedLifecycleProfile(1, account, "person", mind, UUID.randomUUID(), dead.dimension(),
                        dead.location(), dead.appearance(), dead.connectionEpoch(), 8,
                        SavedLifecycleProfile.State.DEAD_CLAIM, 3, 1L), offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyDeathClaim(
                new SavedLifecycleProfile(1, account, "person", mind, body, dead.dimension(),
                        dead.location(), dead.appearance(), dead.connectionEpoch(), 7,
                        SavedLifecycleProfile.State.DEAD_CLAIM, 3, 1L), offline, account));
    }

    @Test void rolledBackClaimWithAdvancedGenerationCannotReuseParkedMarkerOnRetry() {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), corpse = UUID.randomUUID();
        var parked = new SavedLifecycleProfile(1, account, "person", mind, corpse,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0),
                Map.of(), UUID.randomUUID(), 7, SavedLifecycleProfile.State.DEAD_CLAIM, 2, 1L);
        var afterRollback = new SavedLifecycleProfile(1, account, "person", mind, corpse,
                "minecraft:overworld", parked.location(), parked.appearance(), parked.connectionEpoch(),
                8, SavedLifecycleProfile.State.DEAD_CLAIM, 2, 1L);
        assertTrue(LifecycleDevelopmentMode.verifiedParkedGhostClaim(parked, parked, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedGhostClaim(afterRollback, parked, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedGhostClaim(null, parked, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedGhostClaim(parked, parked, UUID.randomUUID()));
    }

    @Test void postRollbackRecoveryRequiresExactAdvancedPrimaryAndMemoryClaim() {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), body = UUID.randomUUID();
        var parked = new SavedLifecycleProfile(1, account, "person", mind, body,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0),
                Map.of(), UUID.randomUUID(), 7, SavedLifecycleProfile.State.DEAD_CLAIM, 2, 1L);
        var rolledBack = new SavedLifecycleProfile(1, account, "person", mind, body,
                parked.dimension(), parked.location(), parked.appearance(), parked.connectionEpoch(),
                8, SavedLifecycleProfile.State.DEAD_CLAIM, 3, 1L);
        var memory = new PlayerLifecycleRegistry.Snapshot(1, account, "person", new MindId(mind),
                new MobHarnessId(body), PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM, 8, false, true);
        assertTrue(LifecycleDevelopmentMode.verifiedRecoveryClaim(rolledBack, parked, memory, true));
        assertFalse(LifecycleDevelopmentMode.verifiedRecoveryClaim(rolledBack, parked, memory, false));
        assertFalse(LifecycleDevelopmentMode.verifiedRecoveryClaim(rolledBack, parked,
                new PlayerLifecycleRegistry.Snapshot(1, account, "person", new MindId(mind),
                        new MobHarnessId(body), PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM, 7, false, true), true));
        assertFalse(LifecycleDevelopmentMode.verifiedRecoveryClaim(new SavedLifecycleProfile(1, account,
                "person", mind, UUID.randomUUID(), parked.dimension(), parked.location(), parked.appearance(),
                parked.connectionEpoch(), 8, SavedLifecycleProfile.State.DEAD_CLAIM, 3, 1L), parked, memory, true));
        assertFalse(LifecycleDevelopmentMode.verifiedRecoveryClaim(null, parked, memory, true));
    }

    @Test void postParkOfflineIdentityMustDeriveFromPinnedActiveRow() {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), body = UUID.randomUUID();
        var active = new SavedLifecycleProfile(1, account, "person", mind, body,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0),
                Map.of(), UUID.randomUUID(), 7, SavedLifecycleProfile.State.ACTIVE, 2, null);
        var offline = new SavedLifecycleProfile(1, account, "person", mind, body,
                active.dimension(), active.location(), active.appearance(), active.connectionEpoch(),
                8, SavedLifecycleProfile.State.OFFLINE, 3, 1L);
        assertTrue(LifecycleDevelopmentMode.verifiedParkedBodyOffline(offline, active, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyOffline(null, active, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyOffline(offline, active, UUID.randomUUID()));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedBodyOffline(new SavedLifecycleProfile(1, account,
                "person", mind, UUID.randomUUID(), offline.dimension(), offline.location(), offline.appearance(),
                offline.connectionEpoch(), 8, SavedLifecycleProfile.State.OFFLINE, 3, 1L), active, account));
        assertTrue(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(offline, active, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(offline, active, null, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(offline, active, offline, UUID.randomUUID()));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(new SavedLifecycleProfile(1, account,
                "other", mind, body, offline.dimension(), offline.location(), offline.appearance(),
                offline.connectionEpoch(), 8, SavedLifecycleProfile.State.OFFLINE, 3, 1L), active, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(new SavedLifecycleProfile(1, account,
                "person", UUID.randomUUID(), body, offline.dimension(), offline.location(), offline.appearance(),
                offline.connectionEpoch(), 8, SavedLifecycleProfile.State.OFFLINE, 3, 1L), active, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(new SavedLifecycleProfile(1, account,
                "person", mind, UUID.randomUUID(), offline.dimension(), offline.location(), offline.appearance(),
                offline.connectionEpoch(), 8, SavedLifecycleProfile.State.OFFLINE, 3, 1L), active, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(new SavedLifecycleProfile(1, account,
                "person", mind, body, offline.dimension(), offline.location(), offline.appearance(),
                offline.connectionEpoch(), 9, SavedLifecycleProfile.State.OFFLINE, 3, 1L), active, offline, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(new SavedLifecycleProfile(1, account,
                "person", mind, body, offline.dimension(), offline.location(), offline.appearance(),
                offline.connectionEpoch(), 8, SavedLifecycleProfile.State.OFFLINE, 4, 1L), active, offline, account));
    }

    @Test void parkedGhostDeathClaimDoesNotDependOnParkedLivingBody() {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), corpse = UUID.randomUUID();
        var claim = new SavedLifecycleProfile(1, account, "person", mind, corpse,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0),
                Map.of(), UUID.randomUUID(), 7, SavedLifecycleProfile.State.DEAD_CLAIM, 2, 1L);
        assertTrue(LifecycleDevelopmentMode.verifiedParkedGhostClaim(claim, claim, account));
        assertFalse(LifecycleDevelopmentMode.verifiedDetachedOfflineRow(claim, null, null, account));
        assertFalse(LifecycleDevelopmentMode.verifiedParkedGhostClaim(claim, claim, UUID.randomUUID()));
    }

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
        assertTrue(LifecycleDevelopmentMode.DEAD_RETURN_MESSAGE.contains("/ms14dev return"));
    }

    private static boolean eligible(boolean gates, boolean tracked, boolean operator, GameType mode, boolean profile) {
        return LifecycleDevelopmentMode.returnEligibilityDecision(gates, tracked, operator, mode, profile);
    }
}
