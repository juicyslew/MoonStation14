package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallSegment;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeChunkData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.SealedWallHostEligibility;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.ms14.sealedwall.SealedWallHostGenerations;

import java.util.Optional;
import java.util.UUID;

/** Small shared trusted-placement operations for authored fixture pipe networks. */
public final class FixturePipeAdmission {
    private FixturePipeAdmission() { }

    public record ConcealedResult(boolean success, String failedStage, String diagnostics) { }

    public static Optional<UUID> exposed(ServerLevel level, BlockPos pos, int lane) {
        boolean existingPipe = level.getBlockState(pos).is(ModBlocks.GAS_PIPE_VISUAL.get())
                && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity;
        if (!existingPipe && (!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                || level.getBlockEntity(pos) != null)) return Optional.empty();
        if (!existingPipe) level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual)) return Optional.empty();
        if (!visual.hasLayer(lane)) {
            if (visual.getLayerMask() == 0) {
                if (!visual.setLayersForPlacement(lane)) return Optional.empty();
            } else if (!visual.addFunctionalPlacementLayer(lane)) return Optional.empty();
        }
        return LoadedPipeGasOwner.createVacuumLane(level, pos, lane);
    }

    public static boolean concealed(ServerLevel level, BlockPos pos, Direction axis, PipeTopology.Layer lane) {
        return concealedDetailed(level, pos, axis, lane).success();
    }

    public static ConcealedResult concealedDetailed(ServerLevel level, BlockPos pos, Direction axis,
                                                     PipeTopology.Layer lane) {
        var before = PipeGasInventorySavedData.existing(level)
                .map(data -> data.snapshot().get(new PipeTopology.NodeKey(pos, lane)).orElse(null)).orElse(null);
        var prepared = LoadedSealedWallSegment.prepareOne(level, pos, axis, lane, UUID.randomUUID());
        if (prepared.isEmpty()) return new ConcealedResult(false, "segment_prepare",
                "prepareResult=empty,ownerAttempted=false," + diagnostic(level, pos, lane, before));
        if (!LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, lane))
            return new ConcealedResult(false, "owner_create", "prepareResult=" + prepared.get()
                    + ",ownerAttempted=true,ownerResult=false," + diagnostic(level, pos, lane, before));
        return new ConcealedResult(true, "", "");
    }

    private static String diagnostic(ServerLevel level, BlockPos pos, PipeTopology.Layer lane,
                                     PipeGasInventory.Entry before) {
        LevelChunk chunk = null;
        if (pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
            var loaded = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
            if (loaded instanceof LevelChunk full) chunk = full;
        }
        boolean eligible = chunk != null && SealedWallHostEligibility.defaults().eligible(level, pos);
        String block = chunk == null ? "unavailable" : BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString();
        String blockEntity = chunk == null ? "unavailable" : String.valueOf(chunk.getBlockEntity(pos));
        boolean fullCollision = chunk != null && chunk.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
        boolean passable = chunk != null && AtmosphereTopology.isPassable(level, pos);
        PipeChunkData data = chunk == null ? null : chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        PipeChunkData.Record record = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15, lane);
        SealedWallHostGenerations generations = chunk == null ? null
                : chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        var generation = generations == null ? java.util.Optional.<UUID>empty() : generations.active(pos.asLong());
        var currentOwner = PipeGasInventorySavedData.existing(level)
                .map(saved -> saved.snapshot().get(new PipeTopology.NodeKey(pos, lane)).orElse(null)).orElse(null);
        return "block=" + block + ",blockEntity=" + blockEntity + ",fullChunk=" + (chunk != null)
                + ",serverThread=" + level.getServer().isSameThread() + ",eligible=" + eligible
                + ",fullCollision=" + fullCollision + ",atmospherePassable=" + passable
                + ",pipeRecord=" + record + ",hostGenerationActive=" + generation
                + ",priorGasOwner=" + before + ",currentGasOwner=" + currentOwner;
    }
}
