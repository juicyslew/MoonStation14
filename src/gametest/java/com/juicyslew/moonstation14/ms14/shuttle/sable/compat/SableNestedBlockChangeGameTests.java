package com.juicyslew.moonstation14.ms14.shuttle.sable.compat;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixturePlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestTags({"shuttle-block-updates", "shuttle"})
@PrefixGameTestTemplate(false)
public final class SableNestedBlockChangeGameTests {
    private SableNestedBlockChangeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void ordinaryChunkPipePlacementRestoresOuterBlockContext(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos outer = helper.absolutePos(new BlockPos(5, 1, 5));
        assertNestedPipeWrite(level, outer);
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 60)
    public static void plotPipePlacementRestoresOuterBlockContext(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos worldOrigin = helper.absolutePos(new BlockPos(2, 8, 2));
        SableFixturePlot plot = SableFixturePlot.allocateAndAuthor(level,
                 new Vector3d(worldOrigin.getX() + .5, worldOrigin.getY(), worldOrigin.getZ() + .5), building -> {
                     BlockPos outer = building.storageOrigin().offset(5, 1, 5);
                     building.ensureStorageFootprint(outer, 2, 1, 1);
                    building.ensureStorageChunk(outer);
                    building.ensureStorageChunk(outer.east());
                    assertNestedPipeWrite(level, outer);
                    return building;
                }).plot();
        try {
            helper.succeed();
        } finally {
            plot.close();
        }
    }

    /** Pipe onPlace recomputes its connection state with a nested setBlockState at the outer cell. */
    private static void assertNestedPipeWrite(ServerLevel level, BlockPos outer) {
        BlockPos neighbor = outer.east();
        var pipe = ModBlocks.GAS_PIPE_VISUAL.get();
        require(level.hasChunkAt(outer) && level.hasChunkAt(neighbor), "both authored cells are already loaded");
        require(level.setBlock(neighbor, pipe.defaultBlockState(), 3), "east visual pipe is authored first");
        VisualPipeBlockEntity neighborBe = requirePipe(level, neighbor);
        require(neighborBe.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "neighbor primary lane is initialized");
        require(LoadedPipeGasOwner.createVacuumLane(level, neighbor, VisualPipeBlockEntity.PRIMARY).isPresent(),
                "real neighbor pipe lane owner is admitted");

        require(level.setBlock(outer, pipe.defaultBlockState(), 3), "outer pipe placement succeeds");
        VisualPipeBlockEntity outerBe = requirePipe(level, outer);
        require(level.getBlockState(outer).getValue(ExposedPipeVisualBlock.EAST),
                "onPlace's nested state write preserves the outer position for Sable's block-change callback");
        require(level.getBlockState(neighbor).getValue(ExposedPipeVisualBlock.WEST),
                "the adjacent callback updates the distinct neighbor position");
        require(level.getBlockEntity(outer) == outerBe && level.getBlockEntity(neighbor) == neighborBe,
                "both positions retain their original real block entities");

        require(outerBe.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "outer primary lane initialized");
        require(LoadedPipeGasOwner.createVacuumLane(level, outer, VisualPipeBlockEntity.PRIMARY).isPresent(),
                "real outer pipe lane owner is admitted");
        require(LoadedPipeComponent.discover(level, outer, VisualPipeBlockEntity.PRIMARY)
                        .orElseThrow(() -> new GameTestAssertException("outer lane component remains discoverable"))
                        .nodes().size() == 2,
                "both correctly-positioned lane owners form the connected two-node component");
        require(level.getBlockState(outer).is(pipe) && level.getBlockEntity(outer) == outerBe,
                "nested callback did not replace the outer authored pipe or its BE");
    }

    private static VisualPipeBlockEntity requirePipe(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof VisualPipeBlockEntity pipe) return pipe;
        throw new GameTestAssertException("real visual-pipe BE initialized at " + pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
