package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Functional and visual acceptance for exposed-to-concealed primary joins. */
@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class ConcealedPipeJoinGameTests {
    private ConcealedPipeJoinGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "concealed_pipe_join", timeoutTicks = 100)
    public static void everyDirectionRequiresLiveMatchingPrimaryAndRoundTripsRenderMask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipe = helper.absolutePos(new BlockPos(3, 1, 3));
        placePrimary(level, pipe);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pipe);
        require(LoadedPipeGasOwner.createVacuumLane(level, pipe, VisualPipeBlockEntity.PRIMARY).isPresent(), "create live exposed primary owner");
        for (Direction direction : Direction.values()) {
            BlockPos host = pipe.relative(direction);
            helper.setBlock(host.subtract(helper.absolutePos(BlockPos.ZERO)), ModBlocks.STEEL_WALL_BLOCK.get());
            require(LoadedSealedWallSegment.prepareOne(level, host, direction, PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isPresent(),
                    "admit matching primary wall lane " + direction);
            require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, host, PipeTopology.Layer.PRIMARY),
                    "create live primary wall owner " + direction);
            visual.refreshConcealedJoins();
            require(visual.hasLayerArm(VisualPipeBlockEntity.PRIMARY, direction), "matching live join arm " + direction);
            require(level.getBlockState(pipe).getValue(ExposedPipeVisualBlock.property(direction)), "primary blockstate direction " + direction);
            BlockState refreshed = level.getBlockState(pipe).updateShape(direction,
                    level.getBlockState(pipe.relative(direction)), level, pipe, pipe.relative(direction));
            require(refreshed.getValue(ExposedPipeVisualBlock.property(direction)),
                    "vanilla shape update preserves concealed primary direction " + direction);
            CompoundTag update = visual.getUpdateTag(level.registryAccess());
            VisualPipeBlockEntity replica = new VisualPipeBlockEntity(pipe, visual.getBlockState());
            replica.handleUpdateTag(update, level.registryAccess());
            require(replica.getUpdateTag(level.registryAccess()).getInt("concealed_join_mask")
                            == update.getInt("concealed_join_mask"), "serialized render mask round trip " + direction);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "concealed_pipe_join", timeoutTicks = 100)
    public static void cosmeticWrongLaneAndWrongAxisNeverArm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipe = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos wall = pipe.east();
        helper.setBlock(new BlockPos(4, 1, 3), ModBlocks.STEEL_WALL_BLOCK.get());
        require(LoadedSealedWallSegment.prepareOne(level, wall, Direction.EAST, PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isPresent(), "prepare primary wall segment");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, wall, PipeTopology.Layer.PRIMARY), "create primary wall owner");
        placePrimary(level, pipe);
        VisualPipeBlockEntity entity = (VisualPipeBlockEntity) level.getBlockEntity(pipe);
        entity.refreshConcealedJoins();
        require(!entity.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.EAST), "cosmetic own lane cannot join");
            require(LoadedPipeGasOwner.createVacuumLane(level, pipe, VisualPipeBlockEntity.PRIMARY).isPresent(), "activate primary lane");
        require(entity.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.EAST), "matching primary lane joins");
        require(!entity.hasLayerArm(VisualPipeBlockEntity.SECONDARY, Direction.EAST), "wrong exposed lane does not join");
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 1, 3), ModBlocks.STEEL_WALL_BLOCK.get());
        require(!entity.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.EAST), "replacement host clears stale arm");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "concealed_pipe_join", timeoutTicks = 100)
    public static void secondaryOnlyDoesNotConnectPrimaryToPrimaryNeighbor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos primary = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos secondary = primary.west();
        placePrimary(level, primary);
        placePrimary(level, secondary);
        ((VisualPipeBlockEntity) level.getBlockEntity(secondary)).setLayersForTest(VisualPipeBlockEntity.SECONDARY);
        ((VisualPipeBlockEntity) level.getBlockEntity(primary)).refreshConcealedJoins();
        require(!level.getBlockState(primary).getValue(ExposedPipeVisualBlock.property(Direction.WEST)),
                "primary does not connect toward secondary-only neighbor");
        require(!level.getBlockState(secondary).getValue(ExposedPipeVisualBlock.property(Direction.EAST)),
                "secondary-only neighbor has no primary connection");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "concealed_pipe_join", timeoutTicks = 100)
    public static void adjacentWrongAxisWallDoesNotRenderJoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipe = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos wall = pipe.east();
        helper.setBlock(new BlockPos(4, 1, 3), ModBlocks.STEEL_WALL_BLOCK.get());
        require(LoadedSealedWallSegment.prepareOne(level, wall, Direction.NORTH, PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isPresent(), "prepare perpendicular primary segment");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, wall, PipeTopology.Layer.PRIMARY), "create perpendicular primary owner");
        placePrimary(level, pipe);
        require(LoadedPipeGasOwner.createVacuumLane(level, pipe, VisualPipeBlockEntity.PRIMARY).isPresent(), "activate primary exposed node");
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pipe);
        visual.refreshConcealedJoins();
        require(!visual.hasLayerArm(VisualPipeBlockEntity.PRIMARY, Direction.EAST), "wrong-axis adjacent host leaves arm hidden");
        require(!level.getBlockState(pipe).getValue(ExposedPipeVisualBlock.property(Direction.EAST)), "wrong-axis blockstate remains disconnected");
        helper.succeed();
    }

    private static void placePrimary(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual != null && visual.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "install primary visual lane");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
