package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the mapped vanilla per-axis shape clipping contract (Minecraft 1.21.1). */
class VanillaAxisNormalizationTest {
    // Minecraft 1.21.1 VoxelShape.collide uses strict abs(delta) < 1e-7 per axis.
    private static final double STOCK_AXIS_EPSILON = Shapes.EPSILON;

    @Test void clipsFloorThenNormalizesOnlyTinyAxes() {
        var floor = Shapes.box(0, 0, 0, 1, 1, 1);
        var body = new AABB(.2, 1.002, .2, .8, 2.802, .8);
        double clippedY = floor.collide(Direction.Axis.Y, body, -.098);
        assertEquals(-.002, clippedY, 1e-12);
        var afterFloorClip = body.move(0, clippedY, 0);
        assertEquals(1.0, afterFloorClip.minY, 1e-12);
        assertEquals(-8.78282436744265e-8, Shapes.empty().collide(Direction.Axis.Z, afterFloorClip, -8.78282436744265e-8));
        assertEquals(8.334619987046437e-7, floor.collide(Direction.Axis.X, afterFloorClip, 8.334619987046437e-7));
        // Shapes.collide's per-axis wrapper zeroes sub-epsilon deltas for nonempty voxel shapes.
        assertEquals(0, Shapes.collide(Direction.Axis.Z, afterFloorClip, List.of(floor), -8.78282436744265e-8));
    }

    @Test void appliesStrictPerAxisThresholdForEitherSign() {
        var shape = Shapes.box(0, 0, 0, 1, 1, 1);
        var body = new AABB(2, 2, 2, 3, 3, 3);
        for (double sign : new double[]{-1, 1}) {
            assertEquals(0, shape.collide(Direction.Axis.X, body, sign * Math.nextDown(STOCK_AXIS_EPSILON)));
            assertEquals(sign * STOCK_AXIS_EPSILON,
                    shape.collide(Direction.Axis.X, body, sign * STOCK_AXIS_EPSILON));
            assertEquals(sign * Math.nextUp(STOCK_AXIS_EPSILON),
                    shape.collide(Direction.Axis.X, body, sign * Math.nextUp(STOCK_AXIS_EPSILON)));
        }
    }

    @Test void emptyShapeListAndFloorClippingKeepTheirOwnContracts() {
        var body = new AABB(.2, 1.002, .2, .8, 2.802, .8);
        assertEquals(-.002, Shapes.collide(Direction.Axis.Y, body,
                List.of(Shapes.box(0, 0, 0, 1, 1, 1)), -.098), 1e-12);
        assertEquals(-8e-8, Shapes.collide(Direction.Axis.Y, body, List.of(), -8e-8));
        assertEquals(-1.2e-7, Shapes.collide(Direction.Axis.Y, body, List.of(), -1.2e-7));
    }
}
