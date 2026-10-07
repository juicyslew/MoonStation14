package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.infrastructure.scanner.ScanGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Set;
import java.util.stream.Collectors;

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class PipeScanCandidatesGameTests {
    private PipeScanCandidatesGameTests() { }

@GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void findsOnlyActiveFunctionalLanesWithoutMutatingInventory(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(center, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(center);
        require(visual != null && visual.setLayersForTest(VisualPipeBlockEntity.PRIMARY
                | VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY), "crossed pipe setup");
        require(LoadedPipeGasOwner.createVacuumLane(level, center, VisualPipeBlockEntity.SECONDARY).isPresent(), "secondary owner");
        require(LoadedPipeGasOwner.createVacuumLane(level, center, VisualPipeBlockEntity.TERTIARY).isPresent(), "tertiary owner");

        BlockPos vertical = center.above(4);
        level.setBlock(vertical, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity verticalVisual = (VisualPipeBlockEntity) level.getBlockEntity(vertical);
        require(verticalVisual != null && verticalVisual.setLayersForTest(VisualPipeBlockEntity.SECONDARY),
                "vertical pipe setup");
        require(LoadedPipeGasOwner.createVacuumLane(level, vertical, VisualPipeBlockEntity.SECONDARY).isPresent(), "vertical owner");
        BlockPos distant = center.east(6);
        level.setBlock(distant, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity distantVisual = (VisualPipeBlockEntity) level.getBlockEntity(distant);
        require(distantVisual != null && distantVisual.setLayersForTest(VisualPipeBlockEntity.TERTIARY),
                "distant pipe setup");
        require(LoadedPipeGasOwner.createVacuumLane(level, distant, VisualPipeBlockEntity.TERTIARY).isPresent(), "distant owner");
        BlockPos outside = center.east(10);
        level.setBlock(outside, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        require(LoadedPipeGasOwner.createVacuumLane(level, outside, VisualPipeBlockEntity.PRIMARY).isPresent(), "outside owner");

        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        long revision = saved.revision();
        var before = saved.snapshot();
        var candidates = PipeScanCandidates.readLoaded(level,
                new Vec3(center.getX() + .5, center.getY() + .5, center.getZ() + .5), 7,
                ScanGeometry.MAX_CANDIDATES);
        Set<String> actual = candidates.stream().map(candidate -> candidate.position().toShortString() + ":" + candidate.lane())
                .collect(Collectors.toSet());
        Set<String> expected = Set.of(center.toShortString() + ":" + VisualPipeBlockEntity.SECONDARY,
                center.toShortString() + ":" + VisualPipeBlockEntity.TERTIARY,
                vertical.toShortString() + ":" + VisualPipeBlockEntity.SECONDARY,
                distant.toShortString() + ":" + VisualPipeBlockEntity.TERTIARY);
        require(actual.containsAll(expected), "all active fixture lanes in 3D radius returned");
        require(!actual.contains(center.toShortString() + ":" + VisualPipeBlockEntity.PRIMARY),
                "inactive center lane excluded");
        require(!actual.contains(outside.toShortString() + ":" + VisualPipeBlockEntity.PRIMARY),
                "out-of-radius fixture lane excluded");
        require(saved.revision() == revision && saved.snapshot() == before, "query leaves inventory unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void concealedWallCandidateRequiresCurrentHostGeneration(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        LoadedSealedWallSegment.prepareOne(level, wall, net.minecraft.core.Direction.EAST,
                PipeTopology.Layer.PRIMARY, java.util.UUID.randomUUID()).orElseThrow();
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(
                level, wall, PipeTopology.Layer.PRIMARY), "wall primary owner");

        BlockPos exposed = wall.east(2);
        level.setBlock(exposed, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(exposed);
        require(visual != null && visual.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "exposed lane setup");
        require(LoadedPipeGasOwner.createVacuumLane(level, exposed, VisualPipeBlockEntity.PRIMARY).isPresent(),
                "exposed lane owner");

        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var before = saved.snapshot();
        var wallKey = new PipeTopology.NodeKey(wall, PipeTopology.Layer.PRIMARY);
        var wallGas = before.get(wallKey).orElseThrow().mixture();
        var query = new Vec3(wall.getX() + .5, wall.getY() + .5, wall.getZ() + .5);
        var initial = PipeScanCandidates.readLoaded(level, query, 5, 32);
        require(initial.stream().anyMatch(candidate -> candidate.position().equals(wall)), "live concealed candidate found");
        require(initial.stream().anyMatch(candidate -> candidate.position().equals(exposed)), "exposed lane found");

        // Replace the host through the world API, restoring the same wall block type afterward.
        level.setBlock(wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        var after = PipeScanCandidates.readLoaded(level, query, 5, 32);
        require(after.stream().noneMatch(candidate -> candidate.position().equals(wall)),
                "stale concealed candidate disappears");
        require(after.stream().anyMatch(candidate -> candidate.position().equals(exposed)),
                "separate exposed lane remains");
        var retained = saved.snapshot().get(wallKey).orElseThrow().mixture();
        require(retained.gasMoles().equals(wallGas.gasMoles())
                        && Double.compare(retained.thermalEnergy(), wallGas.thermalEnergy()) == 0,
                "host invalidation preserves saved gas");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void missingFullNeighborChunkFailsClosedWhenFixtureLeavesItUnloaded(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos boundary = helper.absolutePos(new BlockPos(15, 1, 3));
        int neighborX = (boundary.getX() >> 4) + 1;
        int chunkZ = boundary.getZ() >> 4;
        if (level.getChunkSource().getChunk(neighborX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false)
                != null) {
            helper.succeed();
            return;
        }
        var result = PipeScanCandidates.readLoaded(level,
                new Vec3(boundary.getX() + .5, boundary.getY() + .5, boundary.getZ() + .5), 2, 32);
        require(result.isEmpty(), "missing full chunk fails closed");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
