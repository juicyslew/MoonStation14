package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyInteractionRayTest {
    @Test void bodyHeightOriginUsesBoundsAndNormalizedRatio() {
        var origin = BodyInteractionRay.bodyHeightOrigin(new AABB(-.3, 0, -.3, .3, 1.8, .3), .6);
        assertNotNull(origin);
        assertEquals(0, origin.x, 1e-12);
        assertEquals(1.08, origin.y, 1e-12);
        assertEquals(0, origin.z, 1e-12);
        assertNull(BodyInteractionRay.bodyHeightOrigin(new AABB(0, 0, 0, 1, 1, 1), Double.NaN));
        assertNull(BodyInteractionRay.bodyHeightOrigin(new AABB(0, 0, 0, 1, 1, 1), 1.01));
    }
}
