package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectedLooseOutlineRendererTest {
    private static final AABB BOX = new AABB(-.5, 0, -.5, .5, 1, .5);

    @Test
    void alignsStationaryAndMovingBoundsWithRenderedPositionAndCamera() {
        Vec3 current = new Vec3(4, 2, 8);
        Vec3 camera = new Vec3(1, 1, 3);

        assertEquals(BOX.move(-1, -1, -3), SelectedLooseOutlineRenderer.interpolatedBounds(BOX, current, current, camera));
        assertEquals(BOX.move(0, 0, -3), SelectedLooseOutlineRenderer.interpolatedBounds(
                BOX, current, new Vec3(5, 3, 8), camera));
    }

    @Test
    void midpointPositionMovesBoundsByHalfTheTickDisplacement() {
        Vec3 current = new Vec3(4, 2, 8);
        Vec3 halfway = new Vec3(4.5, 2.5, 8);
        AABB result = SelectedLooseOutlineRenderer.interpolatedBounds(BOX, current, halfway, Vec3.ZERO);

        assertEquals(0, result.minX, 1e-9);
        assertEquals(.5, result.minY, 1e-9);
        assertEquals(-.5, result.minZ, 1e-9);
    }
}
