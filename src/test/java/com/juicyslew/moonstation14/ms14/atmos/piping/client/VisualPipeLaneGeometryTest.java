package com.juicyslew.moonstation14.ms14.atmos.piping.client;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VisualPipeLaneGeometryTest {
    @Test
    void armsJoinCenterWithoutPositiveVolumeOverlapAtEveryLayer() {
        for (int layerMinimum : new int[]{2, 12}) {
            var center = VisualPipeLaneGeometry.center(layerMinimum);
            for (Direction direction : Direction.values()) {
                var arm = VisualPipeLaneGeometry.arm(layerMinimum, direction);
                float overlapX = overlap(center.minX(), center.maxX(), arm.minX(), arm.maxX());
                float overlapY = overlap(center.minY(), center.maxY(), arm.minY(), arm.maxY());
                float overlapZ = overlap(center.minZ(), center.maxZ(), arm.minZ(), arm.maxZ());
                assertFalse(overlapX > 0 && overlapY > 0 && overlapZ > 0, direction + " overlaps center");
                switch (direction.getAxis()) {
                    case X -> assertEquals(direction == Direction.WEST ? center.minX() : center.maxX(),
                            direction == Direction.WEST ? arm.maxX() : arm.minX());
                    case Y -> assertEquals(direction == Direction.DOWN ? center.minY() : center.maxY(),
                            direction == Direction.DOWN ? arm.maxY() : arm.minY());
                    case Z -> assertEquals(direction == Direction.NORTH ? center.minZ() : center.maxZ(),
                            direction == Direction.NORTH ? arm.maxZ() : arm.minZ());
                }
            }
            for (int i = 0; i < Direction.values().length; i++) {
                var first = VisualPipeLaneGeometry.arm(layerMinimum, Direction.values()[i]);
                for (int j = i + 1; j < Direction.values().length; j++) {
                    var second = VisualPipeLaneGeometry.arm(layerMinimum, Direction.values()[j]);
                    assertFalse(overlap(first.minX(), first.maxX(), second.minX(), second.maxX()) > 0
                                    && overlap(first.minY(), first.maxY(), second.minY(), second.maxY()) > 0
                                    && overlap(first.minZ(), first.maxZ(), second.minZ(), second.maxZ()) > 0,
                            Direction.values()[i] + " overlaps " + Direction.values()[j]);
                }
            }
        }
    }

    private static float overlap(float minA, float maxA, float minB, float maxB) {
        return Math.min(maxA, maxB) - Math.max(minA, minB);
    }
}
