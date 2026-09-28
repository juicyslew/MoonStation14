package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.LindaGasSharing;
import com.juicyslew.moonstation14.ms14.atmos.core.ExcitedAtmosphereGroups;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.BoundedGasEqualizer;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosSpaceFlow;
import com.juicyslew.moonstation14.ms14.atmos.core.ImmutableAtmosphereBoundary;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualServerHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.Function;

/** Server-authoritative sparse atmosphere queries, mutations, and bounded diffusion. */
public final class AtmosphereService {
    public static final AtmosphereService INSTANCE = new AtmosphereService();
    public static final int TICK_CADENCE = 2;
    public static final double atmosphereStepSeconds = TICK_CADENCE / 20.0;
    public static final int MAX_PAIR_EDGES_PER_TICK = 64;
    public static final int MAX_CELLS_INSPECTED_PER_TICK = 256;
    static final int MAX_DEFERRED_CHUNK_LOADS_PER_TICK = 16;
    static final int MAX_DISCOVERY_PROBES_PER_TICK = 384;
    static final int MAX_OWNERSHIP_CLAIMS_PER_TICK = 128;
    static final int MAX_PENDING_OWNERSHIP_CLAIM_PLANS = 256;
    static final int MAX_EXTERIOR_WITNESS_CHECKS = 4096;
    private static final int MAX_QUEUED_POSITIONS = 8192;
    private static final int MAX_URGENT_POSITIONS = 256;
    private static final int MAX_CONSECUTIVE_URGENT_POLLS = 8;
    static final int PRIORITY_SEED_CAPACITY = 256;
    private static final int PRIORITY_SEEDS_BEFORE_FIFO = 4;
    static final int MAX_DIRTY_CURSOR_STEPS_PER_TICK = 64;
    static final int LINDA_MAX_ACTIVE_CELLS = MAX_PAIR_EDGES_PER_TICK / Direction.values().length;
    private static final double BOUNDARY_TRANSFER_FRACTION = 0.125;
    private static final double CONVERGENCE_EPSILON = 1.0e-8;

    private final Map<ServerLevel, WorkQueue> queues = new WeakHashMap<>();
    private final Map<ServerLevel, DeferredChunkQueue> deferredChunkLoads = new WeakHashMap<>();
    private final Map<ServerLevel, BoundaryLedger> boundaryLedgers = new WeakHashMap<>();
    private final Map<ServerLevel, Set<BlockPos>> spaceBoundaryCellsThisCycle = new WeakHashMap<>();
    private final Map<ServerLevel, EqualizationJob> equalizationJobs = new WeakHashMap<>();
    private final Map<ServerLevel, PrioritySeedQueue> prioritySeeds = new WeakHashMap<>();
    private final Map<ServerLevel, Integer> prioritySeedStreaks = new WeakHashMap<>();
    private final Map<ServerLevel, OwnershipWork> ownershipWork = new WeakHashMap<>();
    private final Map<ServerLevel, OpenableStateTracker> openableStates = new WeakHashMap<>();
    private final Map<ServerLevel, ExcitedAtmosphereGroups> excitedGroups = new WeakHashMap<>();
    private final Map<ServerLevel, Long> nextExcitedCycle = new WeakHashMap<>();
    private volatile Set<ResourceKey<Level>> vacuumDimensions;
    private volatile boolean enabled;

    private AtmosphereService() { this(Set.of(), false); }
    private AtmosphereService(Set<ResourceKey<Level>> vacuumDimensions, boolean enabled) {
        this.vacuumDimensions = Set.copyOf(vacuumDimensions);
        this.enabled = enabled;
    }

    /** Creates an isolated policy instance; no process-global dimension configuration is mutated. */
    public static AtmosphereService withVacuumDimensions(Set<ResourceKey<Level>> vacuumDimensions) {
        return new AtmosphereService(vacuumDimensions, true);
    }

    /** Installs the common-config policy at server startup without rewriting saved cells. */
    public void configureAtServerStart(boolean enabled, Set<ResourceKey<Level>> vacuumDimensions) {
        clearAll();
        this.vacuumDimensions = Set.copyOf(vacuumDimensions);
        this.enabled = enabled;
    }

    /** Compatibility overload for policy-focused callers predating the startup switch. */
    public void configureAtServerStart(Set<ResourceKey<Level>> vacuumDimensions) {
        configureAtServerStart(true, vacuumDimensions);
    }

    public boolean isEnabled() { return enabled; }

    /** Clears transient work and policy so integrated/server restarts cannot inherit state. */
    public void onServerStopped() {
        clearAll();
        vacuumDimensions = Set.of();
        enabled = false;
    }

    /** Empty means outside world bounds, inaccessible, solid, or in an unloaded chunk. */
    public Optional<GasMixture> sample(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return Optional.empty();
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return Optional.empty();
        Ownership owner = ownership(level, chunk, pos);
        if (owner == Ownership.UNKNOWN) return Optional.empty();
        if (owner == Ownership.EXTERIOR) {
            return Optional.of(ambient(level));
        }
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        GasMixture ambient = ambient(level);
        GasMixture stored = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        return Optional.of(stored == null ? ambient : stored);
    }

