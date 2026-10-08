package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MedicalPredictionPolicyTest {
    @Test
    void characterAlivePermitsHistoryTrackerAndReplay() {
        var decision = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, false, false);
        assertFalse(decision.suspended());
        assertFalse(decision.authorityBarrierRequired());
        assertTrue(decision.recordHistory());
        assertTrue(decision.ownsTracker());
        assertTrue(decision.replaySnapshot());
    }

    @Test
    void criticalAndDeadSuspendEveryPredictionGateAndRequireOneEntryBarrier() {
        for (var state : new MobStateComponent.State[]{MobStateComponent.State.CRITICAL, MobStateComponent.State.DEAD}) {
            var first = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER, state, false, false);
            assertTrue(first.suspended());
            assertTrue(first.authorityBarrierRequired());
            assertFalse(first.recordHistory());
            assertFalse(first.ownsTracker());
            assertFalse(first.replaySnapshot());

            var ongoing = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER, state, true, true);
            assertFalse(ongoing.authorityBarrierRequired());
            assertFalse(ongoing.recordHistory());
            assertFalse(ongoing.ownsTracker());
            assertFalse(ongoing.replaySnapshot());
        }
    }

    @Test
    void recoveryOnlyResumesAfterSuspensionHistoryBarrier() {
        var unsafeRecovery = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, true, false);
        assertTrue(unsafeRecovery.authorityBarrierRequired());
        assertFalse(unsafeRecovery.recordHistory());
        assertFalse(unsafeRecovery.ownsTracker());
        assertFalse(unsafeRecovery.replaySnapshot());

        var recovered = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, true, true);
        assertTrue(recovered.recordHistory());
        assertTrue(recovered.ownsTracker());
        assertTrue(recovered.replaySnapshot());
    }

    @Test
    void authorityBarrierMustBeAppliedBeforeSuspensionStateCanClear() {
        boolean suspended = false;
        var entering = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.CRITICAL, suspended, false);
        assertTrue(entering.authorityBarrierRequired());
        assertFalse(entering.recordHistory());
        suspended = entering.suspended();

        var recovering = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, suspended, false);
        assertTrue(recovering.authorityBarrierRequired());
        assertFalse(recovering.recordHistory());
        assertFalse(recovering.ownsTracker());
        assertFalse(recovering.replaySnapshot());

        var barrierApplied = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, suspended, true);
        assertTrue(barrierApplied.authorityBarrierRequired());
        assertTrue(barrierApplied.recordHistory());
        assertTrue(barrierApplied.ownsTracker());
        assertTrue(barrierApplied.replaySnapshot());
    }

    @Test
    void ghostHarnessIsUnaffectedByMedicalState() {
        for (var state : new MobStateComponent.State[]{MobStateComponent.State.ALIVE,
                MobStateComponent.State.CRITICAL, MobStateComponent.State.DEAD}) {
            var decision = MedicalPredictionPolicy.decide(MobHarnessKind.GHOST, state, false, false);
            assertFalse(decision.suspended());
            assertTrue(decision.recordHistory());
            assertTrue(decision.ownsTracker());
            assertTrue(decision.replaySnapshot());
        }
    }

    @Test
    void incomingPullSuspendsPredictionForAliveCharacterIncludingChainedEndpoints() {
        var incoming = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, false, false, true);
        assertTrue(incoming.suspended());
        assertTrue(incoming.authorityBarrierRequired());
        assertFalse(incoming.recordHistory());
        assertFalse(incoming.ownsTracker());
        assertFalse(incoming.replaySnapshot());

        // An outgoing-only puller remains locally predicted.
        var outgoingOnly = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, false, false, false);
        assertTrue(outgoingOnly.recordHistory());
        assertTrue(outgoingOnly.ownsTracker());

        var chain = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, true, true, true);
        assertFalse(chain.recordHistory());
    }

    @Test
    void incomingPullCleanupRequiresBarrierBeforePredictionResumes() {
        var releasing = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, true, false, false);
        assertTrue(releasing.authorityBarrierRequired());
        assertFalse(releasing.recordHistory());
        var resumed = MedicalPredictionPolicy.decide(MobHarnessKind.CHARACTER,
                MobStateComponent.State.ALIVE, true, true, false);
        assertTrue(resumed.recordHistory());
        assertTrue(resumed.ownsTracker());
        assertTrue(resumed.replaySnapshot());
    }
}
