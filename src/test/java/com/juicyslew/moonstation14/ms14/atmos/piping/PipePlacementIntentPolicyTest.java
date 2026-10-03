package com.juicyslew.moonstation14.ms14.atmos.piping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipePlacementIntentPolicyTest {
    @Test void shiftAndOrdinaryHitsRouteToAirWhenAvailable() {
        assertEquals(PipePlacementIntentPolicy.Intent.EXPOSED,
                PipePlacementIntentPolicy.choose(true, true, false, false, true)); // dirt below
        assertEquals(PipePlacementIntentPolicy.Intent.EXPOSED,
                PipePlacementIntentPolicy.choose(true, false, false, false, true)); // dirt below; timing-independent
        assertEquals(PipePlacementIntentPolicy.Intent.EXPOSED,
                PipePlacementIntentPolicy.choose(false, false, false, true, true)); // pipe end
    }

    @Test void oneShiftHintCanSelectCrossingOnlyForMissingFunctionalLane() {
        assertEquals(PipePlacementIntentPolicy.Intent.CROSSING,
                PipePlacementIntentPolicy.choose(true, true, false, true, false));
        assertEquals(PipePlacementIntentPolicy.Intent.CROSSING,
                PipePlacementIntentPolicy.choose(false, false, false, true, false));
    }

    @Test void staleOrMalformedWallHintNeverInstallsWallAndCosmeticPipeCannotUpgrade() {
        assertEquals(PipePlacementIntentPolicy.Intent.DENY,
                PipePlacementIntentPolicy.choose(true, false, true, false, false));
        assertEquals(PipePlacementIntentPolicy.Intent.DENY,
                PipePlacementIntentPolicy.choose(true, false, true, false, true));
        assertEquals(PipePlacementIntentPolicy.Intent.DENY,
                PipePlacementIntentPolicy.choose(true, true, false, false, false));
        assertEquals(PipePlacementIntentPolicy.Intent.SEALED_WALL,
                PipePlacementIntentPolicy.choose(true, true, true, false, true));
    }
}