    /**
     * Presentation-only atmosphere snapshot. A provisional reading is a UI fallback for an
     * unclaimed passable cell; it is not physical state and must not be used for simulation,
     * mutation, or device rules. Those paths must continue to use strict {@link #sample}.
     */
    public Optional<AtmosphereReading> readAtmosphere(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || !inBounds(level, pos)) return Optional.empty();
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return Optional.empty();
        OwnershipKind owner = ownershipKind(level, chunk, pos);
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        GasMixture stored = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        return Optional.of(readingFor(owner, stored, ambient(level)));
    }

    /** Pure classification/fallback policy, intentionally separate from world access. */
    static AtmosphereReading readingFor(OwnershipKind kind, GasMixture existingOrNull, GasMixture ambient) {
        return switch (kind) {
            case FINITE -> new AtmosphereReading(existingOrNull == null ? ambient : existingOrNull,
                    AtmosphereReading.Status.FINITE);
            case EXTERIOR -> new AtmosphereReading(ambient, AtmosphereReading.Status.EXTERIOR);
            case UNKNOWN -> new AtmosphereReading(existingOrNull == null ? ambient : existingOrNull,
                    AtmosphereReading.Status.PROVISIONAL);
        };
    }

    public boolean addGas(ServerLevel level, BlockPos pos, GasType type, double moles, double injectionTemperature) {
        if (!enabled || type == null || !Double.isFinite(moles) || moles <= 0.0 || !Double.isFinite(injectionTemperature) || injectionTemperature < 0.0) return false;
        if (isLoadedPassableExterior(level, pos)) return false;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return false;
        try {
            GasMixture changed = current.get().withGasDelta(type, moles, injectionTemperature);
            return write(level, pos, changed);
        } catch (IllegalArgumentException exception) { return false; }
    }

    /** Injects breathable air as one composition and one cell write. */
    public boolean addBreathableAir(ServerLevel level, BlockPos pos, double totalMoles, double temperatureKelvin) {
        if (!enabled || !Double.isFinite(totalMoles) || totalMoles <= 0.0
                || !Double.isFinite(temperatureKelvin) || temperatureKelvin <= 0.0) return false;
        if (isLoadedPassableExterior(level, pos)) return false;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return false;
        try {
            GasMixture changed = current.get()
                    .withGasDelta(GasType.OXYGEN, totalMoles * 0.21, temperatureKelvin)
                    .withGasDelta(GasType.NITROGEN, totalMoles * 0.79, temperatureKelvin);
            return write(level, pos, changed);
        } catch (IllegalArgumentException exception) { return false; }
    }

    /** Removes up to the requested amount proportionally across all species. */
    public double removeGasUpTo(ServerLevel level, BlockPos pos, double maxMoles) {
        if (!enabled || !Double.isFinite(maxMoles) || maxMoles <= 0.0) return 0.0;
        if (isLoadedPassableExterior(level, pos)) return 0.0;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return 0.0;
        GasMixture before = current.get();
        double total = before.totalMoles();
        if (total <= 0.0) return 0.0;
        double removed = Math.min(maxMoles, total);
        GasMixture changed = before.withScaledMoles((total - removed) / total);
        return write(level, pos, changed) ? removed : 0.0;
    }

    public boolean addEnergy(ServerLevel level, BlockPos pos, double joules) {
        if (!enabled || !Double.isFinite(joules) || joules == 0.0) return false;
        if (isLoadedPassableExterior(level, pos)) return false;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return false;
        try { return write(level, pos, current.get().withEnergyDelta(joules)); }
        catch (IllegalArgumentException | IllegalStateException exception) { return false; }
    }

    /** Cheap gas/topology activation. This path never enumerates persisted chunk overrides. */
    public void activateCellAndNeighbors(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || !inBounds(level, pos) || loadedChunk(level, pos) == null) return;
        enqueueLocalStencil(queue(level), pos);
    }

    /** Block topology changes also reactivate saved overrides in the affected vertical column. */
    public void topologyChanged(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || !inBounds(level, pos)) return;
        excitedGroups.remove(level);
        cancelEqualization(level);
        invalidateOwnershipAt(level, pos);
        prioritizeSeed(level, pos);
        activateCellAndNeighbors(level, pos);
        WorkQueue urgentQueue = queue(level);
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            LevelChunk adjacentChunk = loadedChunk(level, adjacent);
            if (adjacentChunk != null && isFiniteClaimed(adjacentChunk, adjacent)) urgentQueue.offerUrgent(adjacent);
        }
        LevelChunk chunk = loadedChunk(level, pos);
        AtmosphereChunkData data = chunk == null ? null : chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (data != null) {
            ChunkPos cp = chunk.getPos();
            WorkQueue queue = queue(level);
            for (AtmosphereChunkData.CellPosition cell : data.columnSnapshot(pos.getX() & 15, pos.getZ() & 15))
                enqueue(queue, new BlockPos(cp.getMinBlockX() + cell.x(), cell.y(), cp.getMinBlockZ() + cell.z()));
        }
    }

    /** Neighbor notifications are broad; only a known proof cell becoming sealed is topology. */
    public void neighborNotified(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || !inBounds(level, pos)) return;
        // The event is broad and may be reported on a neighbor of the changed block. Inspect
        // only the event cell and its six loaded neighbors for openable state transitions.
        boolean openableChanged = observeOpenableAt(level, pos);
        for (Direction direction : Direction.values())
            openableChanged |= observeOpenableAt(level, pos.relative(direction));
        if (openableChanged) return;
        OwnershipWork work = ownershipWork.get(level);
        if (work == null || !work.hasExteriorDependency(pos)) return;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk != null && !AtmosphereTopology.isPassable(level, pos)) topologyChanged(level, pos);
    }

    private boolean observeOpenableAt(ServerLevel level, BlockPos pos) {
        if (!inBounds(level, pos) || loadedChunk(level, pos) == null) return false;
        var state = level.getBlockState(pos);
        Boolean open = state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.OPEN)
                : state.getBlock() instanceof TrapDoorBlock ? state.getValue(TrapDoorBlock.OPEN)
                : state.getBlock() instanceof FenceGateBlock ? state.getValue(FenceGateBlock.OPEN)
                : null;
        if (open == null) return false;
        OpenableStateTracker tracker = openableStates.computeIfAbsent(level, ignored -> new OpenableStateTracker());
        boolean changed = tracker.observe(pos, open);
        OwnershipWork work = ownershipWork.get(level);
        // Even if the bounded history evicted this position, a currently sealed openable in a
        // cached witness must invalidate that witness rather than relying on a remembered state.
        if (!open && work != null && work.hasExteriorDependency(pos)) changed = true;
        if (changed) topologyChanged(level, pos);
        return changed;
    }

    /** Compatibility entry point for ordinary local invalidation; intentionally cheap. */
    public void invalidate(ServerLevel level, BlockPos pos) { activateCellAndNeighbors(level, pos); }

    public void tick(ServerLevel level, long gameTime) {
        if (!isTickDue(enabled, gameTime) || level == null) return;
        Set<BlockPos> spaceBoundaryCells = new HashSet<>();
        spaceBoundaryCellsThisCycle.put(level, spaceBoundaryCells);
        advanceOwnership(level);
        WorkQueue queue = queue(level);
        int retrySteps = retryDirtyChunks(level, queue, MAX_DIRTY_CURSOR_STEPS_PER_TICK);
        Set<BlockPos> monstermosHandledThisCycle = new HashSet<>();
        if (equalizationJobs.get(level) == null) {
            BlockPos seed = pollEqualizationSeed(level, queue);
            if (seed != null && !isLoadedPassableExterior(level, seed)) {
                if (loadedChunk(level, seed) == null) {
                    PrioritySeedQueue priority = prioritySeeds.get(level);
                    if (priority != null) priority.defer(seed);
                } else if (sample(level, seed).isPresent()) {
                    equalizationJobs.put(level, new EqualizationJob(seed));
                }
            }
        }
        advanceEqualization(level, queue, monstermosHandledThisCycle);
        processLindaTick(level, queue, spaceBoundaryCells, retrySteps, monstermosHandledThisCycle, gameTime);
    }

    static boolean isTickDue(boolean enabled, long gameTime) {
        return enabled && gameTime % TICK_CADENCE == 0;
    }

    /** Records only coordinates because ChunkEvent.Load can precede promotion to FULL. */
    void deferChunkLoad(ServerLevel level, ChunkPos chunk) {
        if (!enabled || level == null || chunk == null) return;
        deferredChunkLoads.computeIfAbsent(level, ignored -> new DeferredChunkQueue()).offer(chunk);
    }

    /** Resolves a bounded number of recorded chunks without forcing any chunk loads. */
    void processDeferredChunkLoads(ServerLevel level) {
        if (!enabled || level == null) return;
        DeferredChunkQueue deferred = deferredChunkLoads.get(level);
        if (deferred == null) return;
        deferred.drain(MAX_DEFERRED_CHUNK_LOADS_PER_TICK, pos -> {
            ChunkAccess access = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (!(access instanceof LevelChunk chunk)) return false;
            onChunkLoad(level, chunk);
            return true;
        });
        if (deferred.isEmpty()) deferredChunkLoads.remove(level);
    }

    void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (!enabled || level == null || chunk == null) return;
        cancelEqualization(level);
        OwnershipWork ownership = ownershipWork.get(level);
        if (ownership != null) {
            ownership.retryForChunkLifecycle(chunk.getPos());
            ownership.wakeUnknown(chunk.getPos());
        }
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        ChunkPos cp = chunk.getPos();
        if (data != null) {
            var first = data.nextAfter(null);
            if (first.isPresent()) {
                BlockPos seed = new BlockPos(cp.getMinBlockX() + first.get().getKey().x(), first.get().getKey().y(), cp.getMinBlockZ() + first.get().getKey().z());
                queue(level).markDirtyChunk(cp, seed);
                prioritizeSeed(level, seed);
            }
        }
        // A newly loaded empty chunk can complete a persisted gradient whose active cell is
        // in a neighbor. Only inspect saved overrides on the four horizontal neighbor seams.
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            ChunkPos neighborPos = new ChunkPos(cp.x + direction.getStepX(), cp.z + direction.getStepZ());
            LevelChunk neighbor = loadedChunk(level, new BlockPos(neighborPos.getMinBlockX(), level.getMinBuildHeight(), neighborPos.getMinBlockZ()));
            if (neighbor == null) continue;
            AtmosphereChunkData neighborData = neighbor.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (neighborData == null) continue;
            for (AtmosphereChunkData.CellPosition cell : neighborData.snapshot().keySet()) {
                if (!isSeamCell(cell, direction.getOpposite())) continue;
                BlockPos persisted = new BlockPos(neighborPos.getMinBlockX() + cell.x(), cell.y(), neighborPos.getMinBlockZ() + cell.z());
                activateSeam(level, persisted, direction);
            }
        }
    }

    void onChunkUnload(ServerLevel level, ChunkPos chunk) {
        if (!enabled || level == null || chunk == null) return;
        excitedGroups.remove(level);
        cancelEqualization(level);
        OwnershipWork ownership = ownershipWork.get(level);
        if (ownership != null) {
            ownership.retryForChunkLifecycle(chunk);
            ownership.invalidateChunk(chunk).forEach(ownership::offer);
        }
        DeferredChunkQueue deferred = deferredChunkLoads.get(level);
        if (deferred != null) {
            deferred.remove(chunk);
            if (deferred.isEmpty()) deferredChunkLoads.remove(level);
        }
        WorkQueue queue = queue(level);
        queue.removeChunk(chunk);
        PrioritySeedQueue priority = prioritySeeds.get(level);
        if (priority != null) priority.removeChunk(chunk);
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            ChunkPos neighborPos = new ChunkPos(chunk.x + direction.getStepX(), chunk.z + direction.getStepZ());
            LevelChunk neighbor = loadedChunk(level, new BlockPos(neighborPos.getMinBlockX(), level.getMinBuildHeight(), neighborPos.getMinBlockZ()));
            if (neighbor == null) continue;
            AtmosphereChunkData data = neighbor.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data == null) continue;
            for (AtmosphereChunkData.CellPosition cell : data.snapshot().keySet()) {
                if (!isSeamCell(cell, direction.getOpposite())) continue;
                enqueue(queue, new BlockPos(neighborPos.getMinBlockX() + cell.x(), cell.y(), neighborPos.getMinBlockZ() + cell.z()));
            }
        }
    }

    public void clear(ServerLevel level) { queues.remove(level); deferredChunkLoads.remove(level); boundaryLedgers.remove(level); spaceBoundaryCellsThisCycle.remove(level); equalizationJobs.remove(level); prioritySeeds.remove(level); prioritySeedStreaks.remove(level); ownershipWork.remove(level); openableStates.remove(level); excitedGroups.remove(level); nextExcitedCycle.remove(level); }
    public void clearAll() { queues.clear(); deferredChunkLoads.clear(); boundaryLedgers.clear(); spaceBoundaryCellsThisCycle.clear(); equalizationJobs.clear(); prioritySeeds.clear(); prioritySeedStreaks.clear(); ownershipWork.clear(); openableStates.clear(); excitedGroups.clear(); nextExcitedCycle.clear(); }

    private void advanceEqualization(ServerLevel level, WorkQueue queue, Set<BlockPos> monstermosHandledThisCycle) {
        EqualizationJob job = equalizationJobs.get(level);
        if (job == null) return;
        BoundedRegionDiscovery.Snapshot result = job.discovery.step(pos -> {
            if (!inBounds(level, pos)) return BoundedRegionDiscovery.ProbeResult.BLOCKED;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) return BoundedRegionDiscovery.ProbeResult.UNKNOWN_UNLOADED;
            if (!AtmosphereTopology.isPassable(level, pos)) return BoundedRegionDiscovery.ProbeResult.BLOCKED;
            if (!isFiniteClaimed(chunk, pos))
                return isExterior(chunk, pos) || isCachedExterior(level, pos)
                        ? BoundedRegionDiscovery.ProbeResult.EXTERIOR_IMMUTABLE
                        : BoundedRegionDiscovery.ProbeResult.BLOCKED;
            return BoundedRegionDiscovery.ProbeResult.FINITE_LOADED;
        }, MAX_DISCOVERY_PROBES_PER_TICK);
        if (result.status() == BoundedRegionDiscovery.Status.PATCH_READY
                || result.status() == BoundedRegionDiscovery.Status.COMPLETE) {
            if (!result.cells().isEmpty()) commitPatch(level, queue, job, result.cells(), monstermosHandledThisCycle);
            if (result.status() == BoundedRegionDiscovery.Status.PATCH_READY) {
                job.discovery.consumePatch();
            } else {
                equalizationJobs.remove(level);
            }
        } else if (result.status() == BoundedRegionDiscovery.Status.HARD_LIMIT) {
            // HARD_LIMIT is a bounded-work boundary, never a finite component boundary. Commit
            // whatever finite cells fit in this patch, then hand the unclassified frontier to
            // the fair capped seed scheduler instead of repeatedly starting at the old origin.
            // Its overflow is intentionally coalesced per chunk: in an overloaded chunk only one
            // continuation branch is retained until more ordinary activation supplies another.
            if (!result.cells().isEmpty()) commitPatch(level, queue, job, result.cells(), monstermosHandledThisCycle);
            result.continuationSeeds().forEach(seed -> prioritizeSeed(level, seed));
            equalizationJobs.remove(level);
        } else if (result.status() == BoundedRegionDiscovery.Status.UNKNOWN_CHUNK) {
            BlockPos retry = job.discovery.retryCandidate();
            if (retry != null) prioritizeSeed(level, retry);
            equalizationJobs.remove(level);
        } else if (result.status() == BoundedRegionDiscovery.Status.CANCELLED) {
            equalizationJobs.remove(level);
        }
    }

    private void commitPatch(ServerLevel level, WorkQueue queue, EqualizationJob job, List<BlockPos> cells,
                             Set<BlockPos> monstermosHandledThisCycle) {
        if (cells.isEmpty() || cells.size() > MonstermosEqualization.MAX_CELLS) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return;
        }
        List<GasMixture> currentMixtures = new java.util.ArrayList<>(cells.size());
        Map<BlockPos, GasMixture> finite = new java.util.LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency = new java.util.LinkedHashMap<>();
        Set<BlockPos> selected = new HashSet<>(cells);
        Set<BlockPos> exterior = new HashSet<>();
        boolean invalid = false;
        for (BlockPos pos : cells) {
            LevelChunk chunk = loadedChunk(level, pos);
            GasMixture current = chunk == null || !AtmosphereTopology.isPassable(level, pos) || !isFiniteClaimed(chunk, pos)
                    ? null : sample(level, pos).orElse(null);
            if (current == null) {
                enqueue(queue, job.seed);
                equalizationJobs.remove(level);
                return;
            }
            currentMixtures.add(current);
            finite.put(pos.immutable(), current);
            adjacency.put(pos.immutable(), new HashSet<>());
        }
        if (finite.size() != cells.size()) invalid = true;
        if (!invalid) for (BlockPos pos : cells) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (selected.contains(neighbor)) {
                    LevelChunk neighborChunk = loadedChunk(level, neighbor);
                    if (neighborChunk == null || !AtmosphereTopology.canExchange(level, pos, neighbor)
                            || !isFiniteClaimed(neighborChunk, neighbor)) {
                        invalid = true;
                        break;
                    }
                    adjacency.get(pos).add(neighbor.immutable());
                } else if (isLoadedPassableExterior(level, neighbor)) {
                    exterior.add(neighbor.immutable());
                }
            }
            if (invalid) break;
        }
        if (invalid || !isConnected(cells, adjacency)) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return;
        }
        boolean spacePass = chooseKernelForPatch(exterior) == PatchKernel.SPACE_FLOW;
        boolean normalIncomplete = false;
        Map<BlockPos, GasMixture> after;
        MonstermosSpaceFlow.ExportLedger exported = null;
        try {
            if (spacePass) {
                MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(finite, adjacency, exterior,
                        MonstermosEqualization.MAX_CELLS);
                if (!result.work().complete() || result.states().size() != finite.size()) {
                    cells.forEach(pos -> prioritizeSeed(level, pos));
                    equalizationJobs.remove(level);
                    return;
                }
                after = result.states();
                exported = result.exported();
            } else {
                MonstermosEqualization.Result result = MonstermosEqualization.equalize(finite, adjacency,
                        MonstermosEqualization.MAX_CELLS);
                if (result.states().size() != finite.size() || !result.states().keySet().containsAll(finite.keySet()))
                    throw new IllegalStateException("Monstermos returned an incomplete state map");
                after = result.states();
                normalIncomplete = !result.work().complete();
                if (normalIncomplete && (result.edgeFlows().isEmpty()
                        || finite.entrySet().stream().noneMatch(entry -> !same(entry.getValue(), after.get(entry.getKey()))))) {
                    // No bounded graph work made progress. Let the existing finite-to-finite
                    // local pass process this patch instead of rescheduling the same solver seed.
                    cells.forEach(pos -> enqueueLocalStencil(queue, pos));
                    equalizationJobs.remove(level);
                    return;
                }
            }
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return;
        }
        // All predictable validation is completed before this loop. Server-thread execution
        // prevents topology or mixture changes between preflight and writes.
        if (!applyPatchTransaction(cells, finite, after,
                (pos, state) -> write(level, pos, state, false), () -> { })) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return;
        }
        boolean changed = cells.stream().anyMatch(pos -> !same(finite.get(pos), after.get(pos)));
        if (changed) markMonstermosPatchHandled(cells, monstermosHandledThisCycle, queue);
        if (spacePass) {
            Set<BlockPos> cycle = spaceBoundaryCellsThisCycle.computeIfAbsent(level, ignored -> new HashSet<>());
            for (BlockPos pos : cells) {
                for (Direction direction : Direction.values())
                    if (exterior.contains(pos.relative(direction))) cycle.add(pos.immutable());
            }
            BoundaryLedger ledger = boundaryLedgers.computeIfAbsent(level, ignored -> new BoundaryLedger());
            exported.speciesMoles().forEach((type, amount) -> ledger.gas.merge(type, amount, Double::sum));
            ledger.energy += exported.thermalEnergyJoules();
        }
        List<BlockPos> changedCells = new java.util.ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (!same(currentMixtures.get(i), after.get(cells.get(i)))) {
                changedCells.add(cells.get(i));
                enqueue(queue, cells.get(i));
            }
        }
        if (normalIncomplete) {
            // Partial normal-kernel edges are closed-system transfers. Their committed states
            // alter the next bounded invocation's work and therefore make deterministic
            // progress; all touched cells get a fair next-cycle continuation opportunity.
            changedCells.forEach(pos -> prioritizeSeed(level, pos));
        }
    }

    static boolean applyPatchTransaction(List<BlockPos> positions, Map<BlockPos, GasMixture> before,
                                         Map<BlockPos, GasMixture> after,
                                         java.util.function.BiPredicate<BlockPos, GasMixture> writer,
                                         Runnable afterCommit) {
        List<BlockPos> applied = new java.util.ArrayList<>();
        for (BlockPos pos : positions) {
            GasMixture oldState = before.get(pos);
            GasMixture newState = after.get(pos);
            if (oldState == null || newState == null) throw new IllegalArgumentException("Patch states must cover every position");
            if (same(oldState, newState)) continue;
            boolean written;
            try {
                written = writer.test(pos, newState);
            } catch (RuntimeException failure) {
                rollbackPatch(applied, before, writer, failure);
                throw failure;
            }
            if (!written) {
                rollbackPatch(applied, before, writer, null);
                return false;
            }
            applied.add(pos);
        }
        afterCommit.run();
        return true;
    }

    private static void rollbackPatch(List<BlockPos> applied, Map<BlockPos, GasMixture> before,
                                      java.util.function.BiPredicate<BlockPos, GasMixture> writer,
                                      RuntimeException originalFailure) {
        for (int i = applied.size() - 1; i >= 0; i--) {
            BlockPos rollbackPos = applied.get(i);
            try {
                if (!writer.test(rollbackPos, before.get(rollbackPos)))
                    throw new IllegalStateException("Atmosphere patch rollback failed at " + rollbackPos);
            } catch (RuntimeException rollbackFailure) {
                IllegalStateException failure = new IllegalStateException(
                        "Atmosphere patch rollback failed at " + rollbackPos, rollbackFailure);
                if (originalFailure != null) failure.addSuppressed(originalFailure);
                throw failure;
            }
        }
    }

    static boolean isConnected(List<BlockPos> cells, Map<BlockPos, Set<BlockPos>> adjacency) {
        if (cells.isEmpty()) return false;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(cells.get(0));
        seen.add(cells.get(0));
        while (!pending.isEmpty()) for (BlockPos next : adjacency.getOrDefault(pending.remove(), Set.of()))
            if (seen.add(next)) pending.add(next);
        return seen.size() == cells.size();
    }

    static PatchKernel chooseKernelForPatch(Set<BlockPos> verifiedExteriorFaces) {
        return verifiedExteriorFaces.isEmpty() ? PatchKernel.NORMAL_EQUALIZATION : PatchKernel.SPACE_FLOW;
    }

    static boolean claimBoundaryCellForCycle(Set<BlockPos> cycleCells, BlockPos finiteCell) {
        return cycleCells.add(finiteCell.immutable());
    }

    enum PatchKernel { NORMAL_EQUALIZATION, SPACE_FLOW }

    static List<GasMixture> equalizeCurrentPatch(List<GasMixture> currentMixtures) {
        return BoundedGasEqualizer.equalize(currentMixtures);
    }

    static final class EqualizationJob {
        private final BlockPos seed;
        final BoundedRegionDiscovery discovery;
        private boolean cancelled;
        EqualizationJob(BlockPos seed) {
            this.seed = seed.immutable();
            this.discovery = new BoundedRegionDiscovery(seed);
        }
        void cancel() { cancelled = true; discovery.cancel(); }
        boolean isActive() { return !cancelled; }
    }

    /** Signed transient exchanges: positive means exported from finite cells. */
    public Map<GasType, Double> boundaryGasLedger(ServerLevel level) {
        BoundaryLedger ledger = boundaryLedgers.get(level);
        return ledger == null ? Map.of() : Map.copyOf(ledger.gas);
    }
    public double boundaryEnergyLedger(ServerLevel level) {
        BoundaryLedger ledger = boundaryLedgers.get(level);
        return ledger == null ? 0.0 : ledger.energy;
    }

    int queueCountForTesting() { return queues.size(); }
    int deferredChunkCountForTesting() { return deferredChunkLoads.values().stream().mapToInt(DeferredChunkQueue::size).sum(); }

    private void processLindaTick(ServerLevel level, WorkQueue queue, Set<BlockPos> boundaryCells, int retrySteps,
                                  Set<BlockPos> monstermosHandledThisCycle, long gameTime) {
        Map<BlockPos, GasMixture> finite = new java.util.LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency = new java.util.LinkedHashMap<>();
        List<BlockPos> active = new java.util.ArrayList<>();
        List<BlockPos> boundarySources = new java.util.ArrayList<>();
        List<BlockPos> deferredSources = new java.util.ArrayList<>();
        int inspected = retrySteps;
        while (active.size() < LINDA_MAX_ACTIVE_CELLS
                && inspected + Direction.values().length + 1 <= MAX_CELLS_INSPECTED_PER_TICK && !queue.isEmpty()) {
            BlockPos source = queue.poll();
            inspected += Direction.values().length + 1;
            if (source == null || isLoadedPassableExterior(level, source)) continue;
            if (deferHandledLindaSource(source, monstermosHandledThisCycle, deferredSources)) continue;
            GasMixture mixture = finiteMixture(level, source);
            if (mixture == null) continue;
            active.add(source.immutable());
            finite.putIfAbsent(source.immutable(), mixture);
            adjacency.computeIfAbsent(source.immutable(), ignored -> new LinkedHashSet<>());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = source.relative(direction);
                if (monstermosHandledThisCycle.contains(neighbor)) continue;
                if (!inBounds(level, neighbor) || loadedChunk(level, neighbor) == null
                        || !AtmosphereTopology.canExchange(level, source, neighbor)) continue;
                GasMixture neighborMixture = finiteMixture(level, neighbor);
                if (neighborMixture != null) {
                    BlockPos a = source.immutable(), b = neighbor.immutable();
                    finite.putIfAbsent(b, neighborMixture);
                    adjacency.computeIfAbsent(a, ignored -> new LinkedHashSet<>()).add(b);
                    adjacency.computeIfAbsent(b, ignored -> new LinkedHashSet<>()).add(a);
                } else if (isLoadedPassableExterior(level, neighbor)) {
                    boundarySources.add(source.immutable());
                }
            }
        }
        ExcitedAtmosphereGroups groups = excitedGroups.computeIfAbsent(level, ignored -> new ExcitedAtmosphereGroups());
        Set<LindaGasSharing.Pair> grouped = new HashSet<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : adjacency.entrySet())
            for (BlockPos neighbor : entry.getValue()) {
                LindaGasSharing.Pair pair = LindaGasSharing.Pair.of(entry.getKey(), neighbor);
                if (groups.sameGroupPair(pair)) grouped.add(pair);
            }
        LindaGasSharing.Result result = LindaGasSharing.process(finite, adjacency, Set.copyOf(active), grouped,
                Math.min(800, active.size()));

        // Validate the complete snapshot and every predicted state before the first finite write.
        boolean valid = result.mixtures().keySet().equals(finite.keySet());
        if (valid) for (Map.Entry<BlockPos, GasMixture> entry : finite.entrySet())
            if (!entry.getValue().equals(finiteMixture(level, entry.getKey()))) { valid = false; break; }
        if (!valid || !applyPatchTransaction(new java.util.ArrayList<>(finite.keySet()), finite, result.mixtures(),
                (pos, state) -> write(level, pos, state, false), () -> { })) {
            active.forEach(pos -> enqueue(queue, pos));
            requeueDeferredSources(queue, deferredSources);
            return;
        }
        for (LindaGasSharing.Pair pair : result.sharedPairs()) {
            groups.onShare(pair, result.movedMolesByPair().getOrDefault(pair, 0.0), finite);
        }
        result.mixtures().forEach((pos, after) -> {
            if (!same(finite.get(pos), after)) enqueue(queue, pos);
        });
        result.activatedNeighbors().forEach(pos -> enqueue(queue, pos));

        // Exterior is a service-owned immutable sink; never expose it to LINDA's finite graph.
        Set<BlockPos> visitedBoundary = new HashSet<>();
        for (BlockPos source : boundarySources) {
            if (!visitedBoundary.add(source) || boundaryCells.contains(source)) continue;
            if (equalizationJobs.get(level) != null) {
                boundaryCells.add(source);
                prioritizeSeed(level, source);
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos exterior = source.relative(direction);
                if (isLoadedPassableExterior(level, exterior)
                        && exchangeWithExterior(level, queue, source, exterior)) {
                    claimBoundaryCellForCycle(boundaryCells, source);
                    break;
                }
            }
        }

        requeueDeferredSources(queue, deferredSources);
        advanceExcitedGroupsWhenDue(level, queue, groups, monstermosHandledThisCycle, gameTime);
    }

    private void advanceExcitedGroupsWhenDue(ServerLevel level, WorkQueue queue, ExcitedAtmosphereGroups groups,
                                             Set<BlockPos> monstermosHandledThisCycle, long gameTime) {
        long due = nextExcitedCycle.computeIfAbsent(level, ignored -> ((gameTime + 11) / 12) * 12);
        ExcitedCycleSchedule schedule = excitedCycleSchedule(gameTime, due, !monstermosHandledThisCycle.isEmpty());
        if (!schedule.advance()) return;
        advanceExcitedGroups(level, queue, groups);
        nextExcitedCycle.put(level, schedule.nextDue());
    }

    static ExcitedCycleSchedule excitedCycleSchedule(long gameTime, long nextDue, boolean monstermosHandled) {
        if (gameTime < nextDue || monstermosHandled) return new ExcitedCycleSchedule(false, nextDue);
        return new ExcitedCycleSchedule(true, gameTime + 12);
    }

    record ExcitedCycleSchedule(boolean advance, long nextDue) { }

    static boolean deferHandledLindaSource(BlockPos source, Set<BlockPos> handledThisCycle,
                                           List<BlockPos> deferredSources) {
        if (!handledThisCycle.contains(source)) return false;
        deferredSources.add(source.immutable());
        return true;
    }

    static void markMonstermosPatchHandled(List<BlockPos> patch, Set<BlockPos> handledThisCycle,
                                           WorkQueue queue) {
        patch.forEach(pos -> {
            handledThisCycle.add(pos.immutable());
            queue.offer(pos);
        });
    }

    static void requeueDeferredSources(WorkQueue queue, List<BlockPos> deferredSources) {
        deferredSources.forEach(queue::offer);
    }

    private GasMixture finiteMixture(ServerLevel level, BlockPos pos) {
        if (!inBounds(level, pos)) return null;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return null;
        if (!isFiniteClaimed(chunk, pos)) {
            // Preserve strict-sample ownership discovery for covered-but-unclaimed cells. An
            // unknown cell is never admitted to LINDA's finite snapshot.
            ownership(level, chunk, pos);
            return null;
        }
        return sample(level, pos).orElse(null);
    }

    private void advanceExcitedGroups(ServerLevel level, WorkQueue queue, ExcitedAtmosphereGroups groups) {
        // Take the authoritative bounded membership once per group-processing stage, never per LINDA pair.
        Map<BlockPos, GasMixture> current = trackedFiniteSnapshot(groups, pos -> finiteMixture(level, pos));
        if (current == null) {
            // A stale/unloaded member invalidates this transient level-local group state.
            excitedGroups.remove(level);
            return;
        }
        if (current.isEmpty()) return;
        ExcitedAtmosphereGroups.CycleResult cycle = groups.advanceFullCycle(current, 800);
        if (!cycle.replacements().isEmpty() && !applyPatchTransaction(new java.util.ArrayList<>(cycle.replacements().keySet()),
                current, cycle.replacements(), (pos, state) -> write(level, pos, state, false), () -> { })) {
            cycle.replacements().keySet().forEach(pos -> enqueue(queue, pos));
            return;
        }
        cycle.replacements().forEach((pos, state) -> enqueue(queue, pos));
    }

    static Map<BlockPos, GasMixture> trackedFiniteSnapshot(ExcitedAtmosphereGroups groups,
                                                            Function<BlockPos, GasMixture> sampler) {
        Map<BlockPos, GasMixture> current = new java.util.LinkedHashMap<>();
        for (BlockPos pos : groups.trackedCells()) {
            GasMixture mixture = sampler.apply(pos);
            if (mixture == null) return null;
            current.put(pos, mixture);
        }
        return current;
    }

    /** Under-overhang finite cells may drain into an adjacent directly sky-exposed sink. */
    private boolean exchangeWithExterior(ServerLevel level, WorkQueue queue, BlockPos finitePos, BlockPos exteriorPos) {
        Optional<GasMixture> finite = sample(level, finitePos);
        Optional<GasMixture> exterior = sample(level, exteriorPos);
        if (finite.isEmpty() || exterior.isEmpty()) return false;
        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                finite.get(), exterior.get(), BOUNDARY_TRANSFER_FRACTION, vacuumDimensions.contains(level.dimension()));
        if (!meaningful(finite.get(), result.finiteAfter()) || !write(level, finitePos, result.finiteAfter(), false)) return false;
        BoundaryLedger ledger = boundaryLedgers.computeIfAbsent(level, ignored -> new BoundaryLedger());
        result.perGasExported().forEach((type, amount) -> ledger.gas.merge(type, amount, Double::sum));
        ledger.energy += result.energyExportedJoules();
        if (result.finiteAfter().pressureKpa(1.0) > 10.0) queue.offerUrgent(finitePos);
        else enqueue(queue, finitePos);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = finitePos.relative(direction);
            if (!isLoadedPassableExterior(level, neighbor)) enqueue(queue, neighbor);
        }
        return true;
    }

    private boolean write(ServerLevel level, BlockPos pos, GasMixture state) {
        return write(level, pos, state, true);
    }

    private boolean write(ServerLevel level, BlockPos pos, GasMixture state, boolean invalidateAfterWrite) {
        if (!enabled || level == null || pos == null) return false;
        if (!inBounds(level, pos)) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return false;
        if (!isFiniteClaimed(chunk, pos)) return false;
        GasMixture ambient = ambient(level);
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (data == null && same(state, ambient)) return false;
        if (data == null) data = chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        boolean changed = data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state, ambient);
        if (changed) {
            chunk.setUnsaved(true);
            queue(level).noteChunkMutation(pos);
            AtmosphereVisualServerHooks.noteChanged(level, pos);
            if (invalidateAfterWrite) {
                prioritizeSeed(level, pos);
                activateCellAndNeighbors(level, pos);
            }
        }
        return changed;
    }

    private static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk full ? full : null;
    }

    private boolean isLoadedPassableExterior(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || !inBounds(level, pos)) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        boolean loaded = chunk != null;
        boolean passable = loaded && AtmosphereTopology.isPassable(level, pos);
        return loaded && passable && !isFiniteClaimed(chunk, pos)
                && (isExterior(chunk, pos) || isCachedExterior(level, pos));
    }

    private static boolean isExterior(LevelChunk chunk, BlockPos pos) {
        int firstFreeY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() & 15, pos.getZ() & 15);
        return AtmosphereTopology.isDirectExteriorByHeightmap(true, true, pos.getY(), firstFreeY);
    }

    private boolean isFiniteClaimed(LevelChunk chunk, BlockPos pos) {
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    private Ownership ownership(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        if (isFiniteClaimed(chunk, pos)) return Ownership.FINITE;
        if (isExterior(chunk, pos) || isCachedExterior(level, pos)) return Ownership.EXTERIOR;
        OwnershipWork work = ownershipWork.computeIfAbsent(level, ignored -> new OwnershipWork());
        work.offer(pos);
        return Ownership.UNKNOWN;
    }

    private OwnershipKind ownershipKind(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        if (isFiniteClaimed(chunk, pos)) return OwnershipKind.FINITE;
        if (isExterior(chunk, pos) || isCachedExterior(level, pos)) return OwnershipKind.EXTERIOR;
        // Preserve the ownership search side effect used by strict sample, without claiming or
        // materializing atmosphere data for this presentation-only fallback.
        ownership(level, chunk, pos);
        return OwnershipKind.UNKNOWN;
    }

    private boolean isCachedExterior(ServerLevel level, BlockPos pos) {
        OwnershipWork work = ownershipWork.get(level);
        return work != null && work.isVerifiedExterior(pos);
    }

    private void invalidateOwnershipAt(ServerLevel level, BlockPos changed) {
        OwnershipWork work = ownershipWork.get(level);
        if (work == null) return;
        work.cancelPendingOwnership().forEach(work::offer);
        // A proof is invalidated only when the changed cell was part of that proof. Neighbor
        // notifications for unrelated blocks therefore cannot erase a verified covered gap.
        work.invalidateAt(changed).forEach(work::offer);
        work.wakeParked();
        work.offer(changed);
    }

    private void advanceOwnership(ServerLevel level) {
        OwnershipWork work = ownershipWork.get(level);
        if (work == null) return;
        advanceOwnershipClaims(level, work);
        if (work.active == null) {
            BlockPos seed = work.startNext();
            if (seed == null) return;
        }
        OwnershipJob job = work.active;
        AtmosphereOwnershipSearch.Snapshot result = job.search.step(pos -> {
            if (!inBounds(level, pos)) return AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) return AtmosphereOwnershipSearch.ProbeResult.UNKNOWN_UNLOADED;
            if (!AtmosphereTopology.isPassable(level, pos)) return AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
            if (isFiniteClaimed(chunk, pos)) return AtmosphereOwnershipSearch.ProbeResult.FINITE_CLAIMED;
            return isExterior(chunk, pos) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_SKY_SEED
                    : AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED;
        }, MAX_DISCOVERY_PROBES_PER_TICK);
        if (result.status() == AtmosphereOwnershipSearch.Status.EXTERIOR) {
            if (isLiveExteriorWitness(level, result.visitedOpenCells()))
                work.rememberExteriorProof(job.seed, result.visitedOpenCells());
            else
                work.offer(job.seed);
            work.active = null;
        } else if (result.status() == AtmosphereOwnershipSearch.Status.FINITE) {
            if (!work.queueClaimPlan(job.seed, new OwnershipClaimPlan(result.visitedOpenCells())))
                work.offer(job.seed);
            work.active = null;
        } else if (result.status() == AtmosphereOwnershipSearch.Status.UNKNOWN) {
            // One unloaded boundary must not hold the level's only active search. Keep this seed
            // parked until a chunk load or topology event makes another attempt worthwhile.
            work.parkUnknown(job.seed, job.search.unresolvedChunks());
            work.active = null;
        } else if (result.status() == AtmosphereOwnershipSearch.Status.SATURATED) {
            // This candidate cap is permanent for the current topology. Park the seed by
            // loaded chunk and move on to the next queued room on the next due tick.
            work.parkSaturated(job.seed);
            work.active = null;
        } else if (result.status() == AtmosphereOwnershipSearch.Status.CANCELLED) {
            work.offer(job.seed);
            work.active = null;
        }
    }

    static boolean isExteriorWitnessValid(List<BlockPos> witness, Predicate<BlockPos> loadedPassable,
                                          Predicate<BlockPos> directSky) {
        if (witness.isEmpty() || witness.size() > MAX_EXTERIOR_WITNESS_CHECKS) return false;
        boolean reachesSky = false;
        for (BlockPos pos : witness) {
            if (!loadedPassable.test(pos)) return false;
            if (directSky.test(pos)) reachesSky = true;
        }
        return reachesSky;
    }

    private boolean isLiveExteriorWitness(ServerLevel level, List<BlockPos> witness) {
        return isExteriorWitnessValid(witness, pos -> {
            LevelChunk chunk = loadedChunk(level, pos);
            return chunk != null && AtmosphereTopology.isPassable(level, pos);
        }, pos -> {
            LevelChunk chunk = loadedChunk(level, pos);
            return chunk != null && isExterior(chunk, pos);
        });
    }

    private void advanceOwnershipClaims(ServerLevel level, OwnershipWork work) {
        OwnershipClaimJob job = work.claimPlans.pollFirst();
        if (job == null) return;
        job.plan.advance(MAX_OWNERSHIP_CLAIMS_PER_TICK, pos -> {
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return false;
            AtmosphereChunkData data = chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data.claimFinite(pos.getX() & 15, pos.getY(), pos.getZ() & 15)) {
                chunk.setUnsaved(true);
                AtmosphereVisualServerHooks.noteChanged(level, pos);
            }
            return true;
        });
        if (job.plan.isCancelled()) {
            // Already persisted claims remain valid: they were members of a fully proven finite
            // region when committed. Only the uncommitted tail needs a fresh search.
            work.offer(job.seed);
        } else if (!job.plan.isComplete()) {
            work.claimPlans.addLast(job);
        }
    }

    private enum Ownership { FINITE, EXTERIOR, UNKNOWN }
    enum OwnershipKind { FINITE, EXTERIOR, UNKNOWN }

    private static final class OwnershipJob {
        final BlockPos seed;
        final AtmosphereOwnershipSearch search;
        OwnershipJob(BlockPos seed) {
            this.seed = seed.immutable();
            this.search = new AtmosphereOwnershipSearch(seed);
        }
    }

    private static final class OwnershipClaimJob {
        final BlockPos seed;
        final OwnershipClaimPlan plan;
        OwnershipClaimJob(BlockPos seed, OwnershipClaimPlan plan) {
            this.seed = seed.immutable();
            this.plan = plan;
        }
    }

    static final class OwnershipWork {
        private static final int CACHE_CAPACITY = AtmosphereOwnershipSearch.DEFAULT_MAX_CANDIDATES;
        private static final int SEED_CAPACITY = 8192;
        private static final int OVERFLOW_SEED_CAPACITY = AtmosphereOwnershipSearch.DEFAULT_MAX_CANDIDATES;
        private static final int MAX_PROOF_DEPENDENCIES = MAX_EXTERIOR_WITNESS_CHECKS;
        final LinkedHashSet<ExteriorProof> proofs = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> exterior = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> seeds = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> overflowSeeds = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> unknown = new LinkedHashSet<>();
        final java.util.LinkedHashMap<ChunkPos, BlockPos> saturated = new java.util.LinkedHashMap<>();
        final ArrayDeque<OwnershipClaimJob> claimPlans = new ArrayDeque<>();
        private final java.util.Map<BlockPos, java.util.LinkedHashSet<ExteriorProof>> proofByCell = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, java.util.LinkedHashSet<ExteriorProof>> proofByDependency = new java.util.HashMap<>();
        private final java.util.Map<ChunkPos, java.util.LinkedHashSet<ExteriorProof>> proofsByChunk = new java.util.HashMap<>();
        private final java.util.Map<Long, java.util.LinkedHashSet<ExteriorProof>> proofsByColumn = new java.util.HashMap<>();
        private final java.util.Map<ChunkPos, java.util.LinkedHashSet<BlockPos>> unknownByChunk = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, Set<ChunkPos>> unknownChunksBySeed = new java.util.HashMap<>();
        private int proofReferences;
        OwnershipJob active;
        boolean offer(BlockPos pos) {
            BlockPos key = pos.immutable();
            if (seeds.contains(key) || overflowSeeds.contains(key)) return true;
            if (seeds.size() < SEED_CAPACITY) return seeds.add(key);
            return overflowSeeds.size() < OVERFLOW_SEED_CAPACITY && overflowSeeds.add(key);
        }
        BlockPos startNext() {
            if (active != null) return active.seed;
            if (seeds.size() < SEED_CAPACITY && !overflowSeeds.isEmpty()) {
                var iterator = overflowSeeds.iterator();
                seeds.add(iterator.next());
                iterator.remove();
            }
            if (seeds.isEmpty()) return null;
            BlockPos seed = seeds.iterator().next();
            seeds.remove(seed);
            active = new OwnershipJob(seed);
            return active.seed;
        }
        void parkSaturated(BlockPos seed) {
            active = null;
            ChunkPos chunk = new ChunkPos(seed);
            if (saturated.size() < SEED_CAPACITY || saturated.containsKey(chunk))
                saturated.putIfAbsent(chunk, seed.immutable());
        }
        void wakeSaturated() {
            var iterator = saturated.entrySet().iterator();
            while (iterator.hasNext()) {
                if (offer(iterator.next().getValue())) iterator.remove();
                else break;
            }
        }
        void parkUnknown(BlockPos seed) {
            parkUnknown(seed, Set.of());
        }
        void parkUnknown(BlockPos seed, Set<ChunkPos> dependencies) {
            BlockPos key = seed.immutable();
            if (!(unknown.size() < SEED_CAPACITY || unknown.contains(key))) return;
            unknown.add(key);
            Set<ChunkPos> unique = Set.copyOf(dependencies);
            unknownChunksBySeed.put(key, unique);
            for (ChunkPos dependency : unique)
                unknownByChunk.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(key);
        }
        void wakeUnknown() {
            var iterator = unknown.iterator();
            while (iterator.hasNext()) {
                BlockPos seed = iterator.next();
                if (offer(seed)) {
                    iterator.remove();
                    removeUnknownIndexes(seed);
                }
                else break;
            }
        }
        void wakeUnknown(ChunkPos chunk) {
            Set<BlockPos> waiting = unknownByChunk.get(chunk);
            if (waiting == null) return;
            for (BlockPos seed : List.copyOf(waiting)) {
                if (offer(seed)) {
                    unknown.remove(seed);
                    removeUnknownIndexes(seed);
                }
            }
        }
        private void removeUnknownIndexes(BlockPos seed) {
            Set<ChunkPos> chunks = unknownChunksBySeed.remove(seed);
            if (chunks == null) return;
            for (ChunkPos chunk : chunks) {
                Set<BlockPos> waiting = unknownByChunk.get(chunk);
                if (waiting == null) continue;
                waiting.remove(seed);
                if (waiting.isEmpty()) unknownByChunk.remove(chunk);
            }
        }
        void wakeParked() {
            wakeUnknown();
            wakeSaturated();
        }
        boolean hasActive() { return active != null; }
        void finishActive() { active = null; }
        boolean isSaturated(BlockPos seed) { return saturated.containsKey(new ChunkPos(seed)); }
        int saturatedChunkCount() { return saturated.size(); }
        int queuedSeedCount() { return seeds.size() + overflowSeeds.size(); }
        int unknownSeedCount() { return unknown.size(); }
        AtmosphereOwnershipSearch activeSearchForTesting() { return active == null ? null : active.search; }
        boolean queueClaimPlan(BlockPos seed, OwnershipClaimPlan plan) {
            if (claimPlans.size() >= MAX_PENDING_OWNERSHIP_CLAIM_PLANS) return false;
            claimPlans.addLast(new OwnershipClaimJob(seed, plan));
            return true;
        }
        List<BlockPos> cancelPendingOwnership() {
            List<BlockPos> retrySeeds = new java.util.ArrayList<>();
            if (active != null) {
                retrySeeds.add(active.seed);
                active.search.cancel();
                active = null;
            }
            while (!claimPlans.isEmpty()) {
                OwnershipClaimJob job = claimPlans.removeFirst();
                job.plan.cancel();
                retrySeeds.add(job.seed);
            }
            return retrySeeds;
        }
        List<BlockPos> onChunkLifecycle(ChunkPos chunk) {
            List<BlockPos> retrySeeds = new java.util.ArrayList<>();
            if (active != null && active.search.hasTouchedChunk(chunk)) {
                retrySeeds.add(active.seed);
                active.search.cancel();
                active = null;
            }
            var iterator = claimPlans.iterator();
            while (iterator.hasNext()) {
                OwnershipClaimJob job = iterator.next();
                if (!job.plan.dependsOn(chunk)) continue;
                iterator.remove();
                job.plan.cancel();
                retrySeeds.add(job.seed);
            }
            return retrySeeds;
        }
        List<BlockPos> retryForChunkLifecycle(ChunkPos chunk) {
            List<BlockPos> retrySeeds = onChunkLifecycle(chunk);
            retrySeeds.forEach(this::offer);
            return retrySeeds;
        }
        void rememberExteriorProof(BlockPos seed, List<BlockPos> dependencies) {
            LinkedHashSet<BlockPos> uniqueDependencies = new LinkedHashSet<>();
            dependencies.forEach(pos -> uniqueDependencies.add(pos.immutable()));
            if (uniqueDependencies.isEmpty() || uniqueDependencies.size() > MAX_PROOF_DEPENDENCIES) return;
            while (proofReferences + uniqueDependencies.size() > MAX_PROOF_DEPENDENCIES && !proofs.isEmpty())
                removeProof(proofs.iterator().next());
            if (proofReferences + uniqueDependencies.size() > MAX_PROOF_DEPENDENCIES) return;
            ExteriorProof proof = new ExteriorProof(seed);
            for (BlockPos key : uniqueDependencies) {
                java.util.LinkedHashSet<ExteriorProof> indexed = proofByDependency.get(key);
                if (indexed == null) {
                    indexed = new java.util.LinkedHashSet<>();
                    proofByDependency.put(key, indexed);
                }
                indexed.add(proof);
                proof.dependencies.add(key);
                proofReferences++;
                proof.chunks.add(new ChunkPos(key));
                proof.columns.add(columnKey(key));
                proofsByColumn.computeIfAbsent(columnKey(key), ignored -> new java.util.LinkedHashSet<>()).add(proof);
            }
            for (ChunkPos chunk : proof.chunks)
                proofsByChunk.computeIfAbsent(chunk, ignored -> new java.util.LinkedHashSet<>()).add(proof);
            for (BlockPos key : uniqueDependencies) {
                proofByCell.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(proof);
                exterior.add(key);
            }
            proofs.add(proof);
        }
        boolean isVerifiedExterior(BlockPos pos) {
            Set<ExteriorProof> indexed = proofByCell.get(pos);
            return indexed != null && indexed.stream().anyMatch(proof -> proof.valid);
        }
        boolean hasExteriorDependency(BlockPos pos) {
            return proofByDependency.containsKey(pos) || proofsByColumn.containsKey(columnKey(pos));
        }
        int proofReferenceCountForTesting() { return proofReferences; }
        List<BlockPos> invalidateAt(BlockPos changed) {
            java.util.LinkedHashSet<ExteriorProof> affected = new java.util.LinkedHashSet<>();
            addAll(affected, proofByDependency.get(changed));
            // Heightmap exposure depends on the whole vertical column, not only on the sky cell.
            addAll(affected, proofsByColumn.get(columnKey(changed)));
            return removeAffected(affected);
        }
        List<BlockPos> invalidateChunk(ChunkPos chunk) {
            java.util.LinkedHashSet<ExteriorProof> affected = proofsByChunk.get(new ChunkPos(chunk.x, chunk.z));
            return affected == null ? List.of() : removeAffected(new java.util.LinkedHashSet<>(affected));
        }
        private List<BlockPos> removeAffected(Set<ExteriorProof> affected) {
            List<BlockPos> retries = new java.util.ArrayList<>(affected.size());
            for (ExteriorProof proof : affected) {
                retries.add(proof.seed);
                removeProof(proof);
            }
            return retries;
        }
        private void removeProof(ExteriorProof proof) {
            if (!proofs.remove(proof)) return;
            proof.valid = false;
            for (BlockPos dependency : proof.dependencies) {
                removeIndex(proofByDependency, dependency, proof);
                java.util.LinkedHashSet<ExteriorProof> cellProofs = proofByCell.get(dependency);
                if (cellProofs != null) {
                    cellProofs.remove(proof);
                    if (cellProofs.isEmpty()) {
                        proofByCell.remove(dependency);
                        exterior.remove(dependency);
                    }
                }
                proofReferences--;
            }
            for (ChunkPos chunk : proof.chunks) removeIndex(proofsByChunk, chunk, proof);
            for (long column : proof.columns) removeIndex(proofsByColumn, column, proof);
        }
        private static <K> void removeIndex(java.util.Map<K, java.util.LinkedHashSet<ExteriorProof>> index,
                                            K key, ExteriorProof proof) {
            java.util.LinkedHashSet<ExteriorProof> values = index.get(key);
            if (values == null) return;
            values.remove(proof);
            if (values.isEmpty()) index.remove(key);
        }
        private static void addAll(Set<ExteriorProof> target, Set<ExteriorProof> source) {
            if (source != null) target.addAll(source);
        }
        private static long columnKey(BlockPos pos) {
            return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
        }
        private static final class ExteriorProof {
            final BlockPos seed;
            final List<BlockPos> dependencies = new java.util.ArrayList<>();
            final Set<ChunkPos> chunks = new HashSet<>();
            final Set<Long> columns = new HashSet<>();
            boolean valid = true;
            ExteriorProof(BlockPos seed) { this.seed = seed.immutable(); }
        }
    }

    static void enqueueLocalStencil(WorkQueue queue, BlockPos pos) {
        queue.offer(pos);
        for (Direction direction : Direction.values()) queue.offer(pos.relative(direction));
    }

    static boolean isSameVerticalColumn(AtmosphereChunkData.CellPosition cell, BlockPos pos) {
        return cell.x() == (pos.getX() & 15) && cell.z() == (pos.getZ() & 15);
    }

    private static boolean inBounds(ServerLevel level, BlockPos pos) { return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight(); }
    private GasMixture ambient(ServerLevel level) { return ambient(level.dimension()); }
    GasMixture ambient(ResourceKey<Level> dimension) { return AtmosphereProfiles.ambient(dimension, vacuumDimensions); }
    private WorkQueue queue(ServerLevel level) { return queues.computeIfAbsent(level, ignored -> new WorkQueue(pos -> loadedChunk(level, pos) != null)); }
    private void enqueue(WorkQueue queue, BlockPos pos) {
        queue.offer(pos);
    }

    private void cancelEqualization(ServerLevel level) {
        EqualizationJob job = equalizationJobs.get(level);
        if (job != null) {
            job.cancel();
            equalizationJobs.remove(level);
            enqueue(queue(level), job.seed);
        }
    }

    private void prioritizeSeed(ServerLevel level, BlockPos pos) {
        prioritySeeds.computeIfAbsent(level, ignored -> new PrioritySeedQueue(seed -> loadedChunk(level, seed) != null)).offer(pos);
    }

    private BlockPos pollEqualizationSeed(ServerLevel level, WorkQueue queue) {
        PrioritySeedQueue priority = prioritySeeds.get(level);
        int streak = prioritySeedStreaks.getOrDefault(level, 0);
        SeedSelection selection = selectNextSeed(priority, queue, streak);
        prioritySeedStreaks.put(level, selection.priorityStreak());
        return selection.seed();
    }

    static SeedSelection selectNextSeed(PrioritySeedQueue priority, WorkQueue fifo, int priorityStreak) {
        if (priority != null && priority.hasPending()
                && (fifo.isEmpty() || priorityStreak < PRIORITY_SEEDS_BEFORE_FIFO))
            return new SeedSelection(priority.poll(), priorityStreak + 1);
        BlockPos oldWork = fifo.poll();
        if (oldWork != null) return new SeedSelection(oldWork, 0);
        if (priority != null && priority.hasPending()) return new SeedSelection(priority.poll(), priorityStreak + 1);
        return new SeedSelection(null, 0);
    }

    record SeedSelection(BlockPos seed, int priorityStreak) { }

    private int retryDirtyChunks(ServerLevel level, WorkQueue queue, int budget) {
        int steps = 0;
        while (steps < budget) {
            WorkQueue.DirtyChunk dirty = queue.nextDirtyChunk();
            if (dirty == null) break;
            LevelChunk chunk = loadedChunk(level, dirty.seed());
            if (chunk == null) {
                queue.removeChunk(dirty.chunk());
                continue;
            }
            AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (!dirty.seedQueued()) {
                if (!queue.isHotQueued(dirty.seed()) && !queue.offerHot(dirty.seed())) {
                    steps++;
                    continue;
                }
                dirty.markSeedQueued();
                steps++;
                continue;
            }
            if (data == null) {
                queue.finishDirty(dirty);
                steps++;
                continue;
            }
            var next = data.nextAfter(dirty.cursor());
            if (next.isEmpty()) {
                // Mutations during a pass do not invalidate forward progress. Restart only after
                // reaching the end, so insertions before the cursor get a complete later pass.
                if (dirty.passVersion() != dirty.version()) dirty.restart();
                else queue.finishDirty(dirty);
                steps++;
                continue;
            }
            AtmosphereChunkData.CellPosition cell = next.get().getKey();
            BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + cell.x(), cell.y(), chunk.getPos().getMinBlockZ() + cell.z());
            int stencilSize = Direction.values().length + 1;
            if (queue.hotRemaining() < stencilSize) {
                steps++;
                continue;
            }
            enqueueLocalStencilHot(queue, pos);
            dirty.advance(cell);
            steps++;
        }
        return steps;
    }

    private static void enqueueLocalStencilHot(WorkQueue queue, BlockPos pos) {
        queue.offerHot(pos);
        for (Direction direction : Direction.values()) queue.offerHot(pos.relative(direction));
    }

    private void activateSeam(ServerLevel level, BlockPos persisted, Direction towardLoaded) {
        WorkQueue queue = queue(level);
        enqueue(queue, persisted);
        BlockPos available = persisted.relative(towardLoaded);
        if (loadedChunk(level, available) != null) enqueue(queue, available);
    }

    private static boolean isSeamCell(AtmosphereChunkData.CellPosition cell, Direction outward) {
        return switch (outward) {
            case WEST -> cell.x() == 0;
            case EAST -> cell.x() == 15;
            case NORTH -> cell.z() == 0;
            case SOUTH -> cell.z() == 15;
            default -> false;
        };
    }

    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private static final class BoundaryLedger {
        private final EnumMap<GasType, Double> gas = new EnumMap<>(GasType.class);
        private double energy;
    }

    static boolean same(GasMixture a, GasMixture b) {
        if (a.temperatureKelvin() != b.temperatureKelvin()) return false;
        for (GasType type : GasType.values()) if (a.moles(type) != b.moles(type)) return false;
        return true;
    }
    private static boolean meaningful(GasMixture a, GasMixture b) {
        if (Math.abs(a.temperatureKelvin() - b.temperatureKelvin()) > CONVERGENCE_EPSILON) return true;
        for (GasType type : GasType.values()) if (Math.abs(a.moles(type) - b.moles(type)) > CONVERGENCE_EPSILON) return true;
        return false;
    }

    static final class OpenableStateTracker {
        private static final int MAX_TRACKED_POSITIONS = 8192;
        private final java.util.LinkedHashMap<BlockPos, Boolean> states = new java.util.LinkedHashMap<>(16, 0.75f, true);

        /** First-seen open blocks activate topology; duplicate notifications are ignored. */
        boolean observe(BlockPos pos, boolean open) {
            BlockPos key = pos.immutable();
            Boolean previous = states.put(key, open);
            while (states.size() > MAX_TRACKED_POSITIONS)
                states.remove(states.keySet().iterator().next());
            return previous == null ? open : previous != open;
        }
    }

    static final class WorkQueue {
        static final int HOT_QUEUE_CAPACITY = MAX_QUEUED_POSITIONS;
        final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        private final ArrayDeque<BlockPos> urgent = new ArrayDeque<>();
        final java.util.LinkedHashMap<ChunkPos, DirtyChunk> dirtyChunks = new java.util.LinkedHashMap<>();
        final Set<BlockPos> queued = new HashSet<>();
        private final Predicate<BlockPos> loadedChunk;
        private int consecutiveUrgentPolls;

        WorkQueue() { this(ignored -> true); }
        WorkQueue(Predicate<BlockPos> loadedChunk) { this.loadedChunk = loadedChunk; }

        boolean offer(BlockPos pos) {
            BlockPos key = pos.immutable();
            if (queued.contains(key)) return false;
            if (pending.size() < MAX_QUEUED_POSITIONS) return offerHot(key);
            if (!loadedChunk.test(key)) return false;
            ChunkPos chunk = new ChunkPos(key);
            DirtyChunk dirty = dirtyChunks.get(chunk);
            if (dirty != null) {
                dirty.seed = key;
                dirty.version++;
                return false;
            }
            dirtyChunks.put(chunk, new DirtyChunk(chunk, key));
            return true;
        }

        void markDirtyChunk(ChunkPos chunk, BlockPos seed) {
            DirtyChunk current = dirtyChunks.get(chunk);
            if (current == null) dirtyChunks.put(new ChunkPos(chunk.x, chunk.z), new DirtyChunk(new ChunkPos(chunk.x, chunk.z), seed.immutable()));
            else {
                current.seed = seed.immutable();
                current.version++;
            }
        }

        boolean offerHot(BlockPos pos) {
            BlockPos key = pos.immutable();
            if (pending.size() >= MAX_QUEUED_POSITIONS || !queued.add(key)) return false;
            pending.addLast(key);
            return true;
        }

        boolean offerUrgent(BlockPos pos) {
            BlockPos key = pos.immutable();
            if (urgent.contains(key)) return true;
            if (urgent.size() >= MAX_URGENT_POSITIONS) {
                // Keep work when the urgent lane is saturated. The ordinary queue either stores
                // the coordinate directly or coalesces it into its loaded-chunk dirty marker.
                offer(key);
                return queued.contains(key) || dirtyChunks.containsKey(new ChunkPos(key));
            }
            if (queued.contains(key)) pending.remove(key);
            else if (!queued.add(key)) return false;
            urgent.addLast(key);
            return true;
        }

        BlockPos poll() {
            BlockPos pos;
            if (!urgent.isEmpty() && (pending.isEmpty() || consecutiveUrgentPolls < MAX_CONSECUTIVE_URGENT_POLLS)) {
                pos = urgent.pollFirst();
                consecutiveUrgentPolls++;
            } else {
                pos = pending.pollFirst();
                if (pos != null) consecutiveUrgentPolls = 0;
                else {
                    pos = urgent.pollFirst();
                    if (pos != null) consecutiveUrgentPolls++;
                }
            }
            if (pos != null) queued.remove(pos);
            return pos;
        }

        DirtyChunk nextDirtyChunk() {
            if (dirtyChunks.isEmpty()) return null;
            Map.Entry<ChunkPos, DirtyChunk> first = dirtyChunks.entrySet().iterator().next();
            dirtyChunks.remove(first.getKey());
            dirtyChunks.put(first.getKey(), first.getValue());
            return first.getValue();
        }

        void finishDirty(DirtyChunk dirty) {
            if (dirty.passVersion == dirty.version) dirtyChunks.remove(dirty.chunk, dirty);
            else dirty.restart();
        }

        void noteChunkMutation(BlockPos pos) {
            DirtyChunk dirty = dirtyChunks.get(new ChunkPos(pos));
            if (dirty != null) dirty.version++;
        }

        void removeChunk(ChunkPos chunk) {
            urgent.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
            dirtyChunks.remove(chunk);
            pending.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
            queued.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
        }

        int hotRemaining() { return MAX_QUEUED_POSITIONS - pending.size(); }
        boolean isHotQueued(BlockPos pos) { return queued.contains(pos); }
        boolean isEmpty() { return urgent.isEmpty() && pending.isEmpty() && dirtyChunks.isEmpty(); }

        static final class DirtyChunk {
            final ChunkPos chunk;
            BlockPos seed;
            AtmosphereChunkData.CellPosition cursor;
            long version;
            long passVersion;
            private boolean seedQueued;

            DirtyChunk(ChunkPos chunk, BlockPos seed) {
                this.chunk = chunk;
                this.seed = seed;
            }

            BlockPos seed() { return seed; }
            ChunkPos chunk() { return chunk; }
            long version() { return version; }
            long passVersion() { return passVersion; }
            boolean seedQueued() { return seedQueued; }
            void markSeedQueued() { seedQueued = true; }
            void advance(AtmosphereChunkData.CellPosition cell) { cursor = cell; }
            AtmosphereChunkData.CellPosition cursor() { return cursor; }
            void restart() { cursor = null; seedQueued = false; passVersion = version; }
        }
    }

    /** Bounded urgent seeds; overflow is retained as chunk-level retry work. */
    static final class PrioritySeedQueue {
        private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        private final Set<BlockPos> queued = new HashSet<>();
        private final java.util.LinkedHashMap<ChunkPos, BlockPos> overflowSeeds = new java.util.LinkedHashMap<>();
        private final Predicate<BlockPos> loadedChunk;

        PrioritySeedQueue() { this(ignored -> true); }
        PrioritySeedQueue(Predicate<BlockPos> loadedChunk) { this.loadedChunk = loadedChunk; }

        boolean offer(BlockPos pos) {
            BlockPos key = pos.immutable();
            if (queued.contains(key)) return false;
            if (pending.size() < PRIORITY_SEED_CAPACITY) {
                queued.add(key);
                pending.addLast(key);
            } else {
                if (overflowSeeds.putIfAbsent(new ChunkPos(key), key) != null) return false;
            }
            return true;
        }

        void defer(BlockPos pos) {
            BlockPos key = pos.immutable();
            overflowSeeds.putIfAbsent(new ChunkPos(key), key);
        }

        BlockPos poll() {
            demoteUnavailablePending();
            promoteLoadedOverflow();
            int checked = pending.size();
            while (checked-- > 0) {
                BlockPos pos = pending.removeFirst();
                if (loadedChunk.test(pos)) {
                    queued.remove(pos);
                    promoteLoadedOverflow();
                    return pos;
                }
                queued.remove(pos);
                overflowSeeds.putIfAbsent(new ChunkPos(pos), pos);
            }
            return null;
        }

        boolean isEmpty() { return pending.isEmpty() && overflowSeeds.isEmpty(); }
        boolean hasPending() {
            demoteUnavailablePending();
            promoteLoadedOverflow();
            return !pending.isEmpty();
        }
        int overflowChunkCount() { return overflowSeeds.size(); }

        void removeChunk(ChunkPos chunk) {
            pending.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
            queued.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
            overflowSeeds.remove(new ChunkPos(chunk.x, chunk.z));
        }

        private void promoteLoadedOverflow() {
            if (pending.size() >= PRIORITY_SEED_CAPACITY || overflowSeeds.isEmpty()) return;
            var iterator = overflowSeeds.entrySet().iterator();
            while (pending.size() < PRIORITY_SEED_CAPACITY && iterator.hasNext()) {
                var entry = iterator.next();
                BlockPos seed = entry.getValue();
                if (!loadedChunk.test(seed)) continue;
                iterator.remove();
                if (queued.add(seed)) pending.addLast(seed);
            }
        }

        private void demoteUnavailablePending() {
            int checked = pending.size();
            while (checked-- > 0) {
                BlockPos seed = pending.removeFirst();
                if (loadedChunk.test(seed)) pending.addLast(seed);
                else {
                    queued.remove(seed);
                    overflowSeeds.putIfAbsent(new ChunkPos(seed), seed);
                }
            }
        }
    }

    static final class DeferredChunkQueue {
        private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
        private final Set<ChunkPos> queued = new LinkedHashSet<>();

        boolean offer(ChunkPos pos) {
            ChunkPos key = new ChunkPos(pos.x, pos.z);
            if (!queued.add(key)) return false;
            pending.addLast(key);
            return true;
        }

        ChunkPos poll() {
            ChunkPos pos = pending.pollFirst();
            if (pos != null) queued.remove(pos);
            return pos;
        }

        void remove(ChunkPos pos) {
            ChunkPos key = new ChunkPos(pos.x, pos.z);
            if (queued.remove(key)) pending.remove(key);
        }

        int drain(int limit, Predicate<ChunkPos> process) {
            int attempts = 0;
            while (attempts < limit) {
                ChunkPos pos = poll();
                if (pos == null) break;
                attempts++;
                if (!process.test(pos)) offer(pos);
            }
            return attempts;
        }

        boolean isEmpty() { return pending.isEmpty(); }
        int size() { return pending.size(); }
    }
}
