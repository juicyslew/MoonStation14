package com.juicyslew.moonstation14.ms14.medical_observer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObserverIntentYawTest {
    @Test
    void lifecycleTransportOwnershipExcludesTheLegacyMovementRoute() {
        assertFalse(ObserverSystem.useLegacyMovement(true));
        assertTrue(ObserverSystem.useLegacyMovement(false));
        assertEquals(com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent.DEFAULT_WALK_SPEED,
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor.WALK_SPEED_PER_SECOND);
        assertEquals(com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent.DEFAULT_SPRINT_SPEED,
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor.SPRINT_SPEED_PER_SECOND);
    }

    @Test
    void cameraIntentFactoryNormalizesAccumulatedYawAndPreservesInputBounds() {
        for (int turns = -500; turns <= 500; turns++) {
            float yaw = turns * 360f + 47.25f;
            ObserverPayloads.Intent intent = ObserverPayloads.Intent.fromCamera(7, -1000, 1000, 1, true, yaw);
            assertTrue(intent.yaw() >= -180 && intent.yaw() <= 180);
            assertEquals(47.25f, intent.yaw(), 0.01f);
            assertEquals(-1000, intent.strafe());
            assertEquals(1000, intent.forward());
            assertEquals(1, intent.vertical());
            assertTrue(intent.sprint());
        }
        assertEquals(-180, ObserverPayloads.Intent.fromCamera(1, 0, 0, 0, false, 180).yaw());
        assertEquals(-180, ObserverPayloads.Intent.fromCamera(1, 0, 0, 0, false, -180).yaw());
    }

    @Test
    void finiteYawIsAcceptedAndCanonicalizedButNonFiniteValuesAreRejected() {
        for (float yaw : new float[]{450, -450, 1080, Float.MAX_VALUE}) {
            ObserverPayloads.Intent payload = new ObserverPayloads.Intent(1, 0, 0, 0, false, yaw);
            ObserverIntent intent = new ObserverIntent(1, 0, 0, false, yaw);
            assertTrue(payload.yaw() >= -180 && payload.yaw() <= 180);
            assertEquals(payload.yaw(), intent.yaw());
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ObserverPayloads.Intent.fromCamera(1, 0, 0, 0, false, invalid));
            assertThrows(IllegalArgumentException.class, () -> new ObserverPayloads.Intent(1, 0, 0, 0, false, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new ObserverPayloads.Intent(1, 1001, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new ObserverIntent(0, 0, 0, false, Float.NaN));
    }
}
