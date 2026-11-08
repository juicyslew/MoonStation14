package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeslaLightningBeamHazardTest {
    @Test void segmentHasNoArtificialWidthAndUsesFixedEndpoints() {
        Vec3 from = new Vec3(0, 0, 0), to = new Vec3(4, 0, 0);
        assertTrue(TeslaLightningBeamHazard.intersects(from, to, new AABB(1, -.2, -.2, 1.2, .2, .2)));
        assertFalse(TeslaLightningBeamHazard.intersects(from, to, new AABB(1, .21, -.2, 1.2, .4, .2)));
        assertFalse(TeslaLightningBeamHazard.intersects(from, to, new AABB(4.1, -.2, -.2, 4.3, .2, .2)));
        Vec3 movedEntity = new Vec3(8, 0, 0);
        assertFalse(TeslaLightningBeamHazard.intersects(from, to, new AABB(movedEntity, movedEntity).inflate(.2)));
    }

    @Test void sourceOffsetClampsShortBeamWithoutReversing() {
        assertEquals(new Vec3(1, 0, 0), TeslaLightningSystem.beamStart(new Vec3(0, 0, 0), new Vec3(2, 0, 0)));
        assertEquals(new Vec3(.5, 0, 0), TeslaLightningSystem.beamStart(new Vec3(0, 0, 0), new Vec3(.5, 0, 0)));
    }

    @Test void authoredHalfWidthInflatesWholeFiniteSegmentBoxOnly() {
        Vec3 from = new Vec3(0, 0, 0), to = new Vec3(4, 0, 0);
        AABB gap = new AABB(1, .1, -.1, 1.2, .2, .1);
        assertFalse(TeslaLightningBeamHazard.intersects(from, to, gap));
        assertTrue(TeslaLightningBeamHazard.intersects(from, to, gap, .2));
        assertFalse(TeslaLightningBeamHazard.intersects(from, to,
                new AABB(1, .31, -.1, 1.2, .4, .1), .2));
        // Width inflates the entity box before clipping the finite original segment;
        // this deliberately admits a box up to half-width beyond either endpoint (cube cap).
        assertTrue(TeslaLightningBeamHazard.intersects(from, to,
                new AABB(4.1, -.1, -.1, 4.3, .1, .1), .2));
        assertFalse(TeslaLightningBeamHazard.intersects(from, from,
                new AABB(1, -.1, -.1, 1.1, .1, .1), .2));
    }

    @Test void onlyCoincidentOriginalEndpointsHaveNoPhysicalBeam() {
        assertFalse(TeslaLightningSystem.hasPhysicalSegment(new Vec3(1, 2, 3), new Vec3(1, 2, 3)));
        assertTrue(TeslaLightningSystem.hasPhysicalSegment(new Vec3(1, 2, 3), new Vec3(1.01, 2, 3)));
        assertFalse(TeslaLightningBeamHazard.intersects(new Vec3(1, 2, 3), new Vec3(1, 2, 3),
                new AABB(.9, 1.9, 2.9, 1.1, 2.1, 3.1), .2));
    }
}
