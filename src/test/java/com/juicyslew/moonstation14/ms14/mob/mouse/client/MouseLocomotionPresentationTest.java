package com.juicyslew.moonstation14.ms14.mob.mouse.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseLocomotionPresentationTest {
    @Test
    void movingAliveMouseUsesFastScurryRegardlessOfVanillaSprintFlag() {
        assertEquals("run", MouseLocomotionPresentation.clip(0.01, 0, false, false));
    }

    @Test
    void stationaryMouseStopsImmediatelyEvenIfVanillaLimbSwingWouldStillBeNonzero() {
        assertEquals("idle_breathe", MouseLocomotionPresentation.clip(0, 0, false, false));
    }

    @Test
    void deadAndCriticalPosesTakePriorityOverDisplacement() {
        assertEquals("down_hold", MouseLocomotionPresentation.clip(1, 1, true, false));
        assertEquals("down_hold", MouseLocomotionPresentation.clip(1, 1, false, true));
    }

    @Test
    void gaitCadenceTracksResolvedWalkAndRunDisplacement() {
        double walkSpeed = 4d;
        double walkStep = walkSpeed / 20d;
        assertEquals(1f, com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation.cadenceScale(walkStep, 0, walkSpeed), 0.0001f);
        assertEquals(2f, com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation.cadenceScale(walkStep * 2, 0, walkSpeed), 0.0001f);
        assertEquals(1f, com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation.cadenceScale(0, 0, walkSpeed), 0.0001f);
        assertEquals(1f, com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation.cadenceScale(walkStep, 0, 0), 0.0001f);
        assertTrue(com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation.cadenceScale(Double.MAX_VALUE, 0, walkSpeed) > 4f);
        assertEquals(1f, com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation.cadenceScale(Double.NaN, 0, walkSpeed), 0.0001f);
    }
}
