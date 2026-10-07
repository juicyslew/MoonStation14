package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostControlClientPolicyTest {
    @Test
    void characterDefaultsToFastWithoutModifierOrMinecraftSprint() {
        assertEquals(GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, false, false, false));
    }

    @Test
    void characterShiftWalksEvenWhenMinecraftReportsSprinting() {
        assertEquals(GhostControlPayloads.BUTTON_SNEAK,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, false, true, true));
    }

    @Test
    void characterCtrlSprintDoesNotOverrideShiftWalkOrJump() {
        assertEquals(GhostControlPayloads.BUTTON_JUMP | GhostControlPayloads.BUTTON_SNEAK,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, true, true, true));
        assertEquals(GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, false, false, true));
    }

    @Test
    void characterWithoutSprintFlagStillMovesFastWhenUnshifted() {
        assertEquals(GhostControlPayloads.BUTTON_JUMP | GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, true, false, false));
    }

    @Test
    void ghostRetainsMinecraftSprintMappingRegardlessOfShift() {
        assertEquals(0, GhostControlClient.intentButtons(MobHarnessKind.GHOST, false, true, false));
        assertEquals(GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.GHOST, false, true, true));
        assertEquals(GhostControlPayloads.BUTTON_JUMP,
                GhostControlClient.intentButtons(MobHarnessKind.GHOST, true, false, false));
    }

    @Test
    void customRegisteredCharacterDoesNotDependOnLegacyHostMapping() {
        assertTrue(GhostControlClient.characterBodyRecognized(true, false));
    }

    @Test
    void legacyConfiguredMobStillRequiresItsMappedPolicy() {
        assertTrue(GhostControlClient.characterBodyRecognized(false, true));
        assertFalse(GhostControlClient.characterBodyRecognized(false, false));
    }

    @Test
    void deferredBeginSurvivesTransientSessionCleanupAndWaitsForOwner() {
        GhostControlPayloads.Begin begin = begin(4);
        GhostControlPayloads.Begin queued = GhostControlClient.ClientBeginPolicy.defer(null, begin, 3);

        assertSame(begin, GhostControlClient.ClientBeginPolicy.afterSessionClear(queued));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAccept(queued, 3, false));
        assertTrue(GhostControlClient.ClientBeginPolicy.canAccept(queued, 3, true));
    }

    @Test
    void deferredBeginRequiresNewEpochAndLogoutDiscardsIt() {
        GhostControlPayloads.Begin begin = begin(4);
        assertNull(GhostControlClient.ClientBeginPolicy.defer(null, begin, 4));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAccept(begin, 4, true));
        assertNull(GhostControlClient.ClientBeginPolicy.afterLogout());
    }

    @Test
    void beginIsAcceptedOnlyFromMatchingPayloadContext() {
        GhostControlPayloads.Begin begin = begin(4);
        assertTrue(GhostControlClient.ClientBeginPolicy.canAcceptPayload(begin, 3, true));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAcceptPayload(begin, 3, false));
    }

    @Test
    void readyNeedsOnlyMatchingBeginEntityAndSessionNotObserverProjection() {
        // Observer snapshot, corpse entity, and prototype projection are deliberately absent here.
        assertTrue(GhostControlClient.ClientReadyPolicy.canSend(4, 4, true, true, true));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(3, 4, true, true, true));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(4, 4, false, true, true));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(4, 4, true, true, false));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(4, 4, true, false, true));
    }

    @Test
    void beginMovementRatesDriveSameMotorWithNoDefaultFallback() {
        var begin = new GhostControlPayloads.Begin(4, 7, MobHarnessKind.GHOST, 3, 7);
        GhostMovementMotor motor = new GhostMovementMotor(begin.ghostWalkSpeed(), begin.ghostSprintSpeed(), begin.ghostSprintSpeed());
        var walk = motor.tick(MovementVector.ZERO, 0, 1000, 0, false, 0,
                (position, displacement, grounded) -> new MovementCollisionResolver.CollisionResult(displacement, false));
        var sprint = motor.tick(MovementVector.ZERO, 0, 1000, 0, true, 0,
                (position, displacement, grounded) -> new MovementCollisionResolver.CollisionResult(displacement, false));
        assertEquals(3, walk.position().z() / .05, 1e-9);
        assertEquals(7, sprint.position().z() / .05, 1e-9);
        assertEquals(7, motor.verticalSpeedPerSecond());
    }

    @Test
    void characterBeginCarriesNoGhostMovementPolicy() {
        var begin = new GhostControlPayloads.Begin(4, 7, MobHarnessKind.CHARACTER);
        assertEquals(0, begin.ghostWalkSpeed());
        assertEquals(0, begin.ghostSprintSpeed());
    }

    private static GhostControlPayloads.Begin begin(long epoch) {
        return new GhostControlPayloads.Begin(epoch, 7, MobHarnessKind.GHOST);
    }
}
