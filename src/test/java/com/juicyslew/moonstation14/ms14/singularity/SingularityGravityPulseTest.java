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

    @Test void kernelAppliesSmoothPerTickResolvedProfileForce() {
        for (int tick = 0; tick < 10; tick++)
            assertTrue(SingularityGravityPulse.sourceDeltaVelocity(new Vec3(2, 0, 0), Vec3.ZERO, 1, PROFILE, .05).x > 0);
    }

    @Test void kernelAppliesExpandedRangeAndMinimumDistance() {
        assertEquals(new Vec3(1.875, 0, 0), SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(2, 0, 0), Vec3.ZERO, 1, PROFILE, .05));
        assertEquals(Vec3.ZERO, SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(2, 0, 0), Vec3.ZERO, 1, PROFILE, 0));
        assertEquals(Vec3.ZERO, SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(.001, 0, 0), Vec3.ZERO, 1, PROFILE, .05));
        assertTrue(SingularityGravityPulse.sourceDeltaVelocity(new Vec3(4.0001, 0, 0), Vec3.ZERO, 1, PROFILE, .05).length() > 0,
                "the authored expanded gravity range includes distances beyond the upstream base radius");
        assertEquals(Vec3.ZERO, SingularityGravityPulse.sourceDeltaVelocity(
                new Vec3(12, 0, 0), Vec3.ZERO, 1, PROFILE, .05));
    }
}
