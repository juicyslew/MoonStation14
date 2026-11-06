package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

/** Narrow test/fixture adapter: plot chunks are storage, pose position is world space. */
public final class SableFixturePlot implements AutoCloseable {
    private final ServerLevel parent;
    private final ServerSubLevel subLevel;
    private final Vector3d requestedWorldOrigin;
    private boolean authoredPlacementFinished;
    private boolean closed;
    private final List<StorageFootprint> disposableFootprints = new ArrayList<>();
    private final List<BlockPos> individuallyAuthored = new ArrayList<>();

    private record StorageFootprint(BlockPos origin, int width, int height, int depth) { }

    private SableFixturePlot(ServerLevel parent, ServerSubLevel subLevel, Vector3d requestedWorldOrigin) {
        this.parent = parent;
        this.subLevel = subLevel;
        this.requestedWorldOrigin = new Vector3d(requestedWorldOrigin);
    }

    /** Raw allocation is retained for precision diagnostics that intentionally observe unfinalized mass changes. */
    public static SableFixturePlot allocate(ServerLevel parent, Vector3d worldPosition) {
        if (!parent.getServer().isSameThread()) throw new IllegalStateException("Sable fixture allocation must run on server thread");
        var allocated = SubLevelContainer.getContainer(parent).allocateNewSubLevel(
                new Pose3d(new Vector3d(worldPosition), new org.joml.Quaterniond(), new Vector3d(), new Vector3d(1)));
        if (!(allocated instanceof ServerSubLevel serverSubLevel))
            throw new IllegalStateException("server container allocated a non-server sublevel");
        var plot = new SableFixturePlot(parent, serverSubLevel, worldPosition);
        // Pose3d projects (storage - rotationPoint) * scale + position. The authored
        // plot's global storage center is its pivot; Sable does not reset this field.
        serverSubLevel.logicalPose().rotationPoint().set(plot.storageOrigin().getX(),
                plot.storageOrigin().getY(), plot.storageOrigin().getZ());
        // Sable's add observer registers the body before allocateNewSubLevel returns;
        // preserve the updated pivot in the first observer/tracking snapshot before gameplay ticks.
        serverSubLevel.updateLastPose();
        serverSubLevel.updateBoundingBox();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                "[native fixture owner allocated] uuid={} storageOrigin={} requestedWorldOrigin={}",
                serverSubLevel.getUniqueId(), plot.storageOrigin(), plot.requestedWorldOrigin);
        return plot;
    }

    /** Wrap a sublevel returned by Sable's public stock assembly API without changing its pose or pivot. */
    public static SableFixturePlot wrapAssembled(ServerLevel parent, ServerSubLevel assembled,
                                                  BlockPos storageOrigin, int width, int height, int depth) {
        if (!parent.getServer().isSameThread()) throw new IllegalStateException("Sable fixture wrapping must run on server thread");
        Objects.requireNonNull(assembled, "assembled");
        if (SubLevelContainer.getContainer(parent).getSubLevel(assembled.getUniqueId()) != assembled)
            throw new IllegalStateException("stock-assembled sublevel is not the live container owner");
        var plot = new SableFixturePlot(parent, assembled,
                assembled.logicalPose().position().get(new Vector3d()));
        if (width < 1 || height < 1 || depth < 1) throw new IllegalArgumentException("storage footprint dimensions must be positive");
        plot.disposableFootprints.add(new StorageFootprint(storageOrigin.immutable(), width, height, depth));
        return plot;
    }

    /** Retire only the exact returned assembly owner when adapter construction itself fails. */
    public static void retireReturnedAssembly(ServerLevel parent,ServerSubLevel assembled) {
        var container=SubLevelContainer.getContainer(parent);
        if(container.getSubLevel(assembled.getUniqueId())==assembled)
            container.removeSubLevel(assembled,SubLevelRemovalReason.REMOVED);
    }

    /** Verify an already tracked stock-assembly footprint; callers must register cleanup first. */
    public void validateAssembledFootprint(BlockPos origin,int width,int height,int depth) {
        validateFootprintOwnership(parent,subLevel,origin,width,height,depth,false);
    }

    /**
     * Synchronously author an assembly and finalize its native placement before returning it to runtime callers.
     * The callback runs on the server thread with no intervening world tick; allocation is removed if authoring or
     * finalization fails. Use raw {@link #allocate(ServerLevel, Vector3d)} only for explicitly diagnostic fixtures.
     */
    public static <T> Authored<T> allocateAndAuthor(ServerLevel parent, Vector3d worldPosition,
                                                    Function<SableFixturePlot, T> author) {
        Objects.requireNonNull(author, "author");
        SableFixturePlot plot = allocate(parent, worldPosition);
        try {
            T result = Objects.requireNonNull(author.apply(plot), "authored result");
            plot.finishAuthoredPlacement();
            return new Authored<>(plot, result);
        } catch (RuntimeException | Error failure) {
            plot.close();
            throw failure;
        }
    }

    public record Authored<T>(SableFixturePlot plot, T result) {
        public Authored {
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(result, "result");
        }
    }

    public ServerSubLevel subLevel() { return subLevel; }
    public java.util.UUID engineId() { return subLevel.getUniqueId(); }
    public BlockPos storageOrigin() { return subLevel.getPlot().getCenterBlock().immutable(); }
    public Vector3d worldOrigin() { return subLevel.logicalPose().transformPosition(
            new Vector3d(storageOrigin().getX(), storageOrigin().getY(), storageOrigin().getZ())); }
    public Vector3d requestedWorldOrigin() { return new Vector3d(requestedWorldOrigin); }

    /**
     * Finalize one fully-authored fixture before gameplay ticks: preserve its requested world anchor
     * through Sable's final mass-center pivot, then seed native and previous-pose state once.
     */
    private void finishAuthoredPlacement() {
        if (!parent.getServer().isSameThread())
            throw new IllegalStateException("Sable authored placement must be finalized on the server thread");
        if (authoredPlacementFinished)
            throw new IllegalStateException("Sable authored placement can only be finalized once");
        authoredPlacementFinished = true;
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (!handle.isValid()) throw new IllegalStateException("Sable native body must be registered before placement finalization");

        Vector3d correction = requestedWorldOrigin().sub(worldOrigin());
        var pose = subLevel.logicalPose();
        Vector3d nativePosition = pose.position().get(new Vector3d()).add(correction);
        handle.teleport(nativePosition, pose.orientation());
        subLevel.updateLastPose();
        subLevel.updateBoundingBox();

        Vector3d projected = worldOrigin();
        if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y) || !Double.isFinite(projected.z)
                || projected.distance(requestedWorldOrigin) > 1.0e-8)
            throw new IllegalStateException("Sable authored placement failed to preserve requested world anchor: "
                    + projected + " != " + requestedWorldOrigin);
    }

    /** Ensure the explicit global storage chunk exists; Sable's plot lookup itself uses local chunk coordinates. */
    public void ensureStorageChunk(BlockPos storagePosition) {
        ChunkPos global = new ChunkPos(storagePosition);
        var plot = subLevel.getPlot();
        if (!plot.contains(global))
            throw new IllegalArgumentException("storage block is outside this sublevel's reserved plot: " + storagePosition);
        ChunkPos local = plot.toLocal(global);
        if (plot.getChunk(local) == null) plot.newEmptyChunk(global);
        if (!(parent.getChunkSource().getChunk(global.x, global.z, ChunkStatus.FULL, false) instanceof LevelChunk))
            throw new IllegalStateException("Sable plot storage chunk did not become loaded in parent ServerLevel: " + global);
    }

    /** Allocate only the engine-owned chunks intersecting a bounded authored storage footprint. */
    public void ensureStorageFootprint(BlockPos origin, int width, int height, int depth) {
        if (width < 1 || height < 1 || depth < 1) throw new IllegalArgumentException("storage footprint dimensions must be positive");
        validateFootprintOwnership(parent, subLevel, origin, width, height, depth, false);
        int minChunkX = origin.getX() >> 4, maxChunkX = (origin.getX() + width - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4, maxChunkZ = (origin.getZ() + depth - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                ensureStorageChunk(new BlockPos(chunkX << 4, origin.getY(), chunkZ << 4));
        disposableFootprints.add(new StorageFootprint(origin.immutable(), width, height, depth));
    }

    /** Writes through ServerLevel's normal hooks at the global plot-storage block coordinate. */
    public boolean setStorageBlock(BlockPos storagePosition, BlockState state) {
        ensureStorageChunk(storagePosition);
        if (!individuallyAuthored.contains(storagePosition)) individuallyAuthored.add(storagePosition.immutable());
        return parent.setBlock(storagePosition, state, 3);
    }

    /** Normal block-removal lifecycle, limited to this adapter's explicitly authored storage footprints. */
    public static void removeDisposableFootprint(ServerLevel level, ServerSubLevel owner,
                                                  BlockPos origin, int width, int height, int depth) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("fixture teardown must run on server thread");
        if (width < 1 || height < 1 || depth < 1) throw new IllegalArgumentException("storage footprint dimensions must be positive");
        if (!registeredOwner(level, owner)) throw new IllegalStateException("fixture teardown refused: Sable plot is not the live container owner");
        validateFootprintOwnership(level, owner, origin, width, height, depth, true);
        java.util.Set<BlockPos> pipePositions = pipeOwnerPositions(level, origin, width, height, depth);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) {
            BlockPos pos = origin.offset(x, y, z);
            if (!level.getBlockState(pos).isAir())
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        retirePipeOwnersAtAir(level, pipePositions);
    }

    private static java.util.Set<BlockPos> pipeOwnerPositions(ServerLevel level, BlockPos origin, int width, int height, int depth) {
        var inventory = com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level)
                .map(data -> data.snapshot().snapshot()).orElse(java.util.Map.of());
        java.util.Set<BlockPos> positions = new java.util.LinkedHashSet<>();
        for (var key : inventory.keySet()) {
            BlockPos pos = key.position();
            if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + width
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + height
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + depth) positions.add(pos.immutable());
        }
        return positions;
    }

    private static void retirePipeOwnersAtAir(ServerLevel level, java.util.Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner.retireOrphanAtAir(
                    level, pos, com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.PRIMARY))
                throw new IllegalStateException("fixture teardown could not retire pipe owners at proven AIR: " + pos);
        }
    }

    private static boolean registeredOwner(ServerLevel level, ServerSubLevel owner) {
        return owner != null
                && SubLevelContainer.getContainer(level).getSubLevel(owner.getUniqueId()) == owner;
    }

    private static void validateFootprintOwnership(ServerLevel level, ServerSubLevel owner, BlockPos origin, int width, int height,
                                                   int depth, boolean requireLoaded) {
        var plot = owner.getPlot();
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            BlockPos pos = origin.offset(x, 0, z);
            if (pos.getY() < level.getMinBuildHeight()
                    || (long) pos.getY() + height > level.getMaxBuildHeight())
                throw new IllegalArgumentException("storage footprint exceeds level build height: " + origin);
            ChunkPos chunk = new ChunkPos(pos);
            if (!plot.contains(chunk)) throw new IllegalArgumentException("storage footprint is outside this sublevel plot: " + pos);
            if (requireLoaded && !(level.getChunkSource().getChunk(chunk.x, chunk.z,
                    ChunkStatus.FULL, false) instanceof LevelChunk))
                throw new IllegalArgumentException("teardown footprint chunk is not loaded: " + chunk);
        }
    }

    @Override public void close() {
        boolean removedAtEntry = subLevel.isRemoved();
        boolean registeredAtEntry = SubLevelContainer.getContainer(parent).getSubLevel(subLevel.getUniqueId()) == subLevel;
        com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                "[native fixture owner close] uuid={} closed={} isRemoved={} registered={}",
                subLevel.getUniqueId(), closed, removedAtEntry, registeredAtEntry);
        if (closed) return;
        var container = SubLevelContainer.getContainer(parent);
        var currentOwner = container.getSubLevel(subLevel.getUniqueId());
        // A missing or replaced UUID slot is already retired from this fixture's perspective.
        // Never let a late close clean blocks belonging to a replacement owner.
        if (currentOwner != subLevel) {
            closed = true;
            return;
        }
        RuntimeException cleanupFailure = null;
        try {
            ShuttleSimulationResidency.forLevel(parent).release(subLevel);
            for (StorageFootprint footprint : disposableFootprints)
                removeDisposableFootprint(parent, subLevel, footprint.origin(), footprint.width(), footprint.height(), footprint.depth());
            java.util.Set<BlockPos> individualPipePositions = new java.util.LinkedHashSet<>();
            for (BlockPos pos : individuallyAuthored) individualPipePositions.addAll(pipeOwnerPositions(parent, pos, 1, 1, 1));
            for (BlockPos pos : individuallyAuthored)
                if (parent.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk
                        && !parent.getBlockState(pos).isAir())
                    parent.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            retirePipeOwnersAtAir(parent, individualPipePositions);
        } catch (RuntimeException failure) {
            cleanupFailure = failure;
        } finally {
            // Even a malformed footprint must not leak the returned native owner.
            retireReturnedAssembly(parent,subLevel);
        }
        boolean registeredAfter = container.getSubLevel(subLevel.getUniqueId()) == subLevel;
        com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                "[native fixture owner close result] uuid={} isRemoved={} registered={}",
                subLevel.getUniqueId(), subLevel.isRemoved(), registeredAfter);
        if (!registeredAfter) closed = true;
        if (cleanupFailure != null) throw cleanupFailure;
    }
}
