package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import net.minecraft.world.level.GameType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleCharacterSessionControlTest {
    @Test void defaultOffMasterGateWinsAndDoesNotReachActivation() {
        assertEquals(LifecycleCharacterSessionControl.StartResult.GATE_OFF,
                LifecycleCharacterSessionControl.gateDecision(false, false));
        assertEquals(LifecycleCharacterSessionControl.StartResult.GATE_OFF,
                LifecycleCharacterSessionControl.gateDecision(false, true));
    }

    @Test void movementConflictBlocksEvenWhenMasterGateIsOn() {
        assertEquals(LifecycleCharacterSessionControl.StartResult.MOVEMENT_CONFLICT,
                LifecycleCharacterSessionControl.gateDecision(true, true));
    }

    @Test void connectedDeathRequiresExactHumanBindingSessionActiveEpochOwnerAndServerThread() {
        assertTrue(LifecycleCharacterSessionControl.connectedDeathDecision(true, true, true, true, true));
        assertFalse(LifecycleCharacterSessionControl.connectedDeathDecision(false, true, true, true, true));
        assertFalse(LifecycleCharacterSessionControl.connectedDeathDecision(true, false, true, true, true));
        assertFalse(LifecycleCharacterSessionControl.connectedDeathDecision(true, true, false, true, true));
        assertFalse(LifecycleCharacterSessionControl.connectedDeathDecision(true, true, true, false, true));
        assertFalse(LifecycleCharacterSessionControl.connectedDeathDecision(true, true, true, true, false));
    }

    @Test void enabledPreflightRemainsNonActivatingUntilSharedCameraOwnershipIsIntegrated() {
        assertEquals(LifecycleCharacterSessionControl.StartResult.CONTROLLER_INTEGRATION_REQUIRED,
                LifecycleCharacterSessionControl.gateDecision(true, false));
    }

    @Test void developmentModeIsOnlyAllowedForOperatorsLeavingOwnedSessionForCreativeOrSpectator() {
        assertTrue(LifecycleCharacterSessionControl.developmentModeDecision(false, GameType.CREATIVE, false));
        assertTrue(LifecycleCharacterSessionControl.developmentModeDecision(true, GameType.SPECTATOR, false));
        assertTrue(LifecycleCharacterSessionControl.developmentModeDecision(true, GameType.CREATIVE, true));
        assertFalse(LifecycleCharacterSessionControl.developmentModeDecision(true, GameType.CREATIVE, false));
        assertFalse(LifecycleCharacterSessionControl.developmentModeDecision(true, GameType.SURVIVAL, true));
    }

    @Test void failedDevelopmentParkRetainsTheCurrentSessionAndSuccessRunsRevocationExactlyOnce() {
        boolean[] sessionPresent = { true };
        assertFalse(LifecycleCharacterSessionControl.finishDevelopmentPark(false, () -> sessionPresent[0] = false));
        assertTrue(sessionPresent[0]);
        assertTrue(LifecycleCharacterSessionControl.finishDevelopmentPark(true, () -> sessionPresent[0] = false));
        assertFalse(sessionPresent[0]);
    }

    @Test void successfulParkLeavesBodyOfflineAndDeniesThePreviousEpoch() {
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        var account = java.util.UUID.randomUUID();
        var body = new MobHarness(new MobHarnessId(java.util.UUID.randomUUID()), MobHarnessKind.CHARACTER);
        assertTrue(lifecycle.registerHarness(body));
        var active = lifecycle.create(account, "main", new MindId(java.util.UUID.randomUUID()), body.id(), target -> true)
                .orElseThrow();
        assertTrue(lifecycle.authorizes(account, active.connectionGeneration(), body.id()));

        assertTrue(LifecycleCharacterSessionControl.finishDevelopmentPark(true,
                () -> assertEquals(PlayerLifecycleRegistry.Transition.CHANGED,
                        lifecycle.disconnect(account, active.connectionGeneration()))));
        var offline = lifecycle.profile(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.OFFLINE, offline.state());
        assertFalse(offline.active());
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), body.id()));
    }

    @Test void readyDecisionAcceptsOnlyExactUncommittedEpochBeforeTimeout() {
        assertTrue(LifecycleCharacterSessionControl.readyDecision(8, 8, false, 0, 100));
        assertTrue(LifecycleCharacterSessionControl.readyDecision(8, 8, false, 99, 100));
        assertFalse(LifecycleCharacterSessionControl.readyDecision(7, 8, false, 1, 100));
        assertFalse(LifecycleCharacterSessionControl.readyDecision(8, 8, true, 1, 100));
        assertFalse(LifecycleCharacterSessionControl.readyDecision(8, 8, false, 100, 100));
        assertFalse(LifecycleCharacterSessionControl.readyDecision(8, 8, false, -1, 100));
    }

    @Test void delayedBeginRequiresTheSameConnectedEligibleUnsentSessionAndTimeoutStartsAfterSend() {
        assertTrue(LifecycleCharacterSessionControl.delayedBeginDecision(true, true, true, false));
        assertFalse(LifecycleCharacterSessionControl.delayedBeginDecision(false, true, true, false));
        assertFalse(LifecycleCharacterSessionControl.delayedBeginDecision(true, false, true, false));
        assertFalse(LifecycleCharacterSessionControl.delayedBeginDecision(true, true, false, false));
        assertFalse(LifecycleCharacterSessionControl.delayedBeginDecision(true, true, true, true));
        assertFalse(LifecycleCharacterSessionControl.timeoutDecision(false, false, 100, 100));
        assertFalse(LifecycleCharacterSessionControl.timeoutDecision(true, false, 99, 100));
        assertTrue(LifecycleCharacterSessionControl.timeoutDecision(true, false, 100, 100));
        assertFalse(LifecycleCharacterSessionControl.timeoutDecision(true, true, 100, 100));
    }

    @Test void offlineBadgeUsesExactSixtySecondWallClockBoundaryAndRejectsClockRollback() {
        assertFalse(PlayerCharacterHarnessEntity.offlineBadgeDue(10_000L, 69_999L));
        assertTrue(PlayerCharacterHarnessEntity.offlineBadgeDue(10_000L, 70_000L));
        assertFalse(PlayerCharacterHarnessEntity.offlineBadgeDue(10_000L, 9_999L));
        assertFalse(PlayerCharacterHarnessEntity.offlineBadgeDue(-1L, 70_000L));
    }

    @Test void cameraOwnershipRequiresEveryCommittedSessionPrerequisite() {
        assertFalse(LifecycleCharacterSessionControl.shouldKeepCharacterCamera(null));
        assertTrue(cameraDecision(true, true, true, true, true, false));
        assertFalse(cameraDecision(false, true, true, true, true, false));
        assertFalse(cameraDecision(true, false, true, true, true, false));
        assertFalse(cameraDecision(true, true, false, true, true, false));
        assertFalse(cameraDecision(true, true, true, false, true, false));
        assertFalse(cameraDecision(true, true, true, true, false, false));
        assertFalse(cameraDecision(true, true, true, true, true, true));
    }

    @Test void lifecycleBodySnapshotRequiresEveryExactCommittedAuthorityCheck() {
        assertTrue(LifecycleCharacterSessionControl.activeCharacterBody(null).isEmpty());
        assertTrue(snapshotEligible(true, true, true, true, true, true, false, true, true));
        assertFalse(snapshotEligible(false, true, true, true, true, true, false, true, true));
        assertFalse(snapshotEligible(true, false, true, true, true, true, false, true, true));
        assertFalse(snapshotEligible(true, true, false, true, true, true, false, true, true));
        assertFalse(snapshotEligible(true, true, true, false, true, true, false, true, true));
        assertFalse(snapshotEligible(true, true, true, true, false, true, false, true, true));
        assertFalse(snapshotEligible(true, true, true, true, true, false, false, true, true));
        assertFalse(snapshotEligible(true, true, true, true, true, true, true, true, true));
        assertFalse(snapshotEligible(true, true, true, true, true, true, false, false, true));
        assertFalse(snapshotEligible(true, true, true, true, true, true, false, true, false));
    }

    @Test void lifecycleBodySnapshotRequiresExactAccountProfileAndMindBinding() {
        var account = java.util.UUID.randomUUID();
        var mind = new MindId(java.util.UUID.randomUUID());
        var binding = new PlayerCharacterBinding(account, "main", mind.value());
        assertTrue(LifecycleCharacterSessionControl.activeBodyBindingMatches(binding, account, "main", mind));
        assertFalse(LifecycleCharacterSessionControl.activeBodyBindingMatches(null, account, "main", mind));
        assertFalse(LifecycleCharacterSessionControl.activeBodyBindingMatches(binding,
                java.util.UUID.randomUUID(), "main", mind));
        assertFalse(LifecycleCharacterSessionControl.activeBodyBindingMatches(binding, account, "other", mind));
        assertFalse(LifecycleCharacterSessionControl.activeBodyBindingMatches(binding, account, "main",
                new MindId(java.util.UUID.randomUUID())));
    }

    @Test void intentGateEnforcesEpochRateAndStrictSequenceWithoutApplyingAnythingWhenUnused() {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        assertFalse(LifecycleCharacterSessionControl.intentRateAllows(0));
        assertTrue(LifecycleCharacterSessionControl.intentRateAllows(8));
        assertFalse(LifecycleCharacterSessionControl.intentRateAllows(9));
        GhostControlPayloads.Intent intent = intent(4, 1);
        assertTrue(gate.offer(intent.sequence(), 12, intent));
        assertFalse(gate.offer(intent.sequence(), 13, intent));
        assertFalse(gate.offer(2, 12, intent));
        assertEquals(intent, gate.take());
        assertTrue(gate.offer(3, 13, intent(4, 3)));
        assertEquals(3, gate.take().sequence());
        assertFalse(gate.offer(4, 13, intent(5, 4)));
        assertEquals(null, gate.take());
    }

    @Test void snapshotsMustRemainBoundToCommittedCharacterEpochAndEntity() {
        var snapshot = new GhostControlPayloads.Snapshot(4, 27, MobHarnessKind.CHARACTER,
                10, 3, 1, 2, 3, 0, 0, 0, 0, 0, true, 1, 1, false);
        assertTrue(LifecycleCharacterSessionControl.snapshotBoundToSession(snapshot, 4, 27));
        assertFalse(LifecycleCharacterSessionControl.snapshotBoundToSession(snapshot, 5, 27));
        assertFalse(LifecycleCharacterSessionControl.snapshotBoundToSession(snapshot, 4, 28));
        var ghostSnapshot = new GhostControlPayloads.Snapshot(4, 27, MobHarnessKind.GHOST,
                10, 3, 1, 2, 3, 0, 0, 0, 0, 0, true, 1, 1, false);
        assertFalse(LifecycleCharacterSessionControl.snapshotBoundToSession(ghostSnapshot, 4, 27));
    }

    private static GhostControlPayloads.Intent intent(long epoch, long sequence) {
        return new GhostControlPayloads.Intent(epoch, sequence, (short) 0, (short) 0,
                (byte) 0, 0, 0, 0);
    }

    private static boolean cameraDecision(boolean committed, boolean exactConnectedOnSameServer,
                                          boolean authorizedCurrentMindEpochAndBody,
                                          boolean cameraIsAuthorizedBody, boolean spectator,
                                          boolean hasPassenger) {
        return LifecycleCharacterSessionControl.committedCameraOwnershipMatches(committed,
                exactConnectedOnSameServer, authorizedCurrentMindEpochAndBody,
                cameraIsAuthorizedBody, spectator, hasPassenger);
    }

    @Test void terminalFailureClassificationSeparatesCameraModePassengerConnectionAuthorityAndBody() {
        assertEquals(LifecycleCharacterSessionControl.FailureReason.CONNECTION_MISMATCH,
                failure(false, false, true, false, false, false, false));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.MODE_MISMATCH,
                failure(true, false, true, false, false, false, false));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.PLAYER_PASSENGER,
                failure(true, true, true, true, true, true, true));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.CAMERA_MISMATCH,
                failure(true, true, false, false, true, true, true));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.AUTHORIZATION_REVOKED,
                failure(true, true, false, true, false, true, true));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.BODY_NOT_LIVE,
                failure(true, true, false, true, true, false, false));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.BODY_NOT_INTENT_ELIGIBLE,
                failure(true, true, false, true, true, true, false));
        assertEquals(LifecycleCharacterSessionControl.FailureReason.RUNTIME_INELIGIBLE,
                failure(true, true, false, true, true, true, true));
    }

    private static LifecycleCharacterSessionControl.FailureReason failure(boolean connection, boolean spectator,
            boolean passenger, boolean camera, boolean authorized, boolean live, boolean intentEligible) {
        return LifecycleCharacterSessionControl.classifyRuntimeFailure(connection, spectator, passenger,
                camera, authorized, live, intentEligible);
    }

    private static boolean snapshotEligible(boolean exactSession, boolean committed, boolean exactConnected,
                                            boolean serverThread, boolean playerAlive, boolean spectator,
                                            boolean passenger, boolean exactCamera, boolean liveBody) {
        return LifecycleCharacterSessionControl.activeBodySnapshotEligible(exactSession, committed,
                exactConnected, serverThread, playerAlive, spectator, passenger, exactCamera, liveBody);
    }
}
