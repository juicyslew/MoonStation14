package com.juicyslew.moonstation14.ms14.singularity.client;

import com.juicyslew.moonstation14.ms14.singularity.SingularityVisualProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingularityWorldLensCullingTest {
    private static SingularityVisualPreview.WorldSource source(float visibility) {
        var profile = new SingularityVisualProfile(1, 1, 1, 2, 20, 32);
        var uniform = new SingularityWorldLensUniforms.Source(0, 0, 10, 1, 2, 640, visibility, 1);
        return new SingularityVisualPreview.WorldSource(java.util.UUID.randomUUID(), new Vec3(0, 0, 10), 1, profile, visibility, uniform);
    }

    @Test void admitsOffscreenAffectedBoundsButRejectsOutsideCone() {
        var source = source(1);
        assertTrue(SingularityWorldLensCulling.sourceConeIntersects(new AABB(9, -1, 9, 11, 1, 11), source, Vec3.ZERO));
        assertFalse(SingularityWorldLensCulling.sourceConeIntersects(new AABB(99, -1, 9, 101, 1, 11), source, Vec3.ZERO));
    }

    @Test void zeroVisibilityDoesNotDisableOcclusion() {
        assertFalse(SingularityWorldLensCulling.sourceConeIntersects(new AABB(9, -1, 9, 11, 1, 11), source(0), Vec3.ZERO));
    }
}
