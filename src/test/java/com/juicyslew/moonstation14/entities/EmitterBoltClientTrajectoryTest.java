package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EmitterBoltClientTrajectoryTest {
    @Test void velocityAdvancesOnceAndTrackerCorrectionRebasesNextStep() {
        Vec3 velocity = new Vec3(0, 0, 2);
        Vec3 first = EmitterBoltClientTrajectory.advance(Vec3.ZERO, velocity, false);
        assertEquals(new Vec3(0, 0, 2), first);
        // Entity.lerpTo for this bare Projectile applies the packet position immediately;
        // prediction then advances from that authoritative correction, not the old path.
        Vec3 corrected = new Vec3(10, 4, -3);
        assertEquals(corrected.add(velocity), EmitterBoltClientTrajectory.advance(corrected, velocity, false));
    }

    @Test void stationaryAndRemovedBoltsDoNotAdvance() {
        Vec3 position = new Vec3(1, 2, 3);
        assertEquals(position, EmitterBoltClientTrajectory.advance(position, Vec3.ZERO, false));
        assertEquals(position, EmitterBoltClientTrajectory.advance(position, new Vec3(1, 0, 0), true));
    }

    @Test void renderPositionInterpolatesOldAndCurrentIncludingEndpoints() {
        Vec3 old = Vec3.ZERO, current = new Vec3(2, 4, 6);
        assertEquals(old, EmitterBoltClientTrajectory.partial(old, current, 0));
        assertEquals(new Vec3(1, 2, 3), EmitterBoltClientTrajectory.partial(old, current, .5f));
        assertEquals(current, EmitterBoltClientTrajectory.partial(old, current, 1));
    }
}
