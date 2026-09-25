package com.juicyslew.moonstation14.ms14.movement.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementServerTeleportBarrierTest {
    @Test
    void historicalTeleportIdDoesNotBlockBarrierIssueWithoutPendingPosition() {
        assertEquals(MovementServerController.BarrierState.READY_TO_ISSUE,
                MovementServerController.barrierState(null, 42, false, false));
    }

    @Test
    void competingPendingVanillaTeleportMustClearBeforeBarrierIssue() {
        assertEquals(MovementServerController.BarrierState.WAITING_FOR_VANILLA,
                MovementServerController.barrierState(null, 42, true, false));
    }

    @Test
    void matchingBarrierWaitsAndReplacementFailsClosed() {
        assertEquals(MovementServerController.BarrierState.WAITING_FOR_BARRIER,
                MovementServerController.barrierState(43, 43, true, false));
        assertEquals(MovementServerController.BarrierState.REPLACED,
                MovementServerController.barrierState(43, 44, true, false));
    }

    @Test
    void acknowledgedBarrierIsNotRecheckedOrReissuedAfterResumeWasSent() {
        assertEquals(MovementServerController.BarrierState.RESUME_SENT,
                MovementServerController.barrierState(43, 44, false, true));
    }

    @Test
    void missingAcknowledgementDoesNotTreatClearedPositionAsConfirmation() {
        assertEquals(MovementServerController.BarrierState.ACKNOWLEDGEMENT_MISSING,
                MovementServerController.barrierState(43, 0, false, false));
    }
}
