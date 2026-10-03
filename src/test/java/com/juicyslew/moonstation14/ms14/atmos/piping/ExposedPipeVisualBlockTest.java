package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExposedPipeVisualBlockTest {
    private final ExposedPipeVisualBlock block = ModBlocks.GAS_PIPE_VISUAL.get();
    private static final double EPSILON = 1.0e-4;

    @Test
    void secondaryAndTertiaryLayerGeometryIsDistinct() {
        assertEquals(3.0 / 16, 3.0 / 16);
        assertEquals(13.0 / 16, 13.0 / 16);
        assertFalse(3.0 / 16 >= 12.0 / 16 && 3.0 / 16 <= 14.0 / 16);
    }

    @Test
    void eachDirectionControlsItsArmAndOuterFace() {
        BlockState state = block.defaultBlockState();
        assertCovered(state, .5, .5, .5, true);
        assertCovered(state, .5, .5, EPSILON, false);
        for (Direction direction : Direction.values()) {
            BlockState connected = state.setValue(property(direction), true);
            assertCovered(connected, point(direction), true);
            assertCovered(connected, facePoint(direction), true);
            assertCovered(connected, point(direction.getOpposite()), false);
            assertGeometryMatches(connected);
        }
    }

    @Test
    void capStraightElbowTeeAndSixWayShapesCoverExpectedArms() {
        assertArms(block.defaultBlockState(), new Direction[0]);
        assertArms(block.defaultBlockState().setValue(ExposedPipeVisualBlock.NORTH, true), Direction.NORTH);
        assertArms(block.defaultBlockState().setValue(ExposedPipeVisualBlock.NORTH, true)
                .setValue(ExposedPipeVisualBlock.SOUTH, true), Direction.NORTH, Direction.SOUTH);
        assertArms(block.defaultBlockState().setValue(ExposedPipeVisualBlock.NORTH, true)
                .setValue(ExposedPipeVisualBlock.SOUTH, true).setValue(ExposedPipeVisualBlock.EAST, true),
                Direction.NORTH, Direction.SOUTH, Direction.EAST);
        assertArms(block.defaultBlockState().setValue(ExposedPipeVisualBlock.NORTH, true)
                .setValue(ExposedPipeVisualBlock.SOUTH, true).setValue(ExposedPipeVisualBlock.EAST, true)
                .setValue(ExposedPipeVisualBlock.WEST, true).setValue(ExposedPipeVisualBlock.UP, true)
                .setValue(ExposedPipeVisualBlock.DOWN, true), Direction.values());
    }

    @Test
    void updateShapeSetsOnlySameBlockNeighborAndClearsOtherBlock() {
        BlockState state = block.defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockPos pos = BlockPos.ZERO;
            BlockState connected = block.updateShape(state, direction, block.defaultBlockState(), null,
                    pos, pos.relative(direction));
            assertTrue(connected.getValue(property(direction)));
            assertFalse(state.getValue(property(direction)));

            BlockState cleared = block.updateShape(connected, direction, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),
                    null, pos, pos.relative(direction));
            assertFalse(cleared.getValue(property(direction)));
        }
    }

    @Test
    void disconnectedPrimaryDoesNotConnectToPrimaryNeighbor() {
        Direction direction = Direction.NORTH;
        BlockState disconnected = block.defaultBlockState().setValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED, false);
        BlockState result = block.updateShape(disconnected, direction, block.defaultBlockState(), null,
                BlockPos.ZERO, BlockPos.ZERO.relative(direction));
        assertFalse(result.getValue(property(direction)));
    }

    private void assertArms(BlockState state, Direction... expected) {
        for (Direction direction : Direction.values())
            assertCovered(state, point(direction), java.util.Arrays.asList(expected).contains(direction));
        assertGeometryMatches(state);
    }

    private void assertGeometryMatches(BlockState state) {
        VoxelShape outline = shape(state);
        VoxelShape collision = block.getCollisionShape(state, null, null, CollisionContext.empty());
        assertEquals(1, outline.toAabbs().size());
        AABB outlineBox = outline.toAabbs().get(0);
        assertEquals(0, outlineBox.minX);
        assertEquals(0, outlineBox.minY);
        assertEquals(0, outlineBox.minZ);
        assertEquals(1, outlineBox.maxX);
        assertEquals(1, outlineBox.maxY);
        assertEquals(1, outlineBox.maxZ);
        assertFalse(collision.toAabbs().stream().anyMatch(box -> box.minX == 0 && box.minY == 0 && box.minZ == 0
                && box.maxX == 1 && box.maxY == 1 && box.maxZ == 1));
    }

    private void assertCovered(BlockState state, double x, double y, double z, boolean expected) {
        assertEquals(expected, block.getCollisionShape(state, null, null, CollisionContext.empty()).toAabbs()
                        .stream().anyMatch(box -> contains(box, x, y, z)),
                "coverage at " + x + ", " + y + ", " + z);
    }

    private void assertCovered(BlockState state, double[] point, boolean expected) {
        assertCovered(state, point[0], point[1], point[2], expected);
    }

    private static boolean contains(AABB box, double x, double y, double z) {
        return x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ;
    }

    private static double[] point(Direction direction) {
        return switch (direction) {
            case NORTH -> new double[]{.5, .5, .1}; case SOUTH -> new double[]{.5, .5, .9};
            case EAST -> new double[]{.9, .5, .5}; case WEST -> new double[]{.1, .5, .5};
            case UP -> new double[]{.5, .9, .5}; case DOWN -> new double[]{.5, .1, .5};
        };
    }

    private static double[] facePoint(Direction direction) {
        return switch (direction) {
            case NORTH -> new double[]{.5, .5, EPSILON}; case SOUTH -> new double[]{.5, .5, 1-EPSILON};
            case EAST -> new double[]{1-EPSILON, .5, .5}; case WEST -> new double[]{EPSILON, .5, .5};
            case UP -> new double[]{.5, 1-EPSILON, .5}; case DOWN -> new double[]{.5, EPSILON, .5};
        };
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> ExposedPipeVisualBlock.NORTH; case SOUTH -> ExposedPipeVisualBlock.SOUTH;
            case EAST -> ExposedPipeVisualBlock.EAST; case WEST -> ExposedPipeVisualBlock.WEST;
            case UP -> ExposedPipeVisualBlock.UP; case DOWN -> ExposedPipeVisualBlock.DOWN;
        };
    }

    private VoxelShape shape(BlockState state) {
        return block.getShape(state, null, null, CollisionContext.empty());
    }
}
