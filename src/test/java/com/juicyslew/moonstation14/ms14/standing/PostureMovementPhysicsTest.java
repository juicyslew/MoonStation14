package com.juicyslew.moonstation14.ms14.standing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureMovementPhysicsTest {
    @Test void downFrictionPreservesSteadyStateRatio() {
        assertEquals(.546d, PostureMovementPhysics.groundedDamping(.546d, 1d), 1e-12);
        double damping = PostureMovementPhysics.groundedDamping(.546d, .4d);
        assertEquals(.8184d, damping, 1e-12);
        assertEquals(.4d, PostureMovementPhysics.inputGain(.4d), 1e-12);
        assertEquals(1d / (1d - .546d), PostureMovementPhysics.inputGain(.4d) / (1d - damping), 1e-12);
    }
}
