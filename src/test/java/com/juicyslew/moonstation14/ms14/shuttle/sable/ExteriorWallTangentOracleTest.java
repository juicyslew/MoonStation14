package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExteriorWallTangentOracleTest {
    @Test void allowsMotorAccelerationThatDiffersFromFreeActorButMatchesWallOwnRequest() {
        var evidence=ExteriorWallSustainedJumpGameTests.tangentEvidence(.07284928,.0728505167,
                .07284928,.0728505167);
        assertTrue(evidence.withinBounds());
        double freeActorStep=.03789576;
        assertTrue(Math.abs(.07284928-freeActorStep)>.02,
                "a free motor with different post-contact velocity is diagnostic, not the steering oracle");
    }

    @Test void catchesPerStepTangentMotionNotExplainedByOwnRequest() {
        var evidence=ExteriorWallSustainedJumpGameTests.tangentEvidence(.1201,.1,.1201,.1);
        assertFalse(evidence.withinBounds());
    }

    @Test void catchesCumulativeSmallPerStepGlideAndStillAllowsStraightArcZeroTangent() {
        var accumulated=ExteriorWallSustainedJumpGameTests.tangentEvidence(.101,.1,.201, .1);
        assertFalse(accumulated.withinBounds(),"many individually small excesses cannot accumulate into glide");
        var straight=ExteriorWallSustainedJumpGameTests.tangentEvidence(.001,0,.001,0);
        assertTrue(straight.withinBounds(),"straight arc remains compared against zero requested tangent");
    }
}
