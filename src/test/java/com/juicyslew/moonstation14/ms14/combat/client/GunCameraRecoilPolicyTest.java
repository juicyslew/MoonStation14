package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunCameraRecoilPolicyTest {
    @Test void normalizedKickDampsAndClamps() {
        var state = GunCameraRecoilPolicy.kick(GunCameraRecoilPolicy.State.ZERO, 0, 0, 0, 1, 3, 1);
        assertEquals(-1, state.kick().y(), 1e-9);
        var accumulated = GunCameraRecoilPolicy.kick(state, 0, 0, 1, 0, 1, 1);
        assertTrue(accumulated.kick().magnitude() <= 1);
        assertEquals(0, accumulated.elapsedSeconds());
    }
    @Test void zeroIntensityDoesNothing() {
        var initial = new GunCameraRecoilPolicy.State(2, new GunCameraRecoilPolicy.Offset(.2, .3));
        assertSame(initial, GunCameraRecoilPolicy.kick(initial, 0, 0, 1, 0, 1, 0));
    }
    @Test void recoveryDoesNotCrossAxesAndSnapsThreshold() {
        var axis = GunCameraRecoilPolicy.advance(new GunCameraRecoilPolicy.State(0,
                new GunCameraRecoilPolicy.Offset(.001, .1)), 1);
        assertEquals(0, axis.kick().x());
        assertTrue(axis.kick().y() >= 0);
        var snapped = GunCameraRecoilPolicy.advance(new GunCameraRecoilPolicy.State(0,
                new GunCameraRecoilPolicy.Offset(.003, .003)), .01);
        assertEquals(GunCameraRecoilPolicy.Offset.ZERO, snapped.kick());
    }
    @Test void recoveryUsesElapsedTimeAndDeterministicFrameDelta() {
        var start = new GunCameraRecoilPolicy.State(0, new GunCameraRecoilPolicy.Offset(1, 0));
        var next = GunCameraRecoilPolicy.advance(start, .1);
        assertEquals(.1, next.elapsedSeconds());
        assertTrue(next.kick().x() < 1 && next.kick().x() > 0);
    }
    @Test void threeDimensionalCardinalShotsKickBackwardsOnEveryAxis() {
        double[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (double[] direction : directions) {
            var state = GunCameraRecoilPolicy.kick(GunCameraRecoilPolicy.State.ZERO, 0,0,0,
                    direction[0],direction[1],direction[2],1,1);
            assertEquals(.5, state.kick().magnitude(), 1e-9);
            assertEquals(-direction[0] * .5, state.kick().x(), 1e-9);
            assertEquals(-direction[1] * .5, state.kick().y(), 1e-9);
            assertEquals(-direction[2] * .5, state.kick().z(), 1e-9);
        }
    }
}
