package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GroundedHarnessPhysicsTest {
    @Test
    void exposesCachedWalkAndSprintGravityAirProfiles() {
        var walk = GroundedHarnessPhysics.gravityAirControl(false);
        var sprint = GroundedHarnessPhysics.gravityAirControl(true);

        assertSame(walk, GroundedHarnessPhysics.gravityAirControl(false));
        assertSame(sprint, GroundedHarnessPhysics.gravityAirControl(true));
        assertEquals(.05d, walk.orElseThrow().referenceTickSeconds());
        assertEquals(.91d, walk.orElseThrow().horizontalRetentionPerTick());
        assertEquals(7.84d, walk.orElseThrow().inputAccelerationPerSecondSquared());
        assertEquals(10.192d, sprint.orElseThrow().inputAccelerationPerSecondSquared());
    }
}
