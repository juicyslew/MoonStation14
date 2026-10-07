package com.juicyslew.moonstation14.gametest.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestTags({"ventilation", "hands"})
@PrefixGameTestTemplate(false)
public final class ExposedPipeVisualGameTests {
    private ExposedPipeVisualGameTests() { }

@GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void primaryPipeDoesNotConnectToSecondaryOnlyNeighbor(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos primary = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos secondary = primary.east();
        var pipe = ModBlocks.GAS_PIPE_VISUAL.get();

        level.setBlock(secondary, pipe.defaultBlockState().setValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED, false), 3);
        var secondaryEntity = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(secondary);
        require(secondaryEntity != null && secondaryEntity.setLayersForPlacement(
                com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY),
                "secondary-only neighbor is installed");
        level.setBlock(primary, pipe.defaultBlockState(), 3);
        connected(level, primary, Direction.EAST, false);
        connected(level, secondary, Direction.WEST, false);
        assertLayerPoint(level, secondary, .5, .5, .5, false);
        require(!level.getBlockState(secondary).getValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED),
                "secondary-only state has no primary visual");

        require(secondaryEntity.setLayersForTest(
                        com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.PRIMARY
                                | com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY),
                "primary layer added to the secondary neighbor");
        require(level.getBlockState(secondary).getValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED),
                "adding the primary layer updates its connection state");
        connected(level, primary, Direction.EAST, true);
        connected(level, secondary, Direction.WEST, true);

        helper.succeed();
    }

@GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void independentlyPlacedLayersConnectOnlyAlongTheirOwnLane(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        var pipe = ModBlocks.GAS_PIPE_VISUAL.get();
        int secondary = com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY;
        int tertiary = com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY;
        BlockPos[] positions = {center, center.east(), center.west(), center.north(), center.south()};
        int[] masks = {secondary | tertiary, secondary, secondary, tertiary, tertiary};
        for (int i = 0; i < positions.length; i++) {
            level.setBlock(positions[i], pipe.defaultBlockState().setValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED, false), 3);
            var entity = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(positions[i]);
            require(entity != null && entity.setLayersForTest(masks[i]), "independent layer mask installed");
            require(!level.getBlockState(positions[i]).getValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED),
                    "non-primary layer does not enable primary state");
            require(level.getBlockState(positions[i]).is(pipe), "no air or wall replaces a pipe cell");
        }
        var middle = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(center);
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST}) {
            require(middle.hasLayerArm(secondary, direction), "secondary arm runs east-west");
            require(!middle.hasLayerArm(tertiary, direction), "tertiary has no east-west phantom arm");
            assertLayerPoint(level, center, direction == Direction.EAST ? .8 : .2, .15, .15, true);
        }
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
            require(middle.hasLayerArm(tertiary, direction), "tertiary arm runs north-south");
            require(!middle.hasLayerArm(secondary, direction), "secondary has no north-south phantom arm");
        }
        assertLayerPoint(level, center, .15, .15, .15, true);
        assertLayerPoint(level, center, .5, .5, .5, false);
        assertLayerPoint(level, center, .8125, .8125, .8125, true);
        assertLayerPoint(level, center, .95, .8125, .8125, false);
        assertLayerPoint(level, center, .15, .15, .95, false);

        BlockPos pair = center.east(2);
        level.setBlock(pair, pipe.defaultBlockState().setValue(ExposedPipeVisualBlock.PRIMARY_CONNECTED, false), 3);
        level.setBlock(pair.east(), pipe.defaultBlockState(), 3);
        var secondaryOnly = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(pair);
        var primaryOnly = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(pair.east());
        require(secondaryOnly != null && secondaryOnly.setLayersForPlacement(secondary)
                && primaryOnly != null, "adjacent layers installed");
        require(!secondaryOnly.hasLayerArm(1, Direction.EAST) && !primaryOnly.hasLayerArm(1, Direction.WEST),
                "primary-only neighbor does not connect to secondary-only pipe");
        require(secondaryOnly.setLayersForTest(1 | secondary), "explicit primary layer addition");
        require(secondaryOnly.hasLayerArm(1, Direction.EAST) && primaryOnly.hasLayerArm(1, Direction.WEST),
                "primary arms connect reciprocally after explicit addition");
        helper.succeed();
    }

@GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void exposedPipeConnectsOnlyToAdjacentPipeAndLeavesWallsSealed(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos east = center.east();
        BlockPos south = center.south();
        BlockPos above = center.above();
        BlockPos wall = center.west();
        var pipe = ModBlocks.GAS_PIPE_VISUAL.get();

        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        var wallState = level.getBlockState(wall);
        level.setBlock(center, pipe.defaultBlockState(), 3);
        level.setBlock(east, pipe.defaultBlockState(), 3);
        level.setBlock(south, pipe.defaultBlockState(), 3);
        level.setBlock(above, pipe.defaultBlockState(), 3);
        var centerEntity = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(center);
        var eastEntity = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(east);
        require(centerEntity != null && eastEntity != null, "visual pipe entities are installed");
        require(centerEntity.addLayer(com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY), "secondary layer added");
        assertExtraArm(level, center, Direction.EAST, com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY, "center secondary only", false);
        require(eastEntity.addLayer(com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY), "neighbor secondary layer added");
        assertExtraArm(level, center, Direction.EAST, com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY, "both secondary", true);
        require(eastEntity.addLayer(com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY), "tertiary layer added independently");
        assertExtraArm(level, center, Direction.EAST, com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY, "east tertiary only", false);
        assertExtraArm(level, center, Direction.EAST, com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY, "east tertiary only; secondary remains absent", true);
        require(centerEntity.addLayer(com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY), "center tertiary layer added");
        assertLayerPoint(level, center, .8125, .8125, .8125, true);
        assertExtraArm(level, center, Direction.EAST, com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY, "both tertiary", true);
        assertLayerPoint(level, center, .8, .8125, .8125, true);
        assertLayerPoint(level, center, .1875, .1875, .1875, true);
        require(eastEntity.addLayer(com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY) == false,
                "duplicate layer addition is a no-op");

        connected(level, center, Direction.EAST, true);
        connected(level, east, Direction.WEST, true);
        connected(level, center, Direction.SOUTH, true);
        connected(level, south, Direction.NORTH, true);
        connected(level, center, Direction.UP, true);
        connected(level, above, Direction.DOWN, true);
        connected(level, center, Direction.WEST, false);
        require(level.getBlockState(wall).equals(wallState), "adjacent steel wall remains unchanged");
        require(!AtmosphereTopology.isPassable(level, wall), "steel wall remains sealed to atmosphere topology");
        require(wallState.isCollisionShapeFullBlock(level, wall), "steel wall retains full collision");
        require(AtmosphereTopology.isPassable(level, center),
                "thin visual pipe remains passable to atmosphere topology");
        assertSelectionShape(level, center);
        assertTouchesSharedFace(level, center, Direction.EAST);
        assertTouchesSharedFace(level, center, Direction.SOUTH);
        assertTouchesSharedFace(level, center, Direction.UP);

        level.setBlock(center, Blocks.STONE.defaultBlockState(), 3);
        connected(level, east, Direction.WEST, false);
        connected(level, south, Direction.NORTH, false);
        connected(level, above, Direction.DOWN, false);
        require(!pipe.getCollisionShape(level.getBlockState(east), level, east, CollisionContext.empty())
                .toAabbs().stream().anyMatch(ExposedPipeVisualGameTests::isFullCube),
                "disconnected pipe neighbor has no full-cube collision");
        helper.succeed();
    }

    private static void assertSelectionShape(ServerLevel level, BlockPos pos) {
        var block = ModBlocks.GAS_PIPE_VISUAL.get();
        VoxelShape outline = block.getShape(level.getBlockState(pos), level, pos, CollisionContext.empty());
        VoxelShape collision = block.getCollisionShape(level.getBlockState(pos), level, pos, CollisionContext.empty());
        require(outline.toAabbs().size() == 1 && isFullCube(outline.toAabbs().get(0)),
                "pipe selection/outline covers the full voxel");
        require(!collision.toAabbs().stream().anyMatch(ExposedPipeVisualGameTests::isFullCube),
                "pipe collision remains thin and unchanged");
        require(collision.toAabbs().stream().anyMatch(box -> box.maxX == 1),
                "connected thin pipe endpoint still reaches its face");
    }

    private static void assertExtraArm(ServerLevel level, BlockPos pos, Direction direction, int layer, String stage, boolean expected) {
        VoxelShape shape = ModBlocks.GAS_PIPE_VISUAL.get().getCollisionShape(level.getBlockState(pos), level, pos, CollisionContext.empty());
        double[] point = switch (direction) {
            case EAST -> layer == com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY
                    ? new double[]{.8, .8125, .8125} : new double[]{.8, .15, .15};
            default -> throw new IllegalArgumentException();
        };
        boolean covered = shape.toAabbs().stream().anyMatch(box -> point[0] >= box.minX && point[0] <= box.maxX
                && point[1] >= box.minY && point[1] <= box.maxY && point[2] >= box.minZ && point[2] <= box.maxZ);
        var entity = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(pos);
        var neighbor = (com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity) level.getBlockEntity(pos.relative(direction));
        require(covered == expected, stage + ": layer " + layer + " arm toward " + direction + " at "
                + point[0] + ", " + point[1] + ", " + point[2] + " expected " + expected
                + "; center mask=" + (entity == null ? "missing" : entity.getLayerMask())
                + ", neighbor mask=" + (neighbor == null ? "missing" : neighbor.getLayerMask()));
    }

    private static void assertLayerPoint(ServerLevel level, BlockPos pos, double x, double y, double z, boolean expected) {
        VoxelShape shape = ModBlocks.GAS_PIPE_VISUAL.get().getCollisionShape(level.getBlockState(pos), level, pos, CollisionContext.empty());
        boolean covered = shape.toAabbs().stream().anyMatch(box -> x >= box.minX && x <= box.maxX
                && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ);
        require(covered == expected, "layer point coverage at " + x + ", " + y + ", " + z);
    }

    private static void connected(ServerLevel level, BlockPos pos, Direction direction, boolean expected) {
        var property = switch (direction) {
            case NORTH -> ExposedPipeVisualBlock.NORTH;
            case SOUTH -> ExposedPipeVisualBlock.SOUTH;
            case EAST -> ExposedPipeVisualBlock.EAST;
            case WEST -> ExposedPipeVisualBlock.WEST;
            case UP -> ExposedPipeVisualBlock.UP;
            case DOWN -> ExposedPipeVisualBlock.DOWN;
        };
        require(level.getBlockState(pos).getValue(property) == expected,
                "connection at " + pos + " toward " + direction + " expected " + expected);
    }

    private static void assertTouchesSharedFace(ServerLevel level, BlockPos pos, Direction direction) {
        VoxelShape shape = ModBlocks.GAS_PIPE_VISUAL.get().getCollisionShape(
                level.getBlockState(pos), level, pos, CollisionContext.empty());
        double plane = switch (direction) {
            case NORTH, WEST, DOWN -> 0;
            case SOUTH, EAST, UP -> 1;
        };
        boolean touches = shape.toAabbs().stream().anyMatch(box -> switch (direction) {
            case NORTH -> box.minZ == plane;
            case SOUTH -> box.maxZ == plane;
            case EAST -> box.maxX == plane;
            case WEST -> box.minX == plane;
            case UP -> box.maxY == plane;
            case DOWN -> box.minY == plane;
        });
        require(touches, "connected pipe arm touches shared " + direction + " face");
    }

    private static boolean isFullCube(AABB box) {
        return box.minX == 0 && box.minY == 0 && box.minZ == 0
                && box.maxX == 1 && box.maxY == 1 && box.maxZ == 1;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
