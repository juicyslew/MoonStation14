package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.radiation.RadiationSourceProfile;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingularityGravityPulseTest {
    private static final SingularityGameplayProfile PROFILE = new SingularityGameplayProfile(
            180, 1, 2, 1, 10, 4, 1.875, 2.5,
            java.util.List.of(0d, 0d, 200d, 500d, 1000d, 2000d, 5000d),
            java.util.List.of(0d, 1d, 5d, 10d, 20d, 0d, 0d), new RadiationSourceProfile(2, .2));

    @Test void schedulesAreIndependentAndLatePulseAdvancesOnce() {
        var sourceA = new SingularityGravityPulse.Schedule(10);
        var sourceB = new SingularityGravityPulse.Schedule(17);
        assertEquals(0, sourceA.elapsedSeconds(9));
        assertEquals(.5, sourceA.elapsedSeconds(10));
        assertEquals(.5, sourceB.elapsedSeconds(17));
        assertEquals(1.25, sourceA.elapsedSeconds(25));
        sourceA = sourceA.advance(25);
        assertEquals(20, sourceA.nextTick());
        assertEquals(.8, sourceA.elapsedSeconds(26));
        assertEquals(17, sourceB.nextTick());
    }

    @Test void predictionUsesImmutablePhaseAndIsReplaySafe() {
        assertEquals(0, SingularityGravityPulse.predictedElapsedSeconds(10, 9));
        assertEquals(.5, SingularityGravityPulse.predictedElapsedSeconds(10, 10));
        assertEquals(.5, SingularityGravityPulse.predictedElapsedSeconds(10, 10));
        for (long tick = 11; tick < 20; tick++)
            assertEquals(0, SingularityGravityPulse.predictedElapsedSeconds(10, tick));
        assertEquals(.5, SingularityGravityPulse.predictedElapsedSeconds(10, 20));
        assertEquals(.5, SingularityGravityPulse.predictedElapsedSeconds(13, 13));
        assertEquals(0, SingularityGravityPulse.predictedElapsedSeconds(13, 10));
    }

    @Test void kernelAppliesUpstreamRangeAndMinimumDistance() {
        assertEquals(new Vec3(2.5, 0, 0), SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(2, 0, 0), Vec3.ZERO, 1, PROFILE, .5));
        assertEquals(Vec3.ZERO, SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(2, 0, 0), Vec3.ZERO, 1, PROFILE, 0));
        assertEquals(Vec3.ZERO, SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(.001, 0, 0), Vec3.ZERO, 1, PROFILE, .5));
        assertEquals(Vec3.ZERO, SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(4.0001, 0, 0), Vec3.ZERO, 1, PROFILE, .5));
        assertEquals(new Vec3(2.5, 0, 0), SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(2, 0, 0), Vec3.ZERO, 1, PROFILE, .5));
    }
}
