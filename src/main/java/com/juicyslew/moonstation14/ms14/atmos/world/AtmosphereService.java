package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.LindaGasSharing;
import com.juicyslew.moonstation14.ms14.atmos.core.ExcitedAtmosphereGroups;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereDeviceRules;
import com.juicyslew.moonstation14.ms14.atmos.core.BoundedGasEqualizer;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureEqualization;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosSpaceFlow;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotSpread;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotEntityExposure;
import com.juicyslew.moonstation14.ms14.atmos.reaction.ExplosionHotspotExposure;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import com.juicyslew.moonstation14.ms14.fire.FireStackSystem;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualServerHooks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Server-authoritative sparse atmosphere queries, mutations, and bounded diffusion. */
public final class AtmosphereService {
    public static final AtmosphereService INSTANCE = new AtmosphereService();
    /** Compatibility cadence for device/explicit-reaction APIs; scheduler itself uses 15/20 Hz. */
    public static final int TICK_CADENCE = 2;
    public static final double atmosphereStepSeconds = 1.0 / 15.0;
    public static final int MAX_PAIR_EDGES_PER_TICK = 64;
    public static final int MAX_CELLS_INSPECTED_PER_TICK = 256;
    static final int MAX_DEFERRED_CHUNK_LOADS_PER_TICK = 16;
    static final int MAX_DISCOVERY_PROBES_PER_SLICE = 384;
    static final int MAX_OWNERSHIP_CLAIMS_PER_SLICE = 128;
    static final int MAX_FINITE_ROOM_ENDPOINT_PROBES = 1024;
    static final int MAX_PENDING_OWNERSHIP_CLAIM_PLANS = 256;
    static final int MAX_EXTERIOR_WITNESS_CHECKS = AtmosphereOwnershipSearch.DEFAULT_MAX_CANDIDATES;
    private static final int MAX_QUEUED_POSITIONS = 8192;
    private static final int MAX_URGENT_POSITIONS = 256;
    private static final int MAX_CONSECUTIVE_URGENT_POLLS = 8;
    static final int PRIORITY_SEED_CAPACITY = 256;
    private static final int PRIORITY_SEEDS_BEFORE_FIFO = 4;
    static final int MAX_DIRTY_CURSOR_STEPS_PER_TICK = 64;
    static final int MAX_REACTION_INSPECTIONS_PER_TICK = 32;
    static final int MAX_REACTION_LOAD_STEPS_PER_TICK = 32;
    private static final int MAX_ENTITIES_EXPOSED_PER_FIRE_EVENT = 64;
    static final int LINDA_MAX_ACTIVE_CELLS = 800;
    private static final double CONVERGENCE_EPSILON = 1.0e-8;

    private final Map<ServerLevel, WorkQueue> queues = new WeakHashMap<>();
    private final Map<ServerLevel, ReactionWorkQueue> reactionQueues = new WeakHashMap<>();
    private final Map<ServerLevel, LoadedReactionChunks> loadedReactionChunks = new WeakHashMap<>();
    private final Map<ServerLevel, Map<BlockPos, Integer>> fireExposureCursors = new WeakHashMap<>();
    private final Map<ServerLevel, DeferredChunkQueue> deferredChunkLoads = new WeakHashMap<>();
    private final Map<ServerLevel, BoundaryLedger> boundaryLedgers = new WeakHashMap<>();
    /** Interior face heat, by direction; distinct from signed reaction energy and exterior exports. */
    private final Map<ServerLevel, EnumMap<Direction, Double>> hotspotFaceHeat = new WeakHashMap<>();
    /** Persistent whole-air fire-effect slot, independent of per-cycle snapshots. */
    private final Map<ServerLevel, Map<BlockPos, Double>> fireReactionResults = new WeakHashMap<>();
    private final Map<ServerLevel, Set<BlockPos>> spaceBoundaryCellsThisCycle = new WeakHashMap<>();
    private final Map<ServerLevel, EqualizationJob> equalizationJobs = new WeakHashMap<>();
    private final Map<ServerLevel, Map<BlockPos, EqualizationJob>> parkedEqualizationJobs = new WeakHashMap<>();
    /** Seeds needing one fresh-snapshot normal pressure continuation after a committed partial solve. */
    private final Map<ServerLevel, Set<BlockPos>> pressureContinuationSeeds = new WeakHashMap<>();
    private final Map<ServerLevel, AtmosphereCycle> cycles = new WeakHashMap<>();
    private final Map<ServerLevel, PrioritySeedQueue> prioritySeeds = new WeakHashMap<>();
    private final Map<ServerLevel, Integer> prioritySeedStreaks = new WeakHashMap<>();
    private final Map<ServerLevel, OwnershipWork> ownershipWork = new WeakHashMap<>();
    private final Map<ServerLevel, OpenableStateTracker> openableStates = new WeakHashMap<>();
    private final Map<ServerLevel, ExcitedAtmosphereGroups> excitedGroups = new WeakHashMap<>();
    private final Map<ServerLevel, IgnitionDiagnosticHistory> ignitionDiagnostics = new WeakHashMap<>();
    private volatile Set<ResourceKey<Level>> vacuumDimensions;
    private volatile Set<ResourceKey<Level>> spaceDimensions;
    private final Map<ServerLevel, DiagnosticTiming> diagnosticTimings = new WeakHashMap<>();
    private final Map<ServerLevel, AtmosphereRuntimeTelemetry> runtimeTelemetry = new WeakHashMap<>();
    /** Read-only cumulative scheduler snapshot; does not advance or admit work. */
    public AtmosphereRuntimeTelemetry.Snapshot diagnosticRuntime(ServerLevel level) {
        if (level == null) return null;
        AtmosphereRuntimeTelemetry t = runtimeTelemetry.get(level);
        if (t == null) return null;
        AtmosphereCycle cycle = cycles.get(level);
        WorkQueue q = queues.get(level);
        OwnershipWork ow = ownershipWork.get(level);
        Map<BlockPos, EqualizationJob> parked = parkedEqualizationJobs.get(level);
        int activeCandidates = ow == null ? 0 : ow.activeCandidateCount();
        return t.snapshot(level.getGameTime(), System.nanoTime(), cycle == null ? 0 : cycle.completed(),
                cycle == null || cycle.stage() == null ? "idle" : cycle.stage().name(), 0,
                q == null ? 0 : q.pending.size() + q.urgent.size(), ow == null ? 0 : ow.pendingCount(),
                (equalizationJobs.containsKey(level) ? 1 : 0) + (parked == null ? 0 : parked.size()),
                activeCandidates, ow == null ? 0 : ow.retryCandidates.pendingWorkItems(),
                ow == null ? 0 : ow.saturatedChunkCount(), ow == null ? 0 : ow.unknownSeedCount());
    }

    public record CellDiagnostic(String status, GasMixture gas, GasMixture ambient, boolean exteriorConfiguredVacuum) {}
    public record OwnershipDiagnostic(String status, String reason, BlockPos activeSeed, int candidateCount,
                                     String pendingSource, BlockPos lastRejectedSeed,
                                     String lastRejectedReason, int lastRejectedWitnessSize,
                                      int retryWorkItems) {}
    public record IgnitionDiagnostic(BlockPos position, long cycleIdentity, long gameTime, String origin,
                                     double bulkBeforeKelvin, double bulkAfterKelvin, double tritiumBefore,
                                     double oxygenBefore, double hotspotKelvin, double hotspotVolumeLiters,
                                     double fireExtentMoles, String reactionId) { }
    public record CommittedWholeAirDiagnostic(long cycleIdentity, double fireExtentMoles,
                                               List<GasReactionEvaluator.Event> events) {
        public CommittedWholeAirDiagnostic { events = List.copyOf(events); }
    }
    /** Read-only evidence from this cycle's successfully committed LINDA whole-air pass. */
    public Optional<CommittedWholeAirDiagnostic> diagnosticCommittedWholeAir(ServerLevel level, BlockPos position) {
        if (level == null || position == null) return Optional.empty();
        AtmosphereCycle cycle = cycles.get(level);
        return cycle == null ? Optional.empty()
                : committedWholeAirDiagnostic(cycle.identity(), cycle.wholeAir().get(position));
    }
    static Optional<CommittedWholeAirDiagnostic> committedWholeAirDiagnostic(long cycleIdentity,
                                                                              GasReactionStep.WholeAirResult result) {
        return result == null ? Optional.empty()
                : Optional.of(new CommittedWholeAirDiagnostic(cycleIdentity, result.fireExtentMoles(), result.events()));
    }
    public List<IgnitionDiagnostic> diagnosticIgnitions(ServerLevel level, BlockPos position) {
        IgnitionDiagnosticHistory records = ignitionDiagnostics.get(level);
        return records == null ? List.of() : records.recent(position);
    }
    public List<IgnitionDiagnostic> diagnosticFirstIgnitions(ServerLevel level) {
        IgnitionDiagnosticHistory records = ignitionDiagnostics.get(level);
        return records == null ? List.of() : records.firstByCycle();
    }
    /** Loaded service-state read only; never schedules reactions or loads world data. */
    public Optional<HotspotState> diagnosticHotspot(ServerLevel level, BlockPos position) {
        if (level == null || position == null) return Optional.empty();
        ReactionWorkQueue work = reactionQueues.get(level);
        return work == null ? Optional.empty() : Optional.ofNullable(work.hotspot(position));
    }
    /** Read-only current-step commit stamp, with no reaction-clock advancement or queue admission. */
    public long diagnosticHotspotCommitStep(ServerLevel level, BlockPos position) {
        if (level == null || position == null) return Long.MIN_VALUE;
        ReactionWorkQueue work = reactionQueues.get(level);
        return work == null ? Long.MIN_VALUE : work.committedStep(position);
    }
    public record DiagnosticTiming(long gameTime, double ownershipMs, double discoverySolveCommitMs, double lindaMs, double reactionsMs, double totalMs,
                                   String attemptedKernel, String committedKernel, String workStatus, int patchSize, int expansions,
                                   int transferCalls, double exportedMoles, double exportedEnergyJoules, int queued, int dirty, int ownershipPending) {}
    public record PatchDiagnostic(String attempted, String committed, String status, int size, int expansions,
                                   int calls, double exportMoles, double exportEnergy) {}
    public PatchDiagnostic diagnosticPatch(ServerLevel level) { return patchDiagnostics.get(level); }
    private final Map<ServerLevel, PatchDiagnostic> patchDiagnostics = new WeakHashMap<>();
    public record PressureDiagnostic(double pressureSpanBeforeKpa, double pressureSpanAfterKpa, boolean converged,
                                     int selectedCells, int routes, int scalarPreviewSteps, int searchWork,
                                     int faceTransfers, double solverMs) { }
    private final Map<ServerLevel, PressureDiagnostic> pressureDiagnostics = new WeakHashMap<>();
    public PressureDiagnostic diagnosticPressure(ServerLevel level) { return level == null ? null : pressureDiagnostics.get(level); }

    /** Loaded-only diagnostic read: unlike sample(), never advances ownership or queues work. */
    public CellDiagnostic diagnosticCell(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !inBounds(level, pos)) return new CellDiagnostic("unknown", null, null, false);
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return new CellDiagnostic("unknown-unloaded", null, ambient(level), vacuumDimensions.contains(level.dimension()));
        if (!AtmosphereTopology.isPassable(level, pos)) return new CellDiagnostic("blocked", null, ambient(level), vacuumDimensions.contains(level.dimension()));
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (isFiniteClaimed(chunk, pos)) {
            GasMixture gas = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            return new CellDiagnostic("finite", gas == null ? ambient(level) : gas, ambient(level), vacuumDimensions.contains(level.dimension()));
        }
        if (isExterior(chunk, pos) || isCachedExterior(level, pos)) return new CellDiagnostic("exterior", ambient(level), ambient(level), vacuumDimensions.contains(level.dimension()));
        return new CellDiagnostic("unknown", null, ambient(level), vacuumDimensions.contains(level.dimension()));
    }
    /** Read-only ownership explanation; does not probe topology, enqueue work, or load chunks. */
    public OwnershipDiagnostic diagnosticOwnership(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null)
            return new OwnershipDiagnostic("unknown", "invalid target", null, 0, "none", null, null, 0, 0);
        OwnershipWork work = ownershipWork.get(level);
        if (work == null) return new OwnershipDiagnostic("unknown", "no ownership work", null, 0, "none", null, null, 0, 0);
        return work.diagnostic(pos);
    }
    public DiagnosticTiming diagnosticTiming(ServerLevel level) { return diagnosticTimings.get(level); }

    private AtmosphereService() { this(Set.of()); }
    private AtmosphereService(Set<ResourceKey<Level>> vacuumDimensions) {
        this.vacuumDimensions = Set.copyOf(vacuumDimensions);
        this.spaceDimensions = Set.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end")));
    }

    /** Creates an isolated policy instance; no process-global dimension configuration is mutated. */
    public static AtmosphereService withVacuumDimensions(Set<ResourceKey<Level>> vacuumDimensions) {
        AtmosphereService service = new AtmosphereService(vacuumDimensions);
        java.util.HashSet<ResourceKey<Level>> spaces = new java.util.HashSet<>(service.spaceDimensions);
        spaces.addAll(vacuumDimensions);
        service.spaceDimensions = Set.copyOf(spaces);
        return service;
    }

    public static AtmosphereService withDimensions(Set<ResourceKey<Level>> vacuumDimensions,
                                                   Set<ResourceKey<Level>> spaceDimensions) {
        AtmosphereService service = new AtmosphereService(vacuumDimensions);
        service.spaceDimensions = Set.copyOf(spaceDimensions);
        return service;
    }

    /** Installs the common-config policy at server startup without rewriting saved cells. */
    public void configureAtServerStart(Set<ResourceKey<Level>> vacuumDimensions) {
        clearAll();
        this.vacuumDimensions = Set.copyOf(vacuumDimensions);
    }

    /** Installs independent ambient-vacuum and space-behavior dimension profiles. */
    public void configureAtServerStart(Set<ResourceKey<Level>> vacuumDimensions,
                                       Set<ResourceKey<Level>> spaceDimensions) {
        clearAll();
        this.vacuumDimensions = Set.copyOf(vacuumDimensions);
        this.spaceDimensions = Set.copyOf(spaceDimensions);
    }

    public boolean isSpaceDimension(ResourceKey<Level> dimension) { return isSpace(dimension); }
    public Set<ResourceKey<Level>> vacuumDimensionsForTest() { return vacuumDimensions; }
    public Set<ResourceKey<Level>> spaceDimensionsForTest() { return spaceDimensions; }

    /**
     * Creates a transaction endpoint only for an already-persisted finite room cell. Must be
     * called and used on the server thread; raw writes are unpublished and may not span ticks.
     */
    // Package-private until claims have a durable generation token. This seam is not production API.
    Optional<PipeGasTransaction.Endpoint> finiteRoomEndpoint(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null
                || !level.getServer().isSameThread() || !inBounds(level, pos)) return Optional.empty();
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos) || !isFiniteClaimed(chunk, pos)
                || !finiteRoomEnclosureValid(level, pos))
            return Optional.empty();
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (data == null) return Optional.empty();
        GasMixture stored = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        return Optional.of(new FiniteRoomGasEndpoint(this, level, pos.immutable(),
                stored == null ? ambient(level) : stored, true));
    }

    /** Immediate server-thread transaction endpoint for one already-owned finite cell. Unlike the
     * trusted room endpoint, it does not require proving the entire connected component. */
    public Optional<PipeGasTransaction.Endpoint> finiteCellEndpointForImmediateTransfer(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null
                || !level.getServer().isSameThread() || !inBounds(level, pos)) return Optional.empty();
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)
                || !isFiniteClaimed(chunk, pos) || isExterior(chunk, pos)) return Optional.empty();
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        GasMixture stored = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        return Optional.of(new FiniteRoomGasEndpoint(this, level, pos.immutable(),
                stored == null ? ambient(level) : stored, false));
    }

    /** Loaded-only proof of the complete passable component for a live finite endpoint. */
    private boolean finiteRoomEnclosureValid(ServerLevel level, BlockPos start) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        int probes = 0;
        seen.add(start.immutable());
        pending.add(start.immutable());
        while (!pending.isEmpty()) {
            if (++probes > MAX_FINITE_ROOM_ENDPOINT_PROBES) return false;
            BlockPos pos = pending.removeFirst();
            if (!inBounds(level, pos)) return false;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null || !AtmosphereTopology.isPassable(level, pos)
                    || !isFiniteClaimed(chunk, pos) || isExterior(chunk, pos)) return false;
            for (Direction direction : Direction.values()) {
                if (++probes > MAX_FINITE_ROOM_ENDPOINT_PROBES) return false;
                long x = (long) pos.getX() + direction.getStepX();
                long y = (long) pos.getY() + direction.getStepY();
                long z = (long) pos.getZ() + direction.getStepZ();
                if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return false;
                BlockPos adjacent = new BlockPos((int) x, (int) y, (int) z);
                if (!inBounds(level, adjacent)) return false;
                LevelChunk neighbor = loadedChunk(level, adjacent);
                if (neighbor == null) return false;
                if (!AtmosphereTopology.canExchange(level, pos, adjacent)) continue;
                if (!isFiniteClaimed(neighbor, adjacent) || isExterior(neighbor, adjacent)) return false;
                if (seen.add(adjacent)) pending.addLast(adjacent);
            }
        }
        return true;
    }

    /** Trusted server-simulation bridge; callers must be synchronous and server-thread confined. */
    public Optional<PipeGasTransaction.Endpoint> finiteRoomEndpointForTrustedSimulation(ServerLevel level, BlockPos pos) {
        return finiteRoomEndpoint(level, pos);
    }

    /**
     * Replaces a persisted finite cell's complete mixture for trusted authored setup only.
     * Callers must provide their own once-only guard; this is not a gameplay gas source.
     */
    public boolean initializeFiniteMixtureForTrustedSetup(ServerLevel level, BlockPos pos, GasMixture mixture) {
        if (mixture == null) return false;
        Optional<PipeGasTransaction.Endpoint> endpoint = finiteRoomEndpoint(level, pos);
        if (endpoint.isEmpty()) return false;
        GasMixture current = endpoint.get().snapshot();
        if (current == null) return false;
        // Trusted setup reports successful initialization, not whether storage changed. In
        // particular, the authored vacuum in a vacuum dimension is a valid sparse no-op.
        if (same(current, mixture)) return true;
        return write(level, pos, mixture);
    }

    /**
     * Synchronously proves and initializes a bounded authored room before normal ownership work
     * has had a tick to run. Validation failures are mutation-free. This is for trusted,
     * once-guarded setup only.
     */
    public boolean initializeEnclosedFiniteMixtureForTrustedSetup(ServerLevel level, List<BlockPos> cells,
                                                                   GasMixture mixture) {
        if (level == null || cells == null || cells.isEmpty() || cells.size() > 128
                || mixture == null || level.getServer() == null || !level.getServer().isSameThread()) return false;
        Set<BlockPos> supplied = new HashSet<>();
        for (BlockPos cell : cells) {
            if (cell == null || !supplied.add(cell.immutable())) return false;
        }
        Set<BlockPos> proven = new HashSet<>();
        int remaining = MAX_FINITE_ROOM_ENDPOINT_PROBES;
        for (BlockPos seed : supplied) {
            if (proven.contains(seed)) continue;
            LevelChunk seedChunk = loadedChunk(level, seed);
            if (!inBounds(level, seed) || seedChunk == null || !AtmosphereTopology.isPassable(level, seed)) return false;
            // Neighbor discovery examines blocked boundary cells as well as the supplied room.
            // Bound candidates by the trusted-setup inspection budget, not by room size.
            AtmosphereOwnershipSearch search = new AtmosphereOwnershipSearch(seed, MAX_FINITE_ROOM_ENDPOINT_PROBES);
            AtmosphereOwnershipSearch.Snapshot result;
            do {
                if (remaining-- <= 0) return false;
                result = search.step(pos -> {
                    if (!inBounds(level, pos)) return AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
                    LevelChunk chunk = loadedChunk(level, pos);
                    if (chunk == null) return AtmosphereOwnershipSearch.ProbeResult.UNKNOWN_UNLOADED;
                    if (!AtmosphereTopology.isPassable(level, pos)) return AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
                    return isExterior(chunk, pos) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_SKY_SEED
                            : AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED;
                }, 1);
                if (result.status() == AtmosphereOwnershipSearch.Status.UNKNOWN
                        || result.status() == AtmosphereOwnershipSearch.Status.EXTERIOR
                        || result.status() == AtmosphereOwnershipSearch.Status.SATURATED
                        || result.status() == AtmosphereOwnershipSearch.Status.CANCELLED) return false;
            } while (result.status() == AtmosphereOwnershipSearch.Status.IN_PROGRESS);
            if (result.status() != AtmosphereOwnershipSearch.Status.FINITE) return false;
            for (BlockPos visited : result.visitedOpenCells()) {
                if (!supplied.contains(visited) || !proven.add(visited)) return false;
            }
        }
        if (!proven.equals(supplied)) return false;
        // All topology work and target validation finish before the first ownership or gas write.
        Map<BlockPos, LevelChunk> targetChunks = new LinkedHashMap<>();
        for (BlockPos cell : proven) {
            LevelChunk chunk = loadedChunk(level, cell);
            if (chunk == null || !AtmosphereTopology.isPassable(level, cell)) return false;
            targetChunks.put(cell, chunk);
        }

        for (var target : targetChunks.entrySet()) {
            BlockPos cell = target.getKey();
            LevelChunk chunk = target.getValue();
            if (chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get())
                    .claimFinite(cell.getX() & 15, cell.getY(), cell.getZ() & 15)) {
                chunk.setUnsaved(true);
                AtmosphereVisualServerHooks.noteChanged(level, cell);
            }
        }
        for (var target : targetChunks.entrySet())
            writeClaimedMixture(level, target.getKey(), target.getValue(), mixture, false, true);
        return true;
    }

    private static final class FiniteRoomGasEndpoint implements PipeGasTransaction.Endpoint {
        private final AtmosphereService service;
        private final ServerLevel level;
        private final BlockPos pos;
        private final Object identity;
        private final GasMixture initial;
        private final long createdTick;
        private final LevelChunk capturedChunk;
        private final boolean requireEnclosure;
        private boolean pendingPublication;
        private GasMixture pendingAfter;
        private boolean published;

        private FiniteRoomGasEndpoint(AtmosphereService service, ServerLevel level, BlockPos pos, GasMixture initial,
                                      boolean requireEnclosure) {
            this.service = service;
            this.level = level;
            this.pos = pos;
            this.initial = initial;
            this.capturedChunk = loadedChunk(level, pos);
            this.requireEnclosure = requireEnclosure;
            this.createdTick = level.getGameTime();
            // Per-handle identity prevents a same-tick unclaim/reclaim from reusing a stale plan.
            this.identity = new Object();
        }

        private boolean valid() {
            return level.getServer() != null && level.getServer().isSameThread()
                    && level.getGameTime() == createdTick
                    && inBounds(level, pos) && loadedChunk(level, pos) == capturedChunk
                    && AtmosphereTopology.isPassable(level, pos)
                    && service.isFiniteClaimed(loadedChunk(level, pos), pos)
                    && !isExterior(loadedChunk(level, pos), pos)
                    && (!requireEnclosure || service.finiteRoomEnclosureValid(level, pos));
        }

        @Override public Object identity() { return identity; }
        @Override public long version() { return 0L; }
        @Override public GasMixture snapshot() {
            if (!valid()) return null;
            LevelChunk chunk = loadedChunk(level, pos);
            AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data == null) return requireEnclosure ? null : service.ambient(level);
            GasMixture stored = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            return stored == null ? service.ambient(level) : stored;
        }
        @Override public boolean eligible() { return valid() && !pendingPublication; }

        /** Unpublished sparse writes are only valid inside this same synchronous transaction. */
        @Override public boolean writeRaw(GasMixture expectedBefore, GasMixture next) {
            if (!valid() || next == null || published) return false;
            LevelChunk chunk = loadedChunk(level, pos);
            AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data == null && requireEnclosure) return false;
            if (data == null) data = chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            GasMixture current = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            GasMixture currentFull = current == null ? service.ambient(level) : current;
            if (expectedBefore == null) {
                // Compensation may be retried after a write rejected without mutation. Only
                // restore when the cell is still the expected transaction value (or already
                // exactly the initial value); never overwrite an intervening writer.
                if (!exactlySame(next, initial)) return false;
                if (exactlySame(currentFull, initial)) return true;
                if (!pendingPublication || !exactlySame(currentFull, pendingAfter)) return false;
                data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, next, service.ambient(level));
                pendingPublication = false;
                pendingAfter = null;
                return true;
            }
            if (!exactlySame(expectedBefore, currentFull)) return false;
            if (pendingPublication) return false; // no second raw mutation before publish/rollback
            if (exactlySame(currentFull, next)) return true;
            // PipeGasTransaction uses null only for compensating restoration after a possibly
            // mutating failed write. It must restore sparse absence as ambient when available.
            data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, next, service.ambient(level));
            pendingPublication = true;
            pendingAfter = next;
            return true;
        }

        @Override public void publish() {
            if (published) return;
            if (!pendingPublication) {
                if (!valid() || snapshot() == null || !exactlySame(initial, snapshot()))
                    throw new IllegalStateException("finite room endpoint changed before publication");
                return;
            }
            if (!valid() || snapshot() == null)
                throw new IllegalStateException("finite room endpoint is no longer publishable");
            if (!exactlySame(snapshot(), pendingAfter))
                throw new IllegalStateException("finite room endpoint changed before publication");
            LevelChunk chunk = loadedChunk(level, pos);
            chunk.setUnsaved(true);
            service.queue(level).noteChunkMutation(pos);
            service.reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue()).offer(pos);
            AtmosphereVisualServerHooks.noteChanged(level, pos);
            service.prioritizeSeed(level, pos);
            service.activateCellAndNeighbors(level, pos);
            pendingPublication = false;
            pendingAfter = null;
            published = true;
        }
    }

    private static boolean exactlySame(GasMixture a, GasMixture b) {
        if (a == null || b == null || Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0
                || Double.compare(a.thermalEnergy(), b.thermalEnergy()) != 0) return false;
        for (GasType gas : GasType.values())
            if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return true;
    }

    /** Read-only server-authoritative intensity; zero denotes no live committed burn. */
    public int fireIntensity(ServerLevel level, BlockPos pos) {
        ReactionWorkQueue work = level == null ? null : reactionQueues.get(level);
        return work == null || pos == null ? 0 : work.fireIntensity(pos);
    }

    /** Queues a bounded, one-shot blast heat exposure for the next due atmosphere pass. */
    public boolean exposeExplosionHotspot(ServerLevel level, BlockPos pos, double temperatureKelvin, double intensity) {
        if (level == null || pos == null || level.getServer() == null || !level.getServer().isSameThread()
                || !Double.isFinite(temperatureKelvin) || !Double.isFinite(intensity) || intensity <= 0) return false;
        GasMixture gas = reactionFiniteMixture(level, pos);
        if (gas == null) return false;
        ReactionWorkQueue work = reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue());
        HotspotState current = work.hotspot(pos);
        var decision = ExplosionHotspotExposure.decide(gas, temperatureKelvin, intensity,
                current == null ? 0 : current.temperatureKelvin(), current == null ? 0
                        : Math.min(1, current.volumeLiters() / (gas.volumeCubicMeters() * 1000.0)));
        if (!decision.expose() || !work.offerExposure(pos, decision, reactionDueStep(level.getGameTime()) + 1)) return false;
        boolean skippedFirstProcess = cycleLindaProcessed(level, pos);
        explosionHotspotState(gas, current, decision, skippedFirstProcess)
                .ifPresent(state -> work.setHotspot(pos, Optional.of(state)));
        queue(level).offer(pos);
        return true;
    }

    private boolean cycleLindaProcessed(ServerLevel level, BlockPos pos) {
        AtmosphereCycle cycle = cycles.get(level);
        return cycle != null && cycle.lindaProcessedSources().contains(pos);
    }

    static Optional<HotspotState> explosionHotspotState(GasMixture gas, HotspotState current,
                                                        ExplosionHotspotExposure.Decision decision,
                                                        boolean skippedFirstProcess) {
        if (decision == null || !decision.expose()) return Optional.ofNullable(current);
        return HotspotKernel.expose(gas, current, decision.temperatureKelvin(), decision.exposedVolumeLiters(),
                true, skippedFirstProcess);
    }

    /** Returns the next active fire position in this chunk without scanning map cells. */
    public Optional<AtmosphereChunkData.CellPosition> nextBurningAfter(ServerLevel level, ChunkPos chunk, AtmosphereChunkData.CellPosition cursor) {
        ReactionWorkQueue work = level == null ? null : reactionQueues.get(level);
        return work == null || chunk == null ? Optional.empty() : work.nextBurningAfter(chunk, cursor);
    }

    /** Clears transient work and policy so integrated/server restarts cannot inherit state. */
    public void onServerStopped() {
        clearAll();
        vacuumDimensions = Set.of();
        spaceDimensions = Set.of();
    }

    /** Empty means outside world bounds, inaccessible, solid, or in an unloaded chunk. */
    public Optional<GasMixture> sample(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return Optional.empty();
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
        if (level == null || pos == null || !inBounds(level, pos)) return Optional.empty();
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
        if (type == null || !Double.isFinite(moles) || moles <= 0.0 || !Double.isFinite(injectionTemperature) || injectionTemperature < 0.0) return false;
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
        if (!Double.isFinite(totalMoles) || totalMoles <= 0.0
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

    /**
     * Explicit server-thread reaction pass for one already claimed finite 1 m3 cell. Callers
     * must call this on the server thread outside graph-job callbacks; successful commits are
     * limited to one per cell per due atmosphere step across explicit and scheduled calls.
     * Off-cadence calls return empty without evaluating or mutating state. The returned reaction
     * species/energy/events are chemistry only; mixture is the final source AFTER face heat transfer.
     */
    public Optional<GasReactionEvaluator.Result> reactFiniteCell(ServerLevel level, BlockPos pos) {
        if (level == null || !isTickDue(level.getGameTime()) || pos == null || level.getServer() == null
                || !level.getServer().isSameThread()) return Optional.empty();
        return reactFiniteCellAtTime(level, pos, level.getGameTime(), true);
    }

    private Optional<GasReactionEvaluator.Result> reactFiniteCellAtTime(ServerLevel level, BlockPos pos, long gameTime,
                                                                        boolean allowWholeAirFallback) {
        if (level == null || pos == null) return Optional.empty();
        ReactionWorkQueue work = reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue());
        long dueStep = reactionDueStep(gameTime);
        if (work.reacted(dueStep, pos)) return Optional.empty();
        GasMixture before = reactionFiniteMixture(level, pos);
        if (before == null) return Optional.empty();
        HotspotState existing = work.hotspot(pos);
        ExplosionHotspotExposure.Decision exposure = work.exposure(pos, dueStep);
        boolean processedByLindaThisCycle = cycleLindaProcessed(level, pos);
        List<HotspotSpread.Neighbor> neighbors = new java.util.ArrayList<>(6);
        for (Direction face : Direction.values()) {
            BlockPos adjacent = pos.relative(face);
            if (!inBounds(level, adjacent) || loadedChunk(level, adjacent) == null
                    || work.reacted(dueStep, adjacent)
                    || work.hotspot(adjacent) != null || work.hasExposure(adjacent)
                    || !AtmosphereTopology.canExchange(level, pos, adjacent)) continue;
            GasMixture finite = reactionFiniteMixture(level, adjacent);
            if (finite != null) neighbors.add(new HotspotSpread.Neighbor(adjacent, face, finite, work.hotspot(adjacent)));
        }
        AtmosphereCycle cycle = cycles.get(level);
        boolean wholeAlreadyCommitted = cycle != null && cycle.wholeAir().containsKey(pos);
        GasReactionStep.WholeAirResult whole = wholeAlreadyCommitted ? cycle.wholeAir().get(pos) : null;
        boolean wholeEvaluatedNow = false;
        if (whole == null && allowWholeAirFallback) {
            whole = GasReactionStep.evaluateWholeAir(PrototypeRuntime.serverGasReactions(), before,
                    fireReactionResult(level, pos));
            wholeEvaluatedNow = true;
        }
        if (whole == null && existing == null && exposure == null) {
            // Work admitted after LINDA must wait until the next LINDA stage; HOTSPOTS never
            // executes an uncommitted whole-air pass as a side effect of scheduler ordering.
            work.offer(pos);
            return Optional.empty();
        }
        if (whole == null) whole = new GasReactionStep.WholeAirResult(before, List.of(), fireReactionResult(level, pos), 0);
        double priorFireExtent = cycle != null && cycle.wholeAir().containsKey(pos)
                ? cycle.wholeAir().get(pos).fireExtentMoles() : fireReactionResult(level, pos);
        GasMixture passGas = cycle != null && cycle.wholeAir().containsKey(pos) ? before : whole.mixture();
        HotspotState processState = existing;
        if (exposure != null) {
            processState = HotspotKernel.expose(passGas, existing, exposure.temperatureKelvin(),
                    exposure.exposedVolumeLiters(), true, processedByLindaThisCycle).orElse(existing);
        }
        if (!wholeAlreadyCommitted && hasFireEvent(whole.events())) {
            processState = HotspotKernel.expose(passGas, processState, passGas.temperatureKelvin(),
                    passGas.volumeCubicMeters() * 1000.0, false, processedByLindaThisCycle).orElse(processState);
        }
        HotspotState finalProcessState = processState;
        GasReactionStep.WholeAirResult priorWhole = whole;
        Map<BlockPos, HotspotState> spreadReceivers = new LinkedHashMap<>();
        Optional<CommittedHotspot> committed;
        try {
            committed = work.commitOnce(dueStep, pos,
                () -> commitHotspotCyclePass(pos, before, finalProcessState,
                        current -> hotspotStepResult(before, priorWhole, GasReactionStep.processHotspot(
                                PrototypeRuntime.serverGasReactions(), passGas, finalProcessState, priorFireExtent)),
                        neighbors, () -> {
                            if (!sameReactionSnapshot(before, reactionFiniteMixture(level, pos))) return false;
                            for (var neighbor : neighbors)
                                if (work.reacted(dueStep, neighbor.position())
                                        || work.hotspot(neighbor.position()) != null
                                        || work.hasExposure(neighbor.position())
                                        || !AtmosphereTopology.canExchange(level, pos, neighbor.position())
                                        || !sameReactionSnapshot(neighbor.mixture(),
                                                reactionFiniteMixture(level, neighbor.position()))) return false;
                            return true;
                        }, (cell, gas) -> writeHotspotRaw(level, cell, gas),
                        changed -> {
                            for (BlockPos cell : changed) {
                                LevelChunk chunk = loadedChunk(level, cell);
                                chunk.setUnsaved(true);
                                queue(level).noteChunkMutation(cell);
                                AtmosphereVisualServerHooks.noteChanged(level, cell);
                                prioritizeSeed(level, cell);
                                activateCellAndNeighbors(level, cell);
                            }
                        },
                        state -> {
                            boolean clearing = state.isEmpty();
                            boolean transientChanged = clearing
                                    && (work.hotspot(pos) != null || work.fireIntensity(pos) != 0);
                            work.setHotspot(pos, state);
                            if (clearing) work.setBurning(pos, 0);
                            if (!clearing || transientChanged)
                                AtmosphereVisualServerHooks.noteChanged(level, pos);
                        }, (cell, state) -> {
                            work.setHotspot(cell, Optional.of(state));
                            spreadReceivers.put(cell.immutable(), state);
                            work.setBurning(cell, 0);
                            work.deferReceiver(dueStep, cell);
                            if (cycle != null) cycle.deferNewHotspotReceiver(cell);
                            AtmosphereVisualServerHooks.noteChanged(level, cell);
                        }, cell -> cycle != null && cycle.lindaProcessedSources().contains(cell)));
        } catch (RuntimeException failure) {
            // A publication failure after the raw patch committed still stamps this cell.
            if (work.reacted(dueStep, pos)) {
                work.consumeExposure(pos, dueStep);
                resetExcitedGroupCooldownsAfterHotspotCommit(level, pos, finalProcessState);
            }
            throw failure;
        }
        if (committed.isPresent())
            resetExcitedGroupCooldownsAfterHotspotCommit(level, pos, finalProcessState);
        // The receiver only received face heat: its own event must commit on a later due step.
        if (committed.isPresent()) work.consumeExposure(pos, dueStep);
        if (committed.isPresent() && wholeEvaluatedNow)
            storeFireReactionResult(fireReactionResults.computeIfAbsent(level, ignored -> new java.util.HashMap<>()),
                    pos, whole.fireExtentMoles(), true);
        Optional<HotspotState> postHotspotState = committed.flatMap(CommittedHotspot::postState);
        boolean wholeAirIgnition = (wholeEvaluatedNow || wholeAlreadyCommitted) && hasFireEvent(whole.events());
        if (committed.isPresent() && existing == null && postHotspotState.isPresent()
                && (exposure != null || wholeAirIgnition)) {
            String origin = wholeAirIgnition ? "WHOLE_AIR_REACTION" : "DIRECT_EXPOSURE";
            String reactionId = whole.events().stream().filter(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                            || event.effect() == GasReactionData.EffectType.PLASMA_FIRE)
                    .map(event -> event.id().toString()).findFirst().orElse("unknown");
            HotspotState state = postHotspotState.orElseThrow();
            appendIgnitionDiagnostic(level, new IgnitionDiagnostic(pos.immutable(), cycle == null ? 0 : cycle.identity(),
                    gameTime, origin, before.temperatureKelvin(), whole.mixture().temperatureKelvin(),
                    before.moles(GasType.TRITIUM), before.moles(GasType.OXYGEN), state.temperatureKelvin(),
                    state.volumeLiters(), whole.fireExtentMoles(), reactionId));
        }
        if (committed.isPresent()) for (var receiver : spreadReceivers.entrySet()) {
            HotspotSpread.Neighbor input = neighbors.stream().filter(candidate -> candidate.position().equals(receiver.getKey())).findFirst().orElse(null);
            if (input == null) continue;
            GasMixture receiverGas = input.mixture();
            HotspotState receiverState = receiver.getValue();
            appendIgnitionDiagnostic(level, new IgnitionDiagnostic(receiver.getKey(), cycle == null ? 0 : cycle.identity(),
                    gameTime, "NEIGHBOR_SPREAD", receiverGas.temperatureKelvin(), receiverGas.temperatureKelvin(),
                    receiverGas.moles(GasType.TRITIUM), receiverGas.moles(GasType.OXYGEN), receiverState.temperatureKelvin(),
                    receiverState.volumeLiters(), 0.0, "none"));
        }
        if (committed.isPresent() && shouldExposeCommittedHotspot(finalProcessState, postHotspotState)) {
            HotspotState fireState = postHotspotState.orElseThrow();
            HotspotState burning = work.hotspot(pos);
            double displayedFraction = burning == null ? 0 : Math.min(1,
                    burning.volumeLiters() / (before.volumeCubicMeters() * 1000.0));
            work.setBurning(pos, burning == null ? 0 : (int) Math.max(1, Math.min(255, Math.ceil(displayedFraction * 255))));
            AtmosphereVisualServerHooks.noteChanged(level, pos);
            exposeCommittedFire(level, pos, fireState.temperatureKelvin());
        } else if (committed.isPresent() && work.fireIntensity(pos) != 0) {
            work.setBurning(pos, 0);
            AtmosphereVisualServerHooks.noteChanged(level, pos);
        }
        return committed.map(CommittedHotspot::result);
    }

    private void resetExcitedGroupCooldownsAfterHotspotCommit(ServerLevel level, BlockPos pos, HotspotState state) {
        if (!shouldResetExcitedGroupCooldownsAfterCommit(state, true)) return;
        ExcitedAtmosphereGroups excited = excitedGroups.get(level);
        if (excited != null) excited.resetCooldownsForCell(pos);
    }

    static boolean shouldResetExcitedGroupCooldownsAfterCommit(HotspotState state, boolean committed) {
        return committed && HotspotKernel.shouldResetExcitedGroupCooldowns(state);
    }

    private void exposeCommittedFire(ServerLevel level, BlockPos source, double temperatureKelvin) {
        if (HotspotEntityExposure.target(temperatureKelvin) <= 0f) return;
        Map<BlockPos, Integer> cursors = fireExposureCursors.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        // At most two local queries, each materializing at most the remaining mutation budget.
        AABB bounds = new AABB(source);
        HotspotEntityExposure.Selection<LivingEntity> selection = HotspotEntityExposure.select(
                cursors.getOrDefault(source, 0), MAX_ENTITIES_EXPOSED_PER_FIRE_EVENT,
                (predicate, cap) -> {
                    List<LivingEntity> found = new java.util.ArrayList<>(cap);
                    level.getEntities(EntityTypeTest.forClass(LivingEntity.class), bounds, predicate, found, cap);
                    return found;
                });
        if (selection.occupants().isEmpty()) { cursors.remove(source); return; }
        cursors.put(source.immutable(), selection.nextCursor());
        for (LivingEntity entity : selection.occupants()) {
            if (!FireStackSystem.supports(entity)) continue;
            float delta = HotspotEntityExposure.increase(temperatureKelvin, FireStackSystem.existing(entity).stacks());
            if (delta > 0f) FireStackSystem.flammable(entity, delta, null, 1f);
            FireStackSystem.ignite(entity, 1f); // Positive-stack gate remains inside FireStackSystem.
        }
    }

    /** Validated sparse write only: no dirty, visual, queue, or transient publication. */
    private boolean writeHotspotRaw(ServerLevel level, BlockPos pos, GasMixture state) {
        if (level == null || pos == null || state == null || !inBounds(level, pos)) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos) || !isFiniteClaimed(chunk, pos)) return false;
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (data == null) return false;
        data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state, ambient(level));
        return true; // A valid restoration may already equal its old sparse state.
    }

    private GasMixture reactionFiniteMixture(ServerLevel level, BlockPos pos) {
        if (!inBounds(level, pos)) return null;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return null;
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return reactionFiniteMixture(data, pos, ambient(level));
    }

    /** A persisted finite claim takes precedence over any stale exterior witness. */
    static GasMixture reactionFiniteMixture(AtmosphereChunkData data, BlockPos pos, GasMixture ambient) {
        if (data == null || !data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15)) return null;
        GasMixture stored = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        return stored == null ? ambient : stored;
    }

    private static boolean sameReactionSnapshot(GasMixture before, GasMixture current) {
        return current != null && same(before, current);
    }

    private void appendIgnitionDiagnostic(ServerLevel level, IgnitionDiagnostic event) {
        ignitionDiagnostics.computeIfAbsent(level, ignored -> new IgnitionDiagnosticHistory()).append(event);
    }

    static boolean shouldExposeCommittedHotspot(HotspotState inputState, Optional<HotspotState> postState) {
        return inputState != null && inputState.skippedFirstProcess()
                && postState != null && postState.isPresent();
    }

    private static boolean hasFireEvent(List<GasReactionEvaluator.Event> events) {
        return events.stream().anyMatch(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                || event.effect() == GasReactionData.EffectType.PLASMA_FIRE);
    }

    static Optional<IgnitionDiagnostic> lindaIgnitionDiagnostic(long cycleIdentity, long gameTime, BlockPos position,
                                                                 HotspotState existing, GasMixture sharedBefore,
                                                                 GasReactionStep.WholeAirResult whole,
                                                                 Optional<HotspotState> created) {
        if (existing != null || sharedBefore == null || whole == null || whole.fireExtentMoles() <= 0
                || !hasFireEvent(whole.events()) || created == null || created.isEmpty()) return Optional.empty();
        String reactionId = whole.events().stream().filter(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                        || event.effect() == GasReactionData.EffectType.PLASMA_FIRE)
                .map(event -> event.id().toString()).findFirst().orElse("unknown");
        HotspotState state = created.orElseThrow();
        return Optional.of(new IgnitionDiagnostic(position.immutable(), cycleIdentity, gameTime, "WHOLE_AIR_REACTION",
                sharedBefore.temperatureKelvin(), whole.mixture().temperatureKelvin(),
                sharedBefore.moles(GasType.TRITIUM), sharedBefore.moles(GasType.OXYGEN), state.temperatureKelvin(),
                state.volumeLiters(), whole.fireExtentMoles(), reactionId));
    }

    /** Pre-spread temperature is immutable commit metadata, never a callback inside the patch. */
    record CommittedHotspot(GasReactionEvaluator.Result result, double preSpreadTemperature,
                            Optional<HotspotState> postState) {}

    private static GasReactionStep.Result hotspotStepResult(GasMixture before,
            GasReactionStep.WholeAirResult whole, HotspotKernel.ProcessResult hotspot) {
        Map<GasType, Double> delta = new java.util.EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            double amount = hotspot.mixture().moles(type) - before.moles(type);
            if (amount != 0) delta.put(type, amount);
        }
        List<GasReactionEvaluator.Event> events = new java.util.ArrayList<>(whole.events());
        events.addAll(hotspot.events());
        double energy = events.stream().mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum();
        boolean fire = events.stream().anyMatch(event -> event.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                || event.effect() == GasReactionData.EffectType.PLASMA_FIRE);
        return new GasReactionStep.Result(hotspot.mixture(), delta, energy, events, hotspot.nextState(), fire,
                0, hotspot.spreadEligibleThisPass());
    }

    /** Cycle hot pass: writes only source gas; neighbor offers are metadata-only and publish after commit. */
    static Optional<CommittedHotspot> commitHotspotCyclePass(
            BlockPos source, GasMixture before, HotspotState existing,
            Function<GasMixture, GasReactionStep.Result> evaluator,
            List<HotspotSpread.Neighbor> neighbors, Supplier<Boolean> preflight,
            java.util.function.BiPredicate<BlockPos, GasMixture> writer,
            Consumer<List<BlockPos>> publishGas,
            Consumer<Optional<HotspotState>> publishSource,
            java.util.function.BiConsumer<BlockPos, HotspotState> publishReceiver) {
        return commitHotspotCyclePass(source, before, existing, evaluator, neighbors, preflight, writer,
                publishGas, publishSource, publishReceiver, ignored -> false);
    }

    static Optional<CommittedHotspot> commitHotspotCyclePass(
            BlockPos source, GasMixture before, HotspotState existing,
            Function<GasMixture, GasReactionStep.Result> evaluator,
            List<HotspotSpread.Neighbor> neighbors, Supplier<Boolean> preflight,
            java.util.function.BiPredicate<BlockPos, GasMixture> writer,
            Consumer<List<BlockPos>> publishGas,
            Consumer<Optional<HotspotState>> publishSource,
            java.util.function.BiConsumer<BlockPos, HotspotState> publishReceiver,
            Predicate<BlockPos> alreadyProcessedThisCycle) {
        if (source == null || before == null || evaluator == null || neighbors == null || preflight == null
                || writer == null || publishGas == null || publishSource == null || publishReceiver == null
                || alreadyProcessedThisCycle == null)
            return Optional.empty();
        boolean[] patched = {false};
        try {
            GasReactionStep.Result step = evaluator.apply(before);
            if (step == null || step.mixture() == null || step.nextState() == null
                    || !Double.isFinite(step.energyDeltaJoules())
                    || step.events().stream().anyMatch(event -> event == null || !Double.isFinite(event.extentMoles())
                            || event.extentMoles() <= 0 || !Double.isFinite(event.energyDeltaJoules())))
                return Optional.empty();
            Map<BlockPos, GasMixture> old = Map.of(source, before);
            Map<BlockPos, GasMixture> after = Map.of(source, step.mixture());
            Map<BlockPos, HotspotState> receivers = new LinkedHashMap<>();
            HotspotState committedSource = step.nextState().orElse(null);
            double spreadTemperature = committedSource == null ? Double.NEGATIVE_INFINITY
                    : committedSource.bypass() ? step.mixture().temperatureKelvin()
                    : committedSource.temperatureKelvin();
            if (step.spreadEligibleThisPass() && spreadTemperature > 423.15) {
                if (committedSource == null) return Optional.empty();
                double exposedTemperature = (committedSource.bypass() ? step.mixture().temperatureKelvin()
                        : committedSource.temperatureKelvin()) * 0.85;
                for (HotspotSpread.Neighbor neighbor : neighbors) {
                    GasMixture recipient = neighbor.mixture();
                    HotspotKernel.expose(recipient, null, exposedTemperature,
                            recipient.volumeCubicMeters() * 1000.0 * 0.25, false,
                            alreadyProcessedThisCycle.test(neighbor.position()))
                            .ifPresent(state -> receivers.put(neighbor.position(), state));
                }
            }
            if (!Double.isFinite(step.mixture().thermalEnergy()) || step.mixture().thermalEnergy() < 0
                    || !preflight.get()) return Optional.empty();
            CommittedHotspot committed = new CommittedHotspot(new GasReactionEvaluator.Result(
            step.mixture(), step.speciesDelta(), step.energyDeltaJoules(), step.events()),
                    step.mixture().temperatureKelvin(), step.nextState());
            if (!applyHotspotPatch(List.of(source), old, after, writer, changed -> {
                patched[0] = true;
                publishGas.accept(changed);
                publishSource.accept(step.nextState());
                receivers.forEach(publishReceiver);
            })) return Optional.empty();
            return Optional.of(committed);
        } catch (HotspotRollbackException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            if (patched[0]) throw new ReactionWorkQueue.CommittedFailure(failure);
            return Optional.empty();
        }
    }

    /** Hotspot-only raw transaction. The writer must be side-effect-free outside sparse gas storage;
     * it must permit restoring even an already-equal state. Failed attempts are restored too, since
     * a writer may mutate its cell before rejecting or throwing. Unrelated cells are never touched.
     * Runtime rollback failures are surfaced; process crashes and unrecoverable storage failures
     * cannot provide crash atomicity. */
    static boolean applyHotspotPatch(List<BlockPos> positions, Map<BlockPos, GasMixture> before,
                                     Map<BlockPos, GasMixture> after,
                                     java.util.function.BiPredicate<BlockPos, GasMixture> rawWriter,
                                     Consumer<List<BlockPos>> afterCommit) {
        List<BlockPos> changed = new java.util.ArrayList<>();
        // Validate the complete patch before performing even one raw write.
        for (BlockPos pos : positions)
            if (pos == null || before.get(pos) == null || after.get(pos) == null)
                throw new IllegalArgumentException("Hotspot patch states must cover every position");
        for (BlockPos pos : positions) {
            if (same(before.get(pos), after.get(pos))) continue;
            changed.add(pos); // Include the attempted cell in rollback even if the writer rejects it.
            boolean written;
            try {
                written = rawWriter.test(pos, after.get(pos));
            } catch (RuntimeException failure) {
                rollbackHotspotPatch(changed, before, rawWriter, failure);
                throw failure;
            }
            if (!written) {
                rollbackHotspotPatch(changed, before, rawWriter, null);
                return false;
            }
        }
        afterCommit.accept(List.copyOf(changed));
        return true;
    }

    private static void rollbackHotspotPatch(List<BlockPos> attempted, Map<BlockPos, GasMixture> before,
            java.util.function.BiPredicate<BlockPos, GasMixture> rawWriter, RuntimeException original) {
        RuntimeException rollbackFailure = null;
        for (int i = attempted.size() - 1; i >= 0; i--) {
            BlockPos pos = attempted.get(i);
            try {
                if (!rawWriter.test(pos, before.get(pos)))
                    throw new IllegalStateException("Hotspot rollback rejected at " + pos);
            } catch (RuntimeException failure) {
                if (rollbackFailure == null) rollbackFailure = new HotspotRollbackException(failure);
                else rollbackFailure.addSuppressed(failure);
            }
        }
        if (rollbackFailure != null) {
            if (original != null) rollbackFailure.addSuppressed(original);
            throw rollbackFailure;
        }
    }

    private static final class HotspotRollbackException extends IllegalStateException {
        private HotspotRollbackException(RuntimeException cause) { super("Hotspot rollback failed", cause); }
    }

    /**
     * Evaluates and preflights before one write on ordinary success. A writer that mutates state
     * and then throws is not rolled back; this is not a crash-atomic transaction.
     */
    static Optional<GasReactionEvaluator.Result> commitReaction(
            GasMixture before, Function<GasMixture, GasReactionEvaluator.Result> evaluator,
            Supplier<Boolean> preflight, Predicate<GasMixture> writer) {
        if (before == null || evaluator == null || preflight == null || writer == null) return Optional.empty();
        try {
            GasReactionEvaluator.Result result = evaluator.apply(before);
            if (result == null || result.mixture() == null || result.events() == null
                    || result.speciesDelta() == null || !Double.isFinite(result.energyDeltaJoules())
                    || result.speciesDelta().values().stream().anyMatch(v -> v == null || !Double.isFinite(v))
                    || result.events().stream().anyMatch(event -> event == null
                            || !Double.isFinite(event.extentMoles()) || event.extentMoles() <= 0
                            || !Double.isFinite(event.energyDeltaJoules())
                            || event.speciesDelta().values().stream().anyMatch(v -> v == null || !Double.isFinite(v)))
                    || same(before, result.mixture()) || !preflight.get()) return Optional.empty();
            return writer.test(result.mixture()) ? Optional.of(result) : Optional.empty();
        } catch (RuntimeException invalid) {
            // The evaluator may reject overflow; a failing writer must not publish a result.
            return Optional.empty();
        }
    }

    /**
     * Atomically exchanges a lung's requested inhale and its actual former gas with one cell.
     * Unknown ownership, unloaded/solid cells, and disabled atmosphere are strict failures.
     * A proven exterior supplies ambient gas without mutating any finite inventory.
     */
    public Optional<BreathExchange> exchangeBreath(ServerLevel level, BlockPos pos,
                                                    double requestedMoles, GasMixture exhaled) {
        return exchangeBreath(level, pos, requestedMoles, exhaled, inhaled -> true);
    }

    /**
     * Validates the proposed inhaled gas before any finite-cell write. The validator must be
     * side-effect-free and must not mutate atmosphere; it is invoked exactly once for a valid
     * exchange, including a proven exterior or a physical no-op. A rejection leaves the room
     * untouched. This does not make lung and world persistence atomic across a crash.
     */
    public Optional<BreathExchange> exchangeBreath(ServerLevel level, BlockPos pos,
                                                    double requestedMoles, GasMixture exhaled,
                                                    java.util.function.Predicate<GasMixture> validateInhaled) {
        if (level == null || pos == null || exhaled == null
                  || validateInhaled == null || !Double.isFinite(requestedMoles)
                  || requestedMoles < 0.0 || !inBounds(level, pos))
            return Optional.empty();
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return Optional.empty();

        if (!isFiniteClaimed(chunk, pos)) {
            if (!isExterior(chunk, pos) && !isCachedExterior(level, pos)) {
                ownership(level, chunk, pos);
                return Optional.empty();
            }
            GasMixture ambient = ambient(level);
            Optional<GasMixture> portion = ambientPortion(ambient, requestedMoles);
            if (portion.isEmpty() || !accept(validateInhaled, portion.get())) return Optional.empty();
            return Optional.of(new BreathExchange(portion.get(), ambient));
        }

        GasMixture before = sample(level, pos).orElse(null);
        if (before == null) return Optional.empty();
        Optional<BreathExchange> candidate = BreathExchange.calculate(before, requestedMoles, exhaled);
        if (candidate.isEmpty()) return Optional.empty();
        BreathExchange result = candidate.get();
        if (!accept(validateInhaled, result.inhaled())) return Optional.empty();
        // A successful physical no-op must not materialize an ambient override.
        if (same(before, result.roomAfter())) return candidate;
        return write(level, pos, result.roomAfter()) ? candidate : Optional.empty();
    }

    private static boolean accept(java.util.function.Predicate<GasMixture> validator, GasMixture inhaled) {
        try { return validator.test(inhaled); }
        catch (RuntimeException invalid) { return false; }
    }

    private static Optional<GasMixture> ambientPortion(GasMixture ambient, double requestedMoles) {
        double total = ambient.totalMoles();
        if (total == 0.0) return Optional.of(new GasMixture(Map.of(), ambient.temperatureKelvin()));
        try {
            return Optional.of(ambient.splitRatio(Math.min(1.0, requestedMoles / total)).removed());
        } catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    /** Removes up to the requested amount proportionally across all species. */
    public double removeGasUpTo(ServerLevel level, BlockPos pos, double maxMoles) {
        if (!Double.isFinite(maxMoles) || maxMoles <= 0.0) return 0.0;
        if (isLoadedPassableExterior(level, pos)) return 0.0;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return 0.0;
        GasMixture before = current.get();
        double total = before.totalMoles();
        if (total <= 0.0) return 0.0;
        double ratio = Math.min(1.0, maxMoles / total);
        GasMixture.Removal split = before.splitRatio(ratio);
        return write(level, pos, split.remaining()) ? split.removed().totalMoles() : 0.0;
    }

    public boolean addEnergy(ServerLevel level, BlockPos pos, double joules) {
        if (!Double.isFinite(joules) || joules == 0.0) return false;
        if (isLoadedPassableExterior(level, pos)) return false;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return false;
        try { return write(level, pos, current.get().withEnergyDelta(joules)); }
        catch (IllegalArgumentException | IllegalStateException exception) { return false; }
    }

    /** Cheap gas/topology activation. This path never enumerates persisted chunk overrides. */
    public void activateCellAndNeighbors(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !inBounds(level, pos) || loadedChunk(level, pos) == null) return;
        quenchSolidHotspot(level, pos);
        enqueueLocalStencil(queue(level), pos);
        enqueueReactionStencil(level, pos);
    }

    /** A loaded solid is proof that a live finite hotspot is no longer physically visible. */
    private void quenchSolidHotspot(ServerLevel level, BlockPos pos) {
        if (loadedChunk(level, pos) == null || AtmosphereTopology.isPassable(level, pos)) return;
        ReactionWorkQueue work = reactionQueues.get(level);
        if (work != null && work.quenchIfPresent(pos)) AtmosphereVisualServerHooks.noteChanged(level, pos);
    }

    /** Re-samples immediately before committing heater-only positive energy. */
    public boolean addHeaterEnergy(ServerLevel level, BlockPos pos, double requestedJoules) {
        if (!Double.isFinite(requestedJoules) || requestedJoules <= 0.0) return false;
        if (isLoadedPassableExterior(level, pos)) return false;
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return false;
        double offer = AtmosphereDeviceRules.heaterOffer(current.get(), requestedJoules);
        if (!Double.isFinite(offer) || offer <= 0.0) return false;
        try { return write(level, pos, current.get().withEnergyDelta(offer)); }
        catch (IllegalArgumentException | IllegalStateException exception) { return false; }
    }

    private void enqueueReactionStencil(ServerLevel level, BlockPos pos) {
        ReactionWorkQueue reactions = reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue());
        reactions.offer(pos);
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (inBounds(level, adjacent) && loadedChunk(level, adjacent) != null) reactions.offer(adjacent);
        }
    }

    /** Block topology changes also reactivate saved overrides in the affected vertical column. */
    public void topologyChanged(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !inBounds(level, pos)) return;
        AtmosphereCycle cycle = cycles.get(level);
        if (cycle != null) cycle.invalidateTopology(pos);
        excitedGroups.remove(level);
        EqualizationJob job = equalizationJobs.get(level);
        if (job != null && equalizationTouches(job, pos)) cancelEqualization(level);
        Map<BlockPos, EqualizationJob> parked = parkedEqualizationJobs.get(level);
        if (parked != null) parked.values().removeIf(candidate -> equalizationTouches(candidate, pos));
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
        if (level == null || pos == null || !inBounds(level, pos)) return;
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
        if (shouldInvalidateClosedOpenable(open, changed,
                work != null && work.hasExteriorWitnessCell(pos))) changed = true;
        if (changed) topologyChanged(level, pos);
        return changed;
    }

    static boolean shouldInvalidateClosedOpenable(boolean open, boolean stateChanged, boolean exactWitnessCell) {
        return stateChanged || (!open && exactWitnessCell);
    }

    /** Compatibility entry point for ordinary local invalidation; intentionally cheap. */
    public void invalidate(ServerLevel level, BlockPos pos) { activateCellAndNeighbors(level, pos); }

    public void tick(ServerLevel level, long gameTime) {
        if (level == null) return;
        AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.computeIfAbsent(level,
                ignored -> new AtmosphereRuntimeTelemetry(gameTime, System.nanoTime()));
        telemetry.callback();
        long totalStart = System.nanoTime();
        BooleanSupplier deadlineReached = () -> System.nanoTime() - totalStart >= 3_000_000L;
        AtmosphereCycle cycle = cycles.computeIfAbsent(level, ignored -> new AtmosphereCycle());
        if (!cycle.beginWhenDue() || cycle.stage() == null) return;
        telemetry.observeStage(cycle.stage(), gameTime);
        telemetry.admitted(cycle.stage());
        WorkQueue queue = queue(level);
        Set<BlockPos> boundaryCells = cycle.boundaryCells();
        Set<BlockPos> handled = cycle.monstermosHandled();
        spaceBoundaryCellsThisCycle.put(level, boundaryCells);
        long owned = 0, solved = 0, linda = 0, reactions = 0;
        int retrySteps = 0;
        AtmosphereCycle.Stage activeStage = cycle.stage();
        long stageStart = System.nanoTime();
        switch (activeStage) {
                case REVALIDATE -> {
                    advanceOwnership(level, deadlineReached);
                    retrySteps = retryDirtyChunks(level, queue, MAX_DIRTY_CURSOR_STEPS_PER_TICK, deadlineReached);
                }
                case MONSTERMOS -> {
                    if (!cycle.monstermosSeedsStarted()) {
                        PrioritySeedQueue priority = prioritySeeds.get(level);
                        Set<BlockPos> admitted = orderedMonstermosSeeds(
                                pressureContinuationSeeds.getOrDefault(level, Set.of()), queue.snapshotPositions(),
                                priority == null ? Set.of() : priority.snapshotPositions());
                        cycle.startMonstermos(admitted);
                    }
                    BoundedBudgetDrain.drain(deadlineReached, () -> {
                        advanceMonstermosCycle(level, queue, cycle, boundaryCells, handled, deadlineReached);
                        EqualizationJob pending = equalizationJobs.get(level);
                        cycle.monstermosPending(pending != null && !pending.waitingForChunk);
                        // Keep taking bounded BFS/solve units and subsequent seeds while real work remains.
                        return (pending != null && !pending.waitingForChunk) || !cycle.monstermosSeeds().isEmpty();
                    });
                }
                case LINDA -> {
                    if (!cycle.lindaStarted()) cycle.startLinda(queue.snapshotPositions());
                    while (System.nanoTime() - totalStart < 3_000_000L) {
                        if (cycle.lindaSources().isEmpty()) break;
                        int remainingBefore = cycle.lindaSources().size();
                        processLindaTick(level, queue, retrySteps, gameTime,
                                cycle, cycle.lindaSources());
                        if (cycle.lindaSources().size() == remainingBefore) break;
                    }
                }
                case GROUPS -> {
                    advanceExcitedGroups(level, queue, cycle.identity(),
                            excitedGroups.computeIfAbsent(level, ignored -> new ExcitedAtmosphereGroups()), deadlineReached);
                    cycle.groupsPending(excitedGroups.get(level) != null
                            && excitedGroups.get(level).hasUnprocessedCycleWork());
                }
                case HIGH_PRESSURE, DELTA_PRESSURE, PIPE_NET, ATMOS_DEVICES -> { /* not implemented by this adapter */ }
                case HOTSPOTS -> cycle.reactionsPending(processReactions(level, gameTime, cycle, deadlineReached));
        }
        long stageEnd = System.nanoTime();
        if (activeStage == AtmosphereCycle.Stage.REVALIDATE) owned += stageEnd - stageStart;
        else if (activeStage == AtmosphereCycle.Stage.MONSTERMOS) solved += stageEnd - stageStart;
        else if (activeStage == AtmosphereCycle.Stage.LINDA || activeStage == AtmosphereCycle.Stage.GROUPS)
            linda += stageEnd - stageStart;
        else if (activeStage == AtmosphereCycle.Stage.HOTSPOTS) reactions += stageEnd - stageStart;
        cycle.advance();
        telemetry.observeStage(cycle.stage(), gameTime);
        long done = System.nanoTime();
        WorkQueue q = queues.get(level);
        OwnershipWork ow = ownershipWork.get(level);
        PatchDiagnostic patch = patchDiagnostics.getOrDefault(level, new PatchDiagnostic("none", "none", "no patch attempted", 0, 0, 0, 0, 0));
        diagnosticTimings.put(level, new DiagnosticTiming(gameTime, owned/1e6, solved/1e6, linda/1e6, reactions/1e6, (done-totalStart)/1e6,
                patch.attempted(), patch.committed(), patch.status(), patch.size(), patch.expansions(), patch.calls(), patch.exportMoles(), patch.exportEnergy(),
                q == null ? 0 : q.pending.size() + q.urgent.size(), q == null ? 0 : q.dirtyChunks.size(), ow == null ? 0 : ow.pendingCount()));
    }

    private boolean processReactions(ServerLevel level, long gameTime, AtmosphereCycle cycle,
        BooleanSupplier deadlineReached) {
        ReactionWorkQueue work = reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue());
        if (!cycle.reactionsStarted()) cycle.startReactions(work.snapshotPositions());
        LoadedReactionChunks tracked = loadedReactionChunks.get(level);
        int trackedWork = 0;
        if (tracked != null && tracked.hasPending()) {
            trackedWork = tracked.drain(MAX_REACTION_LOAD_STEPS_PER_TICK, deadlineReached, cp -> {
                LevelChunk chunk = loadedChunk(level, new BlockPos(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ()));
                if (chunk == null) return;
                AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
                if (data != null && data.size() > 0) work.startLoad(cp);
            });
        }
        long dueStep = reactionDueStep(gameTime);
        work.expireExposures(dueStep);
        int loadWork = 0;
        if (trackedWork == 0 || !deadlineReached.getAsBoolean())
            loadWork = work.drainLoads(MAX_REACTION_LOAD_STEPS_PER_TICK, MAX_REACTION_LOAD_STEPS_PER_TICK,
                    deadlineReached, cp -> {
            LevelChunk chunk = loadedChunk(level, new BlockPos(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ()));
            if (chunk == null) { work.removeChunk(cp); return false; }
            AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data == null) return false;
            var next = data.nextAfter(work.cursor(cp));
            if (next.isEmpty()) return false;
            var cell = next.get().getKey();
            BlockPos pos = new BlockPos(cp.getMinBlockX() + cell.x(), cell.y(), cp.getMinBlockZ() + cell.z());
            if (data.isFiniteClaimed(cell.x(), cell.y(), cell.z())
                    && potentiallyReactive(next.get().getValue(), PrototypeRuntime.serverGasReactions().asMap().values()))
                work.offer(pos);
            work.continueLoad(cp, cell);
            return true;
        });
        if (cycle.reactionSources().isEmpty()) {
            if ((tracked != null && tracked.hasPending()) || work.hasPendingLoads()) {
                cycle.reactionsPending(true);
                return true;
            }
            cycle.finishReactions(work::offer);
            return false;
        }
        int inspectedCount = 0;
        boolean didPriorWork = trackedWork > 0 || loadWork > 0;
        for (; inspectedCount < MAX_REACTION_INSPECTIONS_PER_TICK && !cycle.reactionSources().isEmpty()
                && (inspectedCount == 0 && !didPriorWork || !deadlineReached.getAsBoolean()); inspectedCount++) {
            BlockPos pos = work.poll(cycle.reactionSources());
            if (pos == null) {
                if ((tracked != null && tracked.hasPending()) || work.hasPendingLoads()) {
                    cycle.reactionsPending(true);
                    return true;
                }
                cycle.finishReactions(work::offer);
                return false;
            }
            cycle.removeReactionSource(pos);
            if (!cycle.reactionVisited().add(pos)) { cycle.deferReaction(pos); continue; }
            if (work.exposureIsFuture(pos, dueStep)) { cycle.deferReaction(pos); continue; }
            GasMixture mixture = reactionFiniteMixture(level, pos);
            boolean hadFire = work.fireIntensity(pos) > 0;
            if (mixture == null && loadedChunk(level, pos) != null
                    && !AtmosphereTopology.isPassable(level, pos)) {
                if (work.quenchIfPresent(pos)) AtmosphereVisualServerHooks.noteChanged(level, pos);
                continue;
            }
            if (!schedulerReactionEligible(work, pos, mixture,
                    PrototypeRuntime.serverGasReactions().asMap().values())) {
                if (hadFire) AtmosphereVisualServerHooks.noteChanged(level, pos);
                if (work.hasExposure(pos)) cycle.deferReaction(pos); // Unavailable finite snapshot: retry until TTL/unload.
                continue;
            }
            if (work.reacted(dueStep, pos) || work.reactionCapacityReached(dueStep)) { cycle.deferReaction(pos); continue; }
            boolean committed = reactFiniteCellAtTime(level, pos, gameTime, false).isPresent();
            if (work.hasExposure(pos)) cycle.deferReaction(pos); // Failed/no-op preflight must not lose a blast admission.
            if (committed) {
                GasMixture after = reactionFiniteMixture(level, pos);
                if (after != null && (work.hotspot(pos) != null
                        || potentiallyReactive(after, PrototypeRuntime.serverGasReactions().asMap().values())))
                    cycle.deferReaction(pos);
            }
        }
        cycle.reactionsPendingFromSources();
        boolean pending = !cycle.reactionSources().isEmpty()
                || (tracked != null && tracked.hasPending()) || work.hasPendingLoads();
        if (!pending) cycle.finishReactions(work::offer);
        return pending;
    }

    /** Called after the loaded finite-cell lookup; never quench on an unavailable snapshot. */
    static boolean schedulerReactionEligible(ReactionWorkQueue work, BlockPos pos, GasMixture mixture,
            java.util.Collection<GasReactionData> reactions) {
        if (mixture == null) return false;
        if (work.hasExposure(pos)) return true;
        if (work.hotspot(pos) != null) return true;
        if (potentiallyReactive(mixture, reactions)) return true;
        work.quenchIfPresent(pos);
        return false;
    }

    static long reactionDueStep(long gameTime) { return Math.floorDiv(gameTime, TICK_CADENCE); }

    /** Conservative catalog gate; no temperature-only hot filter may suppress cold reactions. */
    static boolean potentiallyReactive(GasMixture gas, java.util.Collection<GasReactionData> reactions) {
        if (gas == null) return false;
        for (GasReactionData reaction : reactions) {
            double temperature = gas.temperatureKelvin();
            if (temperature < reaction.minimumTemperature() || temperature > reaction.maximumTemperature()
                    || gas.thermalEnergy() < reaction.minimumEnergy()) continue;
            boolean eligible = true;
            for (var requirement : reaction.minimumRequirements().entrySet())
                if (gas.moles(requirement.getKey()) < requirement.getValue()) { eligible = false; break; }
            if (!eligible) continue;
            for (GasReactionData.Effect effect : reaction.effects()) {
                if (switch (effect.type()) {
                    case FREZON_PRODUCTION -> temperature > 0 && temperature <= 73.15
                            && gas.moles(GasType.TRITIUM) > 0 && gas.moles(GasType.OXYGEN) > 0
                            && gas.moles(GasType.NITROGEN) > 0;
                    case FREZON_COOLANT -> temperature > 23.15 && gas.moles(GasType.FREZON) > 0;
                    case PLASMA_FIRE -> temperature > 373.15 && gas.moles(GasType.PLASMA) > 0 && gas.moles(GasType.OXYGEN) > 0;
                    case TRITIUM_FIRE -> gas.moles(GasType.TRITIUM) > 0 && gas.moles(GasType.OXYGEN) > 0;
                    case AMMONIA_OXYGEN -> gas.moles(GasType.AMMONIA) > 0 && gas.moles(GasType.OXYGEN) > 0;
                    case N2O_DECOMPOSITION -> gas.moles(GasType.NITROUS_OXIDE) > 0;
                }) return true;
            }
        }
        return false;
    }

    static boolean isTickDue(long gameTime) {
        return gameTime % TICK_CADENCE == 0;
    }

    /** Records only coordinates because ChunkEvent.Load can precede promotion to FULL. */
    void deferChunkLoad(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null) return;
        deferredChunkLoads.computeIfAbsent(level, ignored -> new DeferredChunkQueue()).offer(chunk);
    }

    /** Resolves a bounded number of recorded chunks without forcing any chunk loads. */
    void processDeferredChunkLoads(ServerLevel level) {
        if (level == null) return;
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
        if (level == null || chunk == null) return;
        AtmosphereCycle cycle = cycles.get(level);
        if (cycle != null) cycle.invalidateChunk(chunk.getPos());
        loadedReactionChunks.computeIfAbsent(level, ignored -> new LoadedReactionChunks()).add(chunk.getPos());
        EqualizationJob waiting = equalizationJobs.get(level);
        BlockPos retryCandidate = waiting == null || !waiting.waitingForChunk ? null : waiting.discovery.retryCandidate();
        if (retryCandidate != null && new ChunkPos(retryCandidate).equals(chunk.getPos())) waiting.waitingForChunk = false;
        else if (waiting != null && equalizationTouchesChunk(waiting, chunk.getPos())) cancelEqualization(level);
        Map<BlockPos, EqualizationJob> parked = parkedEqualizationJobs.get(level);
        if (parked != null) for (EqualizationJob parkedJob : parked.values()) {
            BlockPos retry = parkedJob.discovery.retryCandidate();
            if (retry != null && parkedJob.waitingForChunk && new ChunkPos(retry).equals(chunk.getPos())) {
                parkedJob.waitingForChunk = false;
                prioritizeSeed(level, parkedJob.seed);
            }
        }
        OwnershipWork ownership = ownershipWork.get(level);
        if (ownership != null) {
            ownership.retryForChunkLifecycle(chunk.getPos());
            ownership.wakeUnknown(chunk.getPos());
        }
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        ChunkPos cp = chunk.getPos();
        if (data != null && data.size() > 0)
            reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue()).startLoad(cp);
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
        if (level == null || chunk == null) return;
        AtmosphereCycle cycle = cycles.get(level);
        if (cycle != null) cycle.invalidateChunk(chunk);
        LoadedReactionChunks tracked = loadedReactionChunks.get(level);
        if (tracked != null) tracked.remove(chunk);
        Map<BlockPos, Integer> cursors = fireExposureCursors.get(level);
        if (cursors != null) {
            cursors.keySet().removeIf(pos -> new ChunkPos(pos).equals(chunk));
            if (cursors.isEmpty()) fireExposureCursors.remove(level);
        }
        Map<BlockPos, Double> fireResults = fireReactionResults.get(level);
        if (fireResults != null) {
            removeFireReactionResultsInChunk(fireResults, chunk);
            if (fireResults.isEmpty()) fireReactionResults.remove(level);
        }
        Set<BlockPos> continuations = pressureContinuationSeeds.get(level);
        if (continuations != null) {
            removePressureContinuationsInChunk(continuations, chunk);
            if (continuations.isEmpty()) pressureContinuationSeeds.remove(level);
        }
        excitedGroups.remove(level);
        EqualizationJob job = equalizationJobs.get(level);
        if (job != null && equalizationTouchesChunk(job, chunk)) cancelEqualization(level);
        Map<BlockPos, EqualizationJob> parkedJobs = parkedEqualizationJobs.get(level);
        if (parkedJobs != null) parkedJobs.values().removeIf(parked -> equalizationTouchesChunk(parked, chunk));
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
        ReactionWorkQueue reactions = reactionQueues.get(level);
        if (reactions != null) reactions.removeChunk(chunk);
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

    /** Called only after a successful server catalog commit; discovery starts on subsequent due ticks. */
    public void onReactionCatalogReload(Iterable<ServerLevel> levels) {
        for (ServerLevel level : levels) {
            LoadedReactionChunks tracked = loadedReactionChunks.get(level);
            if (tracked != null) tracked.restart();
        }
    }

    /** Ordered coordinates permit bounded traversal even when chunks load or unload mid-scan. */
    static final class LoadedReactionChunks {
        private static final Comparator<ChunkPos> ORDER = Comparator.comparingInt((ChunkPos pos) -> pos.x)
                .thenComparingInt(pos -> pos.z);
        private final TreeSet<ChunkPos> chunks = new TreeSet<>(ORDER);
        private ChunkPos cursor;
        private boolean pending;

        void add(ChunkPos pos) { chunks.add(pos); }
        void remove(ChunkPos pos) { chunks.remove(pos); }
        void restart() { cursor = null; pending = !chunks.isEmpty(); }
        boolean hasPending() { return pending; }
        int drain(int budget, Consumer<ChunkPos> inspect) {
            return drain(budget, () -> false, inspect);
        }
        int drain(int budget, BooleanSupplier deadlineReached, Consumer<ChunkPos> inspect) {
            int count = 0;
            while (pending && count < budget && (count == 0 || !deadlineReached.getAsBoolean())) {
                ChunkPos next = cursor == null ? (chunks.isEmpty() ? null : chunks.first()) : chunks.higher(cursor);
                if (next == null) { pending = false; break; }
                cursor = next;
                inspect.accept(next);
                count++;
            }
            if (pending && (chunks.isEmpty() || chunks.higher(cursor) == null)) pending = false;
            return count;
        }
    }

    public long completedCycles(ServerLevel level) { AtmosphereCycle cycle = cycles.get(level); return cycle == null ? 0 : cycle.completed(); }
    public long atmosphereCycleIdentity(ServerLevel level) { AtmosphereCycle cycle = cycles.get(level); return cycle == null ? 0 : cycle.identity(); }
    public String atmosphereStage(ServerLevel level) {
        AtmosphereCycle cycle = cycles.get(level);
        return cycle == null || cycle.stage() == null ? "idle" : cycle.stage().name().toLowerCase(java.util.Locale.ROOT);
    }
    public void clear(ServerLevel level) { diagnosticTimings.remove(level); runtimeTelemetry.remove(level); patchDiagnostics.remove(level); pressureDiagnostics.remove(level); pressureContinuationSeeds.remove(level); queues.remove(level); reactionQueues.remove(level); loadedReactionChunks.remove(level); fireExposureCursors.remove(level); fireReactionResults.remove(level); ignitionDiagnostics.remove(level); deferredChunkLoads.remove(level); boundaryLedgers.remove(level); hotspotFaceHeat.remove(level); spaceBoundaryCellsThisCycle.remove(level); equalizationJobs.remove(level); parkedEqualizationJobs.remove(level); cycles.remove(level); prioritySeeds.remove(level); prioritySeedStreaks.remove(level); ownershipWork.remove(level); openableStates.remove(level); excitedGroups.remove(level); }
    public void clearAll() { diagnosticTimings.clear(); runtimeTelemetry.clear(); patchDiagnostics.clear(); pressureDiagnostics.clear(); pressureContinuationSeeds.clear(); queues.clear(); reactionQueues.clear(); loadedReactionChunks.clear(); fireExposureCursors.clear(); fireReactionResults.clear(); ignitionDiagnostics.clear(); deferredChunkLoads.clear(); boundaryLedgers.clear(); hotspotFaceHeat.clear(); spaceBoundaryCellsThisCycle.clear(); equalizationJobs.clear(); parkedEqualizationJobs.clear(); cycles.clear(); prioritySeeds.clear(); prioritySeedStreaks.clear(); ownershipWork.clear(); openableStates.clear(); excitedGroups.clear(); }

    private void advanceMonstermosCycle(ServerLevel level, WorkQueue queue, AtmosphereCycle cycle,
                                        Set<BlockPos> boundaryCells, Set<BlockPos> handled,
                                        BooleanSupplier deadlineReached) {
        EqualizationJob active = equalizationJobs.get(level);
        if (active != null && active.waitingForChunk) {
            parkedEqualizationJobs.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(active.seed, active);
            prioritizeSeed(level, active.seed);
            equalizationJobs.remove(level);
            active = null;
        }
        boolean seedCheckpointDone = false;
        while (active == null && !cycle.monstermosSeeds().isEmpty()
                && (!seedCheckpointDone || !deadlineReached.getAsBoolean())) {
            BlockPos seed = cycle.monstermosSeeds().removeFirst();
            seedCheckpointDone = true;
            if (skipMonstermosSeed(cycle.monstermosProcessed(), seed, false)) continue;
            if (isLoadedPassableExterior(level, seed)) {
                removeTerminalPressureContinuation(level, seed);
                continue;
            }
            PrioritySeedQueue priority = prioritySeeds.get(level);
            if (priority != null) priority.remove(seed);
            Map<BlockPos, EqualizationJob> parked = parkedEqualizationJobs.get(level);
            active = parked == null ? null : parked.remove(seed);
            if (parked != null && parked.isEmpty()) parkedEqualizationJobs.remove(level);
            if (active != null) {
                BlockPos retry = active.discovery.retryCandidate();
                if (retry != null && loadedChunk(level, retry) == null) {
                    parkedEqualizationJobs.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(seed, active);
                    active = null;
                    continue;
                }
                active.waitingForChunk = false;
            } else {
                boolean continuation = pressureContinuationSeeds.getOrDefault(level, Set.of()).contains(seed);
                SeedPreflight preflight = inspectMonstermosSeed(level, seed, continuation);
                if (preflight == SeedPreflight.INACTIVE) {
                    removeTerminalPressureContinuation(level, seed);
                    cycle.monstermosProcessed().add(seed.immutable());
                    continue;
                }
                if (preflight == SeedPreflight.UNKNOWN) {
                    prioritizeSeed(level, seed);
                    prioritySeeds.get(level).defer(seed);
                    continue;
                }
                active = new EqualizationJob(seed);
            }
        }
        if (active != null) {
            equalizationJobs.put(level, active);
            if (!seedCheckpointDone || !deadlineReached.getAsBoolean())
                advanceEqualization(level, queue, handled, boundaryCells);
        }
    }

    enum SeedPreflight { ACTIVE, INACTIVE, UNKNOWN }

    static SeedPreflight preflightMonstermosSource(boolean loaded, boolean passable, boolean finite,
                                                   Runnable requestOwnership) {
        if (!loaded) return SeedPreflight.UNKNOWN;
        if (!passable) return SeedPreflight.INACTIVE;
        if (!finite) {
            requestOwnership.run();
            return SeedPreflight.UNKNOWN;
        }
        return SeedPreflight.ACTIVE;
    }

    private SeedPreflight inspectMonstermosSeed(ServerLevel level, BlockPos seed, boolean continuation) {
        LevelChunk sourceChunk = loadedChunk(level, seed);
        if (sourceChunk == null) return SeedPreflight.UNKNOWN;
        boolean passable = AtmosphereTopology.isPassable(level, seed);
        SeedPreflight sourcePreflight = preflightMonstermosSource(true, passable,
                isFiniteClaimed(sourceChunk, seed), () -> ownership(level, sourceChunk, seed));
        if (sourcePreflight != SeedPreflight.ACTIVE) return sourcePreflight;
        GasMixture source = finiteMixture(level, seed);
        if (source == null) return SeedPreflight.UNKNOWN;
        boolean unknownNeighbor = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = seed.relative(direction);
            if (!inBounds(level, neighbor)) continue;
            LevelChunk chunk = loadedChunk(level, neighbor);
            if (chunk == null) { unknownNeighbor = true; continue; }
            if (!AtmosphereTopology.canExchange(level, seed, neighbor)) continue;
            GasMixture neighborGas = null;
            if (isFiniteClaimed(chunk, neighbor)) neighborGas = sample(level, neighbor).orElse(null);
            else if (isLoadedPassableExterior(level, neighbor)) neighborGas = ambient(level);
            else {
                ownership(level, chunk, neighbor);
                unknownNeighbor = true;
            }
            if (neighborGas == null) continue;
            boolean spaceExterior = isSpace(level.dimension()) && isLoadedPassableExterior(level, neighbor);
            boolean pressureDriven = !spaceExterior && monstermosSeedNeedsPressureEqualization(source, neighborGas);
            boolean spaceDriven = spaceExterior
                    && monstermosSeedNeedsEqualization(source.totalMoles(), neighborGas.totalMoles());
            if (pressureDriven || spaceDriven)
                return SeedPreflight.ACTIVE;
        }
        return normalPressureContinuationPreflight(false, continuation, unknownNeighbor);
    }

    static SeedPreflight normalPressureContinuationPreflight(boolean pressureGradient, boolean continuation,
                                                              boolean unknownNeighbor) {
        if (pressureGradient) return SeedPreflight.ACTIVE;
        if (unknownNeighbor) return SeedPreflight.UNKNOWN;
        return continuation ? SeedPreflight.ACTIVE : SeedPreflight.INACTIVE;
    }

    static boolean skipMonstermosSeed(Set<BlockPos> processed, BlockPos seed, boolean exterior) {
        return processed.contains(seed) || exterior;
    }

    static void removeTerminalPressureContinuation(Set<BlockPos> continuations, BlockPos seed) {
        continuations.remove(seed);
    }

    static void removePressureContinuationsInChunk(Set<BlockPos> continuations, ChunkPos chunk) {
        continuations.removeIf(pos -> new ChunkPos(pos).equals(chunk));
    }

    private void removeTerminalPressureContinuation(ServerLevel level, BlockPos seed) {
        Set<BlockPos> continuations = pressureContinuationSeeds.get(level);
        if (continuations == null) return;
        removeTerminalPressureContinuation(continuations, seed);
        if (continuations.isEmpty()) pressureContinuationSeeds.remove(level);
    }

    static void updatePressureContinuationSeeds(Set<BlockPos> continuations, BlockPos seed, boolean incomplete,
                                                boolean spacePass) {
        if (incomplete) {
            if (!spacePass) continuations.add(seed.immutable());
        }
        else {
            continuations.remove(seed);
        }
    }

    static Set<BlockPos> orderedMonstermosSeeds(Set<BlockPos> continuations, java.util.Collection<BlockPos> ordinary,
                                                java.util.Collection<BlockPos> priority) {
        Set<BlockPos> admitted = new LinkedHashSet<>();
        admitted.addAll(continuations);
        admitted.addAll(ordinary);
        admitted.addAll(priority);
        return admitted;
    }

    static boolean monstermosSeedNeedsEqualization(double sourceTotalMoles, double neighborTotalMoles) {
        return Double.isFinite(sourceTotalMoles) && Double.isFinite(neighborTotalMoles)
                && Math.abs(sourceTotalMoles - neighborTotalMoles) > MonstermosEqualization.MINIMUM_MOLES_DELTA;
    }

    static boolean monstermosSeedNeedsPressureEqualization(GasMixture source, GasMixture neighbor) {
        if (source == null || neighbor == null) return false;
        double sourcePressure = source.pressureKpa();
        double neighborPressure = neighbor.pressureKpa();
        if (!Double.isFinite(sourcePressure) || !Double.isFinite(neighborPressure)) return false;
        var settings = MonstermosPressureEqualization.DEFAULTS;
        double tolerance = Math.max(settings.absoluteToleranceKpa(),
                Math.max(Math.abs(sourcePressure), Math.abs(neighborPressure)) * settings.relativeTolerance());
        return Math.abs(sourcePressure - neighborPressure) > tolerance;
    }

    private void advanceEqualization(ServerLevel level, WorkQueue queue, Set<BlockPos> monstermosHandledThisCycle,
                                     Set<BlockPos> boundaryCells) {
        EqualizationJob job = equalizationJobs.get(level);
        if (job == null || job.waitingForChunk) return;
        int[] probes = {0};
        BoundedRegionDiscovery.Snapshot result = job.discovery.step(pos -> {
            probes[0]++;
            if (!inBounds(level, pos)) return BoundedRegionDiscovery.ProbeResult.BLOCKED;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) return BoundedRegionDiscovery.ProbeResult.UNKNOWN_UNLOADED;
            if (!AtmosphereTopology.isPassable(level, pos)) return BoundedRegionDiscovery.ProbeResult.BLOCKED;
            if (!isFiniteClaimed(chunk, pos))
                return isExterior(chunk, pos) || isCachedExterior(level, pos)
                        ? BoundedRegionDiscovery.ProbeResult.EXTERIOR_IMMUTABLE
                        : BoundedRegionDiscovery.ProbeResult.BLOCKED;
            return BoundedRegionDiscovery.ProbeResult.FINITE_LOADED;
        }, MAX_DISCOVERY_PROBES_PER_SLICE);
        AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.get(level);
        if (telemetry != null) telemetry.monstermosProbes(probes[0]);
        job.exterior.addAll(result.exteriorSeeds());
        if (result.status() == BoundedRegionDiscovery.Status.PATCH_READY) {
            job.discovered.addAll(result.cells());
            job.discovery.consumePatch();
        } else if (result.status() == BoundedRegionDiscovery.Status.COMPLETE) {
            job.discovered.addAll(result.cells());
            if (job.discovered.size() > MonstermosEqualization.MAX_DISCOVERED_CELLS)
                job.discovered.subList(MonstermosEqualization.MAX_DISCOVERED_CELLS, job.discovered.size()).forEach(pos -> prioritizeSeed(level, pos));
            int patchLimit = monstermosPatchLimit(level, job);
            if (!job.discovered.isEmpty() && commitPatch(level, queue, job,
                    List.copyOf(job.discovered.subList(0, Math.min(job.discovered.size(), patchLimit))),
                    monstermosHandledThisCycle, cycles.get(level).monstermosProcessed(), boundaryCells))
                cycles.get(level).addMonstermosSeeds(job.discovered.subList(
                        Math.min(job.discovered.size(), patchLimit), job.discovered.size()));
            equalizationJobs.remove(level);
        } else if (result.status() == BoundedRegionDiscovery.Status.HARD_LIMIT) {
            // HARD_LIMIT is a bounded-work boundary, never a finite component boundary. Commit
            // whatever finite cells fit in this patch, then hand the unclassified frontier to
            // the fair capped seed scheduler instead of repeatedly starting at the old origin.
            // Its overflow is intentionally coalesced per chunk: in an overloaded chunk only one
            // continuation branch is retained until more ordinary activation supplies another.
            job.discovered.addAll(result.cells());
            if (job.discovered.size() > MonstermosEqualization.MAX_DISCOVERED_CELLS)
                job.discovered.subList(MonstermosEqualization.MAX_DISCOVERED_CELLS, job.discovered.size()).forEach(pos -> prioritizeSeed(level, pos));
            int patchLimit = monstermosPatchLimit(level, job);
            if (!job.discovered.isEmpty() && commitPatch(level, queue, job,
                    List.copyOf(job.discovered.subList(0, Math.min(job.discovered.size(), patchLimit))),
                    monstermosHandledThisCycle, cycles.get(level).monstermosProcessed(), boundaryCells))
                cycles.get(level).addMonstermosSeeds(job.discovered.subList(
                        Math.min(job.discovered.size(), patchLimit), job.discovered.size()));
            result.continuationSeeds().forEach(seed -> prioritizeSeed(level, seed));
            equalizationJobs.remove(level);
        } else if (result.status() == BoundedRegionDiscovery.Status.UNKNOWN_CHUNK) {
            job.waitingForChunk = true;
        } else if (result.status() == BoundedRegionDiscovery.Status.CANCELLED) {
            equalizationJobs.remove(level);
        }
    }

    private int monstermosPatchLimit(ServerLevel level, EqualizationJob job) {
        return isSpace(level.dimension()) && !job.exterior.isEmpty()
                ? MonstermosEqualization.MAX_DISCOVERED_CELLS : MonstermosEqualization.UPSTREAM_SOFT_LIMIT;
    }

    private boolean commitPatch(ServerLevel level, WorkQueue queue, EqualizationJob job, List<BlockPos> cells,
                             Set<BlockPos> monstermosHandledThisCycle, Set<BlockPos> monstermosProcessed,
                             Set<BlockPos> boundaryCells) {
        if (cells.isEmpty() || cells.size() > MonstermosEqualization.MAX_DISCOVERED_CELLS) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return false;
        }
        List<GasMixture> currentMixtures = new java.util.ArrayList<>(cells.size());
        Map<BlockPos, GasMixture> finite = new java.util.LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency = new java.util.LinkedHashMap<>();
        Set<BlockPos> selected = new HashSet<>(cells);
        Set<BlockPos> exterior = new HashSet<>();
        exterior.addAll(job.exterior);
        Map<MonstermosSpaceFlow.DirectedEdge, GasMixture> exteriorFaces = new java.util.LinkedHashMap<>();
        boolean invalid = false;
        for (BlockPos pos : cells) {
            LevelChunk chunk = loadedChunk(level, pos);
            GasMixture current = chunk == null || !AtmosphereTopology.isPassable(level, pos) || !isFiniteClaimed(chunk, pos)
                    ? null : sample(level, pos).orElse(null);
            if (current == null) {
                enqueue(queue, job.seed);
                equalizationJobs.remove(level);
                return false;
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
                } else if (isLoadedPassableExterior(level, neighbor)
                        && AtmosphereTopology.canExchange(level, pos, neighbor)) {
                    GasMixture snapshot = sample(level, neighbor).orElse(null);
                    if (snapshot == null) { invalid = true; break; }
                    exterior.add(neighbor.immutable());
                    exteriorFaces.put(new MonstermosSpaceFlow.DirectedEdge(pos, neighbor), snapshot);
                }
            }
            if (invalid) break;
        }
        if (invalid || !isConnected(cells, adjacency)) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return false;
        }
        // A single graph cannot safely couple a vacuum sink and a replenishing reservoir.
        boolean spacePolicy = isSpace(level.dimension());
        PatchKernel kernel = chooseKernelForPatch(exterior, spacePolicy);
        patchDiagnostics.put(level, new PatchDiagnostic(kernel.name(), "none", "attempted", cells.size(), 0, 0, 0, 0));
        boolean spacePass = kernel == PatchKernel.SPACE_FLOW;
        boolean normalIncomplete = false;
        Map<BlockPos, GasMixture> after;
        Set<BlockPos> normalIncludedCells = null;
        MonstermosSpaceFlow.ExportLedger exported = null;
        MonstermosPressureEqualization.Diagnostics pressureDiagnostic = null;
        int pressureSearchWork = 0;
        int pressureFaceTransfers = 0;
        double pressureSolverMs = 0;
        Map<GasType, Double> boundaryGasPackets = Map.of();
        double boundaryEnergyPacket = 0.0;
        try {
            if (spacePass) {
                MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(finite, adjacency, exteriorFaces, job.seed,
                        MonstermosEqualization.MAX_DISCOVERED_CELLS);
                if (!result.work().complete() || result.states().size() != finite.size()) {
                    patchDiagnostics.put(level, new PatchDiagnostic(kernel.name(), "none", "solver " + result.work().status(), cells.size(), result.work().discoveredCells(), result.work().simulationCalls(), 0, 0));
                    cells.forEach(pos -> prioritizeSeed(level, pos));
                    equalizationJobs.remove(level);
                    return false;
                }
                after = result.states();
                exported = result.exported();
                boundaryGasPackets = exported.speciesMoles();
                boundaryEnergyPacket = exported.thermalEnergyJoules();
                patchDiagnostics.put(level, new PatchDiagnostic(kernel.name(), "none", "solved; commit pending", cells.size(), result.work().discoveredCells(), result.work().simulationCalls(), exported.speciesMoles().values().stream().mapToDouble(Double::doubleValue).sum(), exported.thermalEnergyJoules()));
            } else {
                NormalMonstermosGraph normalGraph = normalMonstermosGraph(finite, adjacency, exteriorFaces);
                long pressureStarted = System.nanoTime();
                MonstermosPressureEqualization.Result pressureResult = solveNormalPressureGraph(normalGraph, job.seed);
                pressureSolverMs = Math.max(0, System.nanoTime() - pressureStarted) / 1_000_000.0;
                pressureDiagnostic = pressureResult.diagnostics();
                MonstermosEqualization.Result result = pressureResult.transport();
                pressureSearchWork = result.work().edgeSearchWork();
                pressureFaceTransfers = result.work().transferOperations();
                normalIncludedCells = result.includedCells();
                after = new LinkedHashMap<>(finite);
                for (BlockPos pos : finite.keySet()) {
                    GasMixture replacement = result.states().get(pos);
                    if (replacement != null) after.put(pos, replacement);
                }
                boundaryGasPackets = result.boundaryExchange().speciesMoles();
                boundaryEnergyPacket = result.boundaryExchange().thermalEnergyJoules();
                normalIncomplete = !result.work().complete();
                patchDiagnostics.put(level, new PatchDiagnostic(kernel.name(), "none", "solved " + result.work().status(), cells.size(), result.work().discoveredCells(), result.work().transferOperations(), boundaryGasPackets.values().stream().mapToDouble(Double::doubleValue).sum(), boundaryEnergyPacket));
            }
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return false;
        }
        // All predictable validation is completed before this loop. Server-thread execution
        // prevents topology or mixture changes between preflight and writes.
        boolean snapshotValid = finite.entrySet().stream().allMatch(e -> {
            GasMixture current = finiteMixture(level, e.getKey());
            return current != null && same(e.getValue(), current);
        })
                && exteriorFaces.entrySet().stream().allMatch(e ->
                        isLoadedPassableExterior(level, e.getKey().to())
                                && AtmosphereTopology.canExchange(level, e.getKey().from(), e.getKey().to())
                                && sample(level, e.getKey().to()).filter(g -> same(e.getValue(), g)).isPresent());
        if (!snapshotValid || !applyPatchTransaction(cells, finite, after,
                (pos, state) -> write(level, pos, state, false), () -> { })) {
            PatchDiagnostic previous = patchDiagnostics.get(level);
            if (previous != null) patchDiagnostics.put(level, new PatchDiagnostic(previous.attempted(), "none", "commit rejected", previous.size(), previous.expansions(), previous.calls(), previous.exportMoles(), previous.exportEnergy()));
            cells.forEach(pos -> prioritizeSeed(level, pos));
            equalizationJobs.remove(level);
            return false;
        }
        Set<BlockPos> processedCells = spacePass ? Set.copyOf(cells)
                : monstermosProcessedFiniteCells(normalIncludedCells, finite.keySet());
        monstermosProcessed.addAll(processedCells);
        if (!spacePass && processedCells.size() < finite.size()) {
            List<BlockPos> omitted = cells.stream().filter(pos -> !processedCells.contains(pos)).toList();
            cycles.get(level).addMonstermosSeeds(omitted);
        }
        boolean changed = cells.stream().anyMatch(pos -> !same(finite.get(pos), after.get(pos)));
        if (changed) reconcileHotspotsAfterPatch(level, finite, after);
        PatchDiagnostic completedPatch = patchDiagnostics.get(level);
        if (completedPatch != null) patchDiagnostics.put(level, new PatchDiagnostic(completedPatch.attempted(), kernel.name(),
                "committed " + (changed ? (normalIncomplete ? "partial" : "complete") : "no-op"), completedPatch.size(), completedPatch.expansions(),
                completedPatch.calls(), completedPatch.exportMoles(), completedPatch.exportEnergy()));
        if (pressureDiagnostic != null) pressureDiagnostics.put(level, new PressureDiagnostic(
                pressureDiagnostic.pressureSpanBefore(), pressureDiagnostic.pressureSpanAfter(), pressureDiagnostic.converged(),
                normalIncludedCells == null ? 0 : normalIncludedCells.size(), pressureDiagnostic.routes(),
                pressureDiagnostic.scalarPreviewSteps(), pressureSearchWork, pressureFaceTransfers, pressureSolverMs));
        AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.get(level);
        if (telemetry != null) telemetry.equalizationCommitted(cells.size(), processedCells.size());
        if (telemetry != null && pressureDiagnostic != null) telemetry.pressureFaceTransfers(pressureFaceTransfers);
        // Both equalization kernels precede LINDA. Handled-cell state is diagnostic/topology
        // bookkeeping only; finite cells still participate in same-cycle LINDA and immutable
        // exterior-face sharing after either kernel commits.
        List<BlockPos> handledCells = spacePass ? cells : cells.stream().filter(processedCells::contains).toList();
        if (changed || spacePass) markMonstermosPatchHandled(handledCells, monstermosHandledThisCycle, queue, spacePass);
        if (spacePass) {
            Set<BlockPos> cycle = spaceBoundaryCellsThisCycle.computeIfAbsent(level, ignored -> new HashSet<>());
            for (BlockPos pos : cells) {
                for (Direction direction : Direction.values())
                    if (exterior.contains(pos.relative(direction))) cycle.add(pos.immutable());
            }
        }
        if (!boundaryGasPackets.isEmpty() || boundaryEnergyPacket != 0.0) {
            BoundaryLedger ledger = boundaryLedgers.computeIfAbsent(level, ignored -> new BoundaryLedger());
            boundaryGasPackets.forEach((type, amount) -> ledger.gas.merge(type, amount, Double::sum));
            ledger.energy += boundaryEnergyPacket;
        }
        List<BlockPos> changedCells = new java.util.ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (!same(currentMixtures.get(i), after.get(cells.get(i)))) {
                changedCells.add(cells.get(i));
                enqueue(queue, cells.get(i));
            }
        }
        Set<BlockPos> continuations = pressureContinuationSeeds.computeIfAbsent(level, ignored -> new LinkedHashSet<>());
        updatePressureContinuationSeeds(continuations, job.seed, normalIncomplete, spacePass);
        if (continuations.isEmpty()) pressureContinuationSeeds.remove(level);
        if (normalIncomplete) {
            prioritizeSeed(level, job.seed);
            changedCells.forEach(pos -> prioritizeSeed(level, pos));
        }
        return true;
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

    static PatchKernel chooseKernelForPatch(Set<BlockPos> verifiedExteriorFaces, boolean spaceDimension) {
        return verifiedExteriorFaces.isEmpty() || !spaceDimension ? PatchKernel.NORMAL_EQUALIZATION : PatchKernel.SPACE_FLOW;
    }

    /** Adds an immutable LINDA endpoint without admitting it to the mutable transaction map. */
    static void connectImmutableBoundary(Map<BlockPos, GasMixture> cycleArchive,
                                          Map<BlockPos, Set<BlockPos>> adjacency,
                                          BlockPos finite, BlockPos exterior, GasMixture baseline) {
        BlockPos finiteKey = finite.immutable(), exteriorKey = exterior.immutable();
        cycleArchive.putIfAbsent(exteriorKey, baseline);
        adjacency.computeIfAbsent(finiteKey, ignored -> new LinkedHashSet<>()).add(exteriorKey);
    }

    static LindaBoundaryExchange lindaBoundaryExchange(Map<BlockPos, GasMixture> before,
                                                        Map<BlockPos, GasMixture> after) {
        EnumMap<GasType, Double> species = new EnumMap<>(GasType.class);
        double beforeEnergy = 0.0, afterEnergy = 0.0;
        for (Map.Entry<BlockPos, GasMixture> entry : before.entrySet()) {
            GasMixture next = after.get(entry.getKey());
            if (next == null) continue;
            GasMixture previous = entry.getValue();
            beforeEnergy += previous.thermalEnergy();
            afterEnergy += next.thermalEnergy();
            for (GasType gas : GasType.values())
                species.merge(gas, previous.moles(gas) - next.moles(gas), Double::sum);
        }
        species.entrySet().removeIf(entry -> entry.getValue() == 0.0);
        return new LindaBoundaryExchange(Map.copyOf(species), beforeEnergy - afterEnergy);
    }

    record LindaBoundaryExchange(Map<GasType, Double> speciesMoles, double energyJoules) { }

    /** Normal Monstermos sees the finite patch plus each distinct, directly adjacent ambient tile. */
    static NormalMonstermosGraph normalMonstermosGraph(Map<BlockPos, GasMixture> finite,
                                                         Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                                                         Map<MonstermosSpaceFlow.DirectedEdge, GasMixture> exteriorFaces) {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>(finite);
        Map<BlockPos, Set<BlockPos>> adjacency = new LinkedHashMap<>();
        finiteAdjacency.forEach((pos, neighbors) -> adjacency.put(pos.immutable(), new LinkedHashSet<>(neighbors)));
        Set<BlockPos> immutable = new LinkedHashSet<>();
        for (var face : exteriorFaces.entrySet()) {
            BlockPos inside = face.getKey().from().immutable(), outside = face.getKey().to().immutable();
            cells.putIfAbsent(outside, face.getValue());
            immutable.add(outside);
            adjacency.computeIfAbsent(inside, ignored -> new LinkedHashSet<>()).add(outside);
            adjacency.computeIfAbsent(outside, ignored -> new LinkedHashSet<>()).add(inside);
        }
        return new NormalMonstermosGraph(cells, adjacency, immutable);
    }

    static MonstermosPressureEqualization.Result solveNormalPressureGraph(NormalMonstermosGraph graph, BlockPos seed) {
        return MonstermosPressureEqualization.equalizeDiscovered(
                graph.cells(), graph.adjacency(), seed, graph.immutablePositions());
    }

    record NormalMonstermosGraph(Map<BlockPos, GasMixture> cells,
                                 Map<BlockPos, Set<BlockPos>> adjacency,
                                 Set<BlockPos> immutablePositions) { }

    enum PatchKernel { NORMAL_EQUALIZATION, SPACE_FLOW, AMBIENT_FLOW }

    static List<GasMixture> equalizeCurrentPatch(List<GasMixture> currentMixtures) {
        return BoundedGasEqualizer.equalize(currentMixtures);
    }

    static final class EqualizationJob {
        private final BlockPos seed;
        final BoundedRegionDiscovery discovery;
        final List<BlockPos> discovered = new java.util.ArrayList<>();
        final Set<BlockPos> exterior = new LinkedHashSet<>();
        boolean waitingForChunk;
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

    private void processLindaTick(ServerLevel level, WorkQueue queue, int retrySteps,
                                  long gameTime,
                                  AtmosphereCycle cycle, Set<BlockPos> cycleSources) {
        Map<BlockPos, GasMixture> finite = new java.util.LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency = new java.util.LinkedHashMap<>();
        List<BlockPos> active = new java.util.ArrayList<>();
        Set<BlockPos> immutableEndpoints = new HashSet<>();
        int inspected = retrySteps;
        while (active.size() < 1
                && inspected + Direction.values().length + 1 <= MAX_CELLS_INSPECTED_PER_TICK && !cycleSources.isEmpty()) {
            BlockPos source = queue.poll(cycleSources);
            if (source == null) { cycleSources.clear(); break; }
            cycleSources.remove(source);
            inspected += Direction.values().length + 1;
            if (source == null || isLoadedPassableExterior(level, source)) continue;
            GasMixture mixture = finiteMixture(level, source);
            if (mixture == null) continue;
            active.add(source.immutable());
            finite.putIfAbsent(source.immutable(), mixture);
            cycle.archive().putIfAbsent(source.immutable(), mixture);
            adjacency.computeIfAbsent(source.immutable(), ignored -> new LinkedHashSet<>());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = source.relative(direction);
                if (!inBounds(level, neighbor) || loadedChunk(level, neighbor) == null
                        || !AtmosphereTopology.canExchange(level, source, neighbor)) continue;
                GasMixture neighborMixture = finiteMixture(level, neighbor);
                if (neighborMixture != null) {
                    BlockPos a = source.immutable(), b = neighbor.immutable();
                    finite.putIfAbsent(b, neighborMixture);
                    cycle.archive().putIfAbsent(b, neighborMixture);
                    adjacency.computeIfAbsent(a, ignored -> new LinkedHashSet<>()).add(b);
                    adjacency.computeIfAbsent(b, ignored -> new LinkedHashSet<>()).add(a);
                } else if (isLoadedPassableExterior(level, neighbor)) {
                    BlockPos exterior = neighbor.immutable();
                    immutableEndpoints.add(exterior);
                    connectImmutableBoundary(cycle.archive(), adjacency, source, exterior, ambient(level));
                }
            }
        }
        ExcitedAtmosphereGroups groups = excitedGroups.computeIfAbsent(level, ignored -> new ExcitedAtmosphereGroups());
        Set<LindaGasSharing.Pair> grouped = new HashSet<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : adjacency.entrySet())
            for (BlockPos neighbor : entry.getValue()) {
                LindaGasSharing.Pair pair = LindaGasSharing.Pair.of(entry.getKey(), neighbor);
                if (groups.bothGrouped(pair)) grouped.add(pair);
            }
        LindaGasSharing.Result result = LindaGasSharing.processPressureAware(finite, cycle.archive(), adjacency, Set.copyOf(active), grouped,
                cycle.lindaProcessedSources(), Math.min(800, active.size()));

        Map<BlockPos, GasMixture> after = new java.util.LinkedHashMap<>(result.mixtures());
        Map<BlockPos, GasReactionStep.WholeAirResult> wholePasses = new LinkedHashMap<>();
        Map<BlockPos, GasMixture> lindaSharedBefore = new LinkedHashMap<>();
        var catalog = PrototypeRuntime.serverGasReactions();
        for (BlockPos pos : active) {
            GasMixture shared = after.get(pos);
            if (shared == null) continue;
            GasReactionStep.WholeAirResult whole = GasReactionStep.evaluateWholeAir(catalog, shared,
                    fireReactionResult(level, pos));
            wholePasses.put(pos.immutable(), whole);
            lindaSharedBefore.put(pos.immutable(), shared);
            after.put(pos, whole.mixture());
        }

        // Validate the complete snapshot and every predicted state before the first finite write.
        boolean valid = result.mixtures().keySet().equals(finite.keySet())
                && finiteSnapshotMatches(finite, pos -> finiteMixture(level, pos));
        if (!valid || !applyPatchTransaction(new java.util.ArrayList<>(finite.keySet()), finite, after,
                (pos, state) -> write(level, pos, state, false), () -> { })) {
            active.forEach(pos -> enqueue(queue, pos));
            return;
        }
        if (!immutableEndpoints.isEmpty()) {
            LindaBoundaryExchange exchange = lindaBoundaryExchange(finite, result.mixtures());
            if (!exchange.speciesMoles().isEmpty() || exchange.energyJoules() != 0.0) {
                BoundaryLedger ledger = boundaryLedgers.computeIfAbsent(level, ignored -> new BoundaryLedger());
                exchange.speciesMoles().forEach((gas, amount) -> ledger.gas.merge(gas, amount, Double::sum));
                ledger.energy += exchange.energyJoules();
            }
        }
        reconcileHotspotsAfterPatch(level, finite, result.mixtures());
        cycle.lindaProcessedSources().addAll(result.processedCells());
        AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.get(level);
        if (telemetry != null) telemetry.lindaSources(result.processedCells().size());
        if (telemetry != null) telemetry.lindaSharedPairs(result.sharedPairs().size());
        Map<BlockPos, Double> fireResults = fireReactionResults.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        wholePasses.forEach((pos, whole) -> storeFireReactionResult(fireResults, pos, whole.fireExtentMoles(), true));
        cycle.wholeAir().putAll(wholePasses);
        ReactionWorkQueue reactionWork = reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue());
        for (Map.Entry<BlockPos, GasReactionStep.WholeAirResult> entry : wholePasses.entrySet()) {
            GasReactionStep.WholeAirResult whole = entry.getValue();
            if (!hasFireEvent(whole.events())) continue;
            BlockPos pos = entry.getKey();
            GasMixture committedGas = after.get(pos);
            HotspotState existingHotspot = reactionWork.hotspot(pos);
            Optional<HotspotState> created = HotspotKernel.expose(committedGas, existingHotspot,
                    committedGas.temperatureKelvin(), committedGas.volumeCubicMeters() * 1000.0,
                    false, true);
            created.ifPresent(state -> {
                        reactionWork.setHotspot(pos, Optional.of(state));
                        AtmosphereVisualServerHooks.noteChanged(level, pos);
                    });
            lindaIgnitionDiagnostic(cycle.identity(), level.getGameTime(), pos, existingHotspot,
                    lindaSharedBefore.get(pos), whole, created).ifPresent(event -> appendIgnitionDiagnostic(level, event));
        }
        Map<BlockPos, GasMixture> groupSnapshots = new LinkedHashMap<>(result.mixtures());
        for (LindaGasSharing.Pair pair : result.sharedPairs()) {
            if (cycle.archive().containsKey(pair.first())) groupSnapshots.put(pair.first(), cycle.archive().get(pair.first()));
            if (cycle.archive().containsKey(pair.second())) groupSnapshots.put(pair.second(), cycle.archive().get(pair.second()));
            groups.onShare(pair, result.absoluteMovedMolesByPair().getOrDefault(pair, 0.0), groupSnapshots);
        }
        after.forEach((pos, finalState) -> {
            if (!same(finite.get(pos), finalState)) enqueue(queue, pos);
        });
        result.activatedNeighbors().forEach(pos -> enqueue(queue, pos));

    }

    static void markMonstermosPatchHandled(List<BlockPos> patch, Set<BlockPos> handledThisCycle,
                                           WorkQueue queue) {
        markMonstermosPatchHandled(patch, handledThisCycle, queue, true);
    }

    static Set<BlockPos> monstermosProcessedFiniteCells(Set<BlockPos> includedCells, Set<BlockPos> finiteCells) {
        if (includedCells == null || finiteCells == null || includedCells.isEmpty() || finiteCells.isEmpty()) return Set.of();
        Set<BlockPos> result = new HashSet<>(includedCells);
        result.retainAll(finiteCells);
        return Set.copyOf(result);
    }

    private static boolean equalizationTouches(EqualizationJob job, BlockPos pos) {
        return job.seed.equals(pos) || job.discovered.contains(pos) || job.exterior.contains(pos);
    }

    private static boolean equalizationTouchesChunk(EqualizationJob job, ChunkPos chunk) {
        return new ChunkPos(job.seed).equals(chunk)
                || job.discovered.stream().anyMatch(pos -> new ChunkPos(pos).equals(chunk))
                || job.exterior.stream().anyMatch(pos -> new ChunkPos(pos).equals(chunk));
    }

    static void markMonstermosPatchHandled(List<BlockPos> patch, Set<BlockPos> handledThisCycle,
                                           WorkQueue queue, boolean boundaryKernel) {
        patch.forEach(pos -> {
            if (boundaryKernel) handledThisCycle.add(pos.immutable());
            queue.offer(pos);
        });
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

    private int advanceExcitedGroups(ServerLevel level, WorkQueue queue, long cycleId, ExcitedAtmosphereGroups groups,
                                     BooleanSupplier deadlineReached) {
        Map<BlockPos, GasMixture> captured = new LinkedHashMap<>();
        Set<BlockPos> immutable = new HashSet<>(), space = new HashSet<>();
        long[] memberReads = {0}, unavailableReads = {0}, failedCaptureNanos = {0};
        ExcitedAtmosphereGroups.CycleResult cycle = groups.advanceCycle(cycleId, members -> {
            long captureStarted = System.nanoTime();
            Map<BlockPos, GasMixture> current = new LinkedHashMap<>();
            Set<BlockPos> immutableMembers = new HashSet<>(), spaceMembers = new HashSet<>();
            for (BlockPos pos : members) {
                memberReads[0]++;
                GroupCellRead read = readGroupCell(level, pos);
                if (read == null) {
                    unavailableReads[0]++;
                    failedCaptureNanos[0] += System.nanoTime() - captureStarted;
                    return null;
                }
                current.put(pos, read.mixture());
                if (read.immutable()) immutableMembers.add(pos);
                if (read.space()) spaceMembers.add(pos);
            }
            captured.putAll(current);
            immutable.addAll(immutableMembers);
            space.addAll(spaceMembers);
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(current, immutableMembers, spaceMembers,
                    System.nanoTime() - captureStarted);
        }, false, 800, deadlineReached);
        AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.get(level);
        if (telemetry != null) telemetry.groupWork(cycle.groupOperations(), memberReads[0], cycle.averages(),
                unavailableReads[0], cycle.captureNanos() + failedCaptureNanos[0], cycle.averageNanos());
        if (!cycle.replacements().isEmpty()) {
            Map<BlockPos, GasMixture> expected = new LinkedHashMap<>();
            for (BlockPos pos : cycle.replacements().keySet()) expected.put(pos, captured.get(pos));
            long publicationStarted = System.nanoTime();
            if (!applyPatchTransaction(new java.util.ArrayList<>(cycle.replacements().keySet()),
                    expected, cycle.replacements(), (pos, state) -> write(level, pos, state, false), () -> { })) {
                cycle.replacements().keySet().forEach(pos -> enqueue(queue, pos));
                return 0;
            }
            if (telemetry != null) telemetry.groupPublication(System.nanoTime() - publicationStarted);
            if (telemetry != null) telemetry.groupAveragedMembers(
                    committedGroupFiniteMembers(expected, cycle.replacements(), immutable));
            reconcileHotspotsAfterPatch(level, captured, cycle.replacements());
            cycle.replacements().forEach((pos, state) -> enqueue(queue, pos));
            if (!cycle.boundarySpeciesMoles().isEmpty() || cycle.boundaryEnergyJoules() != 0.0) {
                BoundaryLedger ledger = boundaryLedgers.computeIfAbsent(level, ignored -> new BoundaryLedger());
                cycle.boundarySpeciesMoles().forEach((gas, amount) -> ledger.gas.merge(gas, amount, Double::sum));
                ledger.energy += cycle.boundaryEnergyJoules();
            }
        }
        return cycle.groupOperations();
    }

    private GroupCellRead readGroupCell(ServerLevel level, BlockPos pos) {
        if (loadedChunk(level, pos) == null) return null;
        GasMixture mixture = finiteMixture(level, pos);
        if (mixture != null) return new GroupCellRead(mixture, false, false);
        if (!isLoadedPassableExterior(level, pos)) return null;
        return new GroupCellRead(ambient(level), true, isSpace(level.dimension()));
    }

    static long committedGroupFiniteMembers(Map<BlockPos, GasMixture> before,
                                            Map<BlockPos, GasMixture> replacements,
                                            Set<BlockPos> immutable) {
        return replacements.entrySet().stream().filter(entry -> !immutable.contains(entry.getKey())
                && before.get(entry.getKey()) != null && !same(before.get(entry.getKey()), entry.getValue())).count();
    }

    private record GroupCellRead(GasMixture mixture, boolean immutable, boolean space) { }

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

    private boolean write(ServerLevel level, BlockPos pos, GasMixture state) {
        return write(level, pos, state, true);
    }

    private boolean write(ServerLevel level, BlockPos pos, GasMixture state, boolean invalidateAfterWrite) {
        if (level == null || pos == null) return false;
        if (!inBounds(level, pos)) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return false;
        if (!isFiniteClaimed(chunk, pos)) return false;
        return writeClaimedMixture(level, pos, chunk, state, invalidateAfterWrite, false);
    }

    private boolean writeClaimedMixture(ServerLevel level, BlockPos pos, LevelChunk chunk, GasMixture state,
                                        boolean invalidateAfterWrite, boolean trustedNoOpSuccess) {
        GasMixture ambient = ambient(level);
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (data == null && same(state, ambient)) return trustedNoOpSuccess;
        if (data == null) data = chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        GasMixture before = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        if (before == null) before = ambient;
        boolean changed = data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state, ambient);
        if (changed) {
            chunk.setUnsaved(true);
            queue(level).noteChunkMutation(pos);
            reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue()).offer(pos);
            AtmosphereVisualServerHooks.noteChanged(level, pos);
            if (invalidateAfterWrite) {
                prioritizeSeed(level, pos);
                activateCellAndNeighbors(level, pos);
            }
            if (invalidateAfterWrite) reconcileHotspotAfterMutation(level, pos, before, state);
        }
        return changed;
    }

    /** Reproject a tracked hotspot after a committed non-reaction gas mutation. */
    private void reconcileHotspotAfterMutation(ServerLevel level, BlockPos pos, GasMixture before, GasMixture after) {
        ReactionWorkQueue reactions = reactionQueues.get(level);
        HotspotState hotspot = reactions == null ? null : reactions.hotspot(pos);
        if (hotspot == null) return;
        Optional<HotspotState> reconciled = reconcileHotspotState(before, after, hotspot);
        reactions.setHotspot(pos, reconciled);
        if (reconciled.isEmpty()) reactions.setBurning(pos, 0);
        AtmosphereVisualServerHooks.noteChanged(level, pos);
    }

    static Optional<HotspotState> reconcileHotspotState(GasMixture before, GasMixture after,
                                                         HotspotState state) {
        return HotspotKernel.reconcileAfterHeatTransfer(before, after, state);
    }

    private void reconcileHotspotsAfterPatch(ServerLevel level, Map<BlockPos, GasMixture> before,
                                              Map<BlockPos, GasMixture> after) {
        after.forEach((pos, mixture) -> {
            GasMixture previous = before.get(pos);
            if (previous != null && !same(previous, mixture)) reconcileHotspotAfterMutation(level, pos, previous, mixture);
        });
    }

    private static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk full ? full : null;
    }

    private boolean isLoadedPassableExterior(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !inBounds(level, pos)) return false;
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

    private void advanceOwnership(ServerLevel level) { advanceOwnership(level, () -> false); }

    private void advanceOwnership(ServerLevel level, BooleanSupplier deadlineReached) {
        OwnershipWork work = ownershipWork.get(level);
        if (work == null) return;
        BoundedBudgetDrain.drain(deadlineReached, () -> {
            long before = work.progressCounter;
            long rejectedBefore = work.rejectedProofs;
            advanceOwnershipSlice(level, deadlineReached);
            return work.progressCounter != before && work.rejectedProofs == rejectedBefore;
        });
    }

    private void advanceOwnershipSlice(ServerLevel level, BooleanSupplier deadlineReached) {
        OwnershipWork work = ownershipWork.get(level);
        if (work == null) return;
        // An active forward proof always gets its checkpoint before optional retries; retry
        // backlogs therefore cannot starve it. A checkpoint is at most eight loaded probes.
        int discoveryBudget = MAX_DISCOVERY_PROBES_PER_SLICE;
        if (work.active != null) {
            int probes = Math.min(8, discoveryBudget);
            advanceOwnershipSearch(level, work, probes);
            discoveryBudget -= probes;
            if (deadlineReached.getAsBoolean()) return;
        }

        int ownershipChecks = 0;
        while (ownershipChecks < Math.min(128, MAX_DISCOVERY_PROBES_PER_SLICE / 3)
                && (ownershipChecks == 0 || !deadlineReached.getAsBoolean())) {
            int slice = Math.min(8, Math.min(128, MAX_DISCOVERY_PROBES_PER_SLICE / 3) - ownershipChecks);
            int drained = work.retryCandidates.drain(slice, deadlineReached, pos -> {
            work.progressCounter++;
            LevelChunk chunk = loadedChunk(level, pos);
            // Reuse normal bounded unknown-seed handling: its search parks by chunk and the chunk
            // load lifecycle wakes it for a fresh proof. Do not let one unloaded tail block later
            // loaded candidates in this retry batch.
            if (chunk == null) return work.offer(pos);
            if (AtmosphereTopology.isPassable(level, pos) && !isFiniteClaimed(chunk, pos))
                return work.offer(pos);
            return true; // blocked or already finite: no search is required
            });
            ownershipChecks += drained;
            if (drained == 0) break;
            if (deadlineReached.getAsBoolean()) break;
        }
        discoveryBudget = Math.max(0, discoveryBudget - ownershipChecks);

        int claimSlices = 0;
        int maxClaimSlices = Math.max(1, (MAX_OWNERSHIP_CLAIMS_PER_SLICE + 7) / 8);
        while (!work.claimPlans.isEmpty() && claimSlices < maxClaimSlices
                && (claimSlices == 0 || !deadlineReached.getAsBoolean())) {
            advanceOwnershipClaims(level, work, 8);
            claimSlices++;
        }
        if (claimSlices > 0 && deadlineReached.getAsBoolean()) return;

        if (work.active == null) {
            boolean seeded = false;
            boolean checkedSeedSlice = false;
            while (!seeded && discoveryBudget > 0
                    && (!checkedSeedSlice || !deadlineReached.getAsBoolean())) {
                int seedChecks = work.startNextSkippingClassified(Math.min(8, discoveryBudget), seed -> {
                    LevelChunk chunk = loadedChunk(level, seed);
                    if (chunk == null) return false;
                    // Existing finite claims take precedence; cached/direct exterior is reusable
                    // only while the seed remains loaded and passable.
                    return isFiniteClaimed(chunk, seed) || isLoadedPassableExterior(level, seed);
                });
                checkedSeedSlice = true;
                discoveryBudget -= seedChecks;
                seeded = work.active != null || seedChecks == 0;
            }
        }
        while (discoveryBudget > 0 && work.active != null) {
            int probes = Math.min(8, discoveryBudget);
            advanceOwnershipSearch(level, work, probes);
            discoveryBudget -= probes;
            if (deadlineReached.getAsBoolean()) break;
        }
    }

    private void advanceOwnershipSearch(ServerLevel level, OwnershipWork work, int probeBudget) {
        if (probeBudget <= 0 || work.active == null) return;
        OwnershipJob job = work.active;
        int[] probes = {0};
        AtmosphereOwnershipSearch.Snapshot result = job.search.step(pos -> {
            probes[0]++;
            if (!inBounds(level, pos)) return AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) return AtmosphereOwnershipSearch.ProbeResult.UNKNOWN_UNLOADED;
            if (!AtmosphereTopology.isPassable(level, pos)) return AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
            if (isFiniteClaimed(chunk, pos)) return AtmosphereOwnershipSearch.ProbeResult.FINITE_CLAIMED;
            return isExterior(chunk, pos) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_SKY_SEED
                    : AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED;
        }, probeBudget);
        AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.get(level);
        if (telemetry != null) telemetry.ownershipProbes(probes[0]);
        work.progressCounter += probes[0];
        finishOwnershipSearch(level, work, job, result);
    }

    private void finishOwnershipSearch(ServerLevel level, OwnershipWork work, OwnershipJob job,
                                       AtmosphereOwnershipSearch.Snapshot result) {
        if (result.status() == AtmosphereOwnershipSearch.Status.EXTERIOR) {
            // A terminal witness is validated and published as one coherent proof operation;
            // this bounded (at most MAX_EXTERIOR_WITNESS_CHECKS cells) operation may overrun
            // the soft tick deadline, but never publishes a partial exterior classification.
            String rejection = exteriorWitnessRejection(level, result.visitedOpenCells());
            if (rejection == null) {
                if (work.rememberExteriorProof(job.seed, result.visitedOpenCells()))
                    wakeFiniteBoundary(level, result.visitedOpenCells());
                else {
                    rejectExteriorWitnessForAdmission(work, job.seed, "proof cache admission failed", result.visitedOpenCells().size());
                    return;
                }
            } else {
                rejectExteriorWitnessForAdmission(work, job.seed, rejection, result.visitedOpenCells().size());
                return;
            }
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

    /** Requeues a failed witness for a later callback and marks this callback's search as yielded. */
    static boolean rejectExteriorWitnessForAdmission(OwnershipWork work, BlockPos seed) {
        return rejectExteriorWitnessForAdmission(work, seed, "witness validation failed", 0);
    }

    static boolean rejectExteriorWitnessForAdmission(OwnershipWork work, BlockPos seed, String reason, int size) {
        work.offer(seed);
        work.active = null;
        work.rejectedProofs++;
        work.lastRejectedSeed = seed.immutable();
        work.lastRejectedReason = reason;
        work.lastRejectedWitnessSize = size;
        return true;
    }

    /** Wake only loaded finite cells adjacent to a newly accepted proof; bounded by proof size. */
    private void wakeFiniteBoundary(ServerLevel level, List<BlockPos> witness) {
        Set<BlockPos> candidates = finiteBoundaryCandidates(witness, pos -> {
            LevelChunk chunk = loadedChunk(level, pos);
            return chunk != null && AtmosphereTopology.isPassable(level, pos) && isFiniteClaimed(chunk, pos);
        });
        WorkQueue queue = queues.get(level);
        if (queue == null || candidates.isEmpty()) return;
        for (BlockPos pos : candidates) {
            queue.offerUrgent(pos);
            prioritizeSeed(level, pos);
        }
    }

    static Set<BlockPos> finiteBoundaryCandidates(List<BlockPos> witness, Predicate<BlockPos> loadedFinite) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (BlockPos open : witness) for (Direction direction : Direction.values()) {
            BlockPos adjacent = open.relative(direction);
            if (loadedFinite.test(adjacent)) candidates.add(adjacent.immutable());
        }
        return Set.copyOf(candidates);
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

    private String exteriorWitnessRejection(ServerLevel level, List<BlockPos> witness) {
        if (witness.isEmpty()) return "empty witness";
        if (witness.size() > MAX_EXTERIOR_WITNESS_CHECKS) return "witness exceeds candidate limit";
        boolean reachesSky = false;
        for (BlockPos pos : witness) {
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) return "witness dependency unloaded";
            if (!AtmosphereTopology.isPassable(level, pos)) return "witness dependency blocked";
            if (isExterior(chunk, pos)) reachesSky = true;
        }
        return reachesSky ? null : "witness has no live sky endpoint";
    }

    private void advanceOwnershipClaims(ServerLevel level, OwnershipWork work) {
        advanceOwnershipClaims(level, work, MAX_OWNERSHIP_CLAIMS_PER_SLICE);
    }

    private void advanceOwnershipClaims(ServerLevel level, OwnershipWork work, int claimBudget) {
        OwnershipClaimJob job = work.claimPlans.pollFirst();
        if (job == null) return;
        job.plan.advance(Math.min(8, claimBudget), pos -> {
            work.progressCounter++;
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return false;
            AtmosphereChunkData data = chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data.claimFinite(pos.getX() & 15, pos.getY(), pos.getZ() & 15)) {
                AtmosphereRuntimeTelemetry telemetry = runtimeTelemetry.get(level);
                if (telemetry != null) telemetry.ownershipClaimed(1);
                chunk.setUnsaved(true);
                AtmosphereVisualServerHooks.noteChanged(level, pos);
                if (data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15) != null)
                    reactionQueues.computeIfAbsent(level, ignored -> new ReactionWorkQueue()).offer(pos);
            }
            return true;
        });
        if (job.plan.isCancelled()) {
            // Already persisted claims remain valid: they were members of a fully proven finite
            // region when committed. Only the uncommitted tail needs a fresh search.
            work.retryCandidates.add(job.plan.uncommittedPositions());
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
        private static final int MAX_PROOF_DEPENDENCIES = AtmosphereOwnershipSearch.DEFAULT_MAX_CANDIDATES;
        final LinkedHashSet<ExteriorProof> proofs = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> exterior = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> seeds = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> overflowSeeds = new LinkedHashSet<>();
        final LinkedHashSet<BlockPos> unknown = new LinkedHashSet<>();
        final java.util.LinkedHashMap<ChunkPos, BlockPos> saturated = new java.util.LinkedHashMap<>();
        final ArrayDeque<OwnershipClaimJob> claimPlans = new ArrayDeque<>();
        final OwnershipRetryCandidates retryCandidates = new OwnershipRetryCandidates();
        private final java.util.Map<BlockPos, java.util.LinkedHashSet<ExteriorProof>> proofByCell = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, java.util.LinkedHashSet<ExteriorProof>> proofByDependency = new java.util.HashMap<>();
        private final java.util.Map<ChunkPos, java.util.LinkedHashSet<ExteriorProof>> proofsByChunk = new java.util.HashMap<>();
        private final java.util.Map<Long, java.util.LinkedHashSet<ExteriorProof>> proofsByColumn = new java.util.HashMap<>();
        private final java.util.Map<ChunkPos, java.util.LinkedHashSet<BlockPos>> unknownByChunk = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, Set<ChunkPos>> unknownChunksBySeed = new java.util.HashMap<>();
        private int proofReferences;
        private BlockPos lastRejectedSeed;
        private String lastRejectedReason;
        private int lastRejectedWitnessSize;
        long progressCounter, rejectedProofs;
        int pendingCount() { return seeds.size() + overflowSeeds.size() + unknown.size() + saturated.size()
                + claimPlans.size() + retryCandidates.pendingWorkItems() + (active == null ? 0 : 1); }
        int activeCandidateCount() { return active == null ? 0 : active.search.activeCandidateCount(); }
        OwnershipDiagnostic diagnostic(BlockPos target) {
            BlockPos activeSeed = active == null ? null : active.seed;
            int candidateCount = active == null ? 0 : active.search.activeCandidateCount();
            String status = "NOT_QUEUED", source = "none", reason = "no ownership work for target";
            if (active != null && (active.seed.equals(target) || active.search.hasCandidate(target))) {
                status = "ACTIVE_SEARCH"; source = "active seed"; reason = "searching bounded candidate graph";
            } else if (seeds.contains(target) || overflowSeeds.contains(target)) {
                status = "SEED_QUEUED"; source = "seed queue"; reason = "awaiting ownership search";
            } else if (claimPlans.stream().anyMatch(job -> job.seed.equals(target))) {
                status = "CLAIM_PUBLICATION"; source = "claim plan"; reason = "finite proof awaits claim publication";
            } else if (unknown.contains(target)) {
                status = "WAITING_UNLOADED"; source = "unloaded boundary"; reason = "ownership search encountered unloaded cells";
            } else if (target.equals(saturated.get(new ChunkPos(target)))) {
                status = "SATURATED"; source = "candidate cap"; reason = "ownership search reached candidate limit";
            } else if (lastRejectedSeed != null && lastRejectedSeed.equals(target)) {
                status = "LAST_REJECTED"; source = "witness validation"; reason = lastRejectedReason;
            }
            return new OwnershipDiagnostic(status, reason, activeSeed, candidateCount, source,
                    lastRejectedSeed, lastRejectedReason, lastRejectedWitnessSize, retryCandidates.pendingWorkItems());
        }
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
        /** Drops stale finite seeds at the head, stopping as soon as one fresh seed is active. */
        int startNextSkippingClassified(int budget, Predicate<BlockPos> classified) {
            int checked = 0;
            while (active == null && checked < budget) {
                BlockPos seed = startNext();
                if (seed == null) break;
                checked++;
                if (!classified.test(seed)) break;
                finishActive();
            }
            progressCounter += checked;
            return checked;
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
                retryCandidates.add(job.plan.uncommittedPositions());
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
                retryCandidates.add(job.plan.uncommittedPositions());
            }
            return retrySeeds;
        }
        List<BlockPos> retryForChunkLifecycle(ChunkPos chunk) {
            List<BlockPos> retrySeeds = onChunkLifecycle(chunk);
            retrySeeds.forEach(this::offer);
            return retrySeeds;
        }
        boolean rememberExteriorProof(BlockPos seed, List<BlockPos> dependencies) {
            LinkedHashSet<BlockPos> uniqueDependencies = new LinkedHashSet<>();
            dependencies.forEach(pos -> uniqueDependencies.add(pos.immutable()));
            if (uniqueDependencies.isEmpty() || uniqueDependencies.size() > MAX_PROOF_DEPENDENCIES) return false;
            while (proofReferences + uniqueDependencies.size() > MAX_PROOF_DEPENDENCIES && !proofs.isEmpty())
                removeProof(proofs.iterator().next());
            if (proofReferences + uniqueDependencies.size() > MAX_PROOF_DEPENDENCIES) return false;
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
            return true;
        }
        boolean isVerifiedExterior(BlockPos pos) {
            Set<ExteriorProof> indexed = proofByCell.get(pos);
            return indexed != null && indexed.stream().anyMatch(proof -> proof.valid);
        }
        boolean hasExteriorDependency(BlockPos pos) {
            return proofByDependency.containsKey(pos) || proofsByColumn.containsKey(columnKey(pos));
        }
        boolean hasExteriorWitnessCell(BlockPos pos) {
            Set<ExteriorProof> indexed = proofByDependency.get(pos);
            return indexed != null && indexed.stream().anyMatch(proof -> proof.valid);
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
    boolean isSpace(ResourceKey<Level> dimension) { return AtmosphereProfiles.spaceDimension(dimension, spaceDimensions); }
    GasMixture ambient(ResourceKey<Level> dimension) { return AtmosphereProfiles.ambient(dimension, vacuumDimensions); }
    private double fireReactionResult(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Double> results = fireReactionResults.get(level);
        return results == null ? 0 : results.getOrDefault(pos, 0.0);
    }
    static void storeFireReactionResult(Map<BlockPos, Double> results, BlockPos pos,
                                        double fireResultMoles, boolean committed) {
        if (!committed || results == null || pos == null || !Double.isFinite(fireResultMoles) || fireResultMoles < 0) return;
        results.put(pos.immutable(), fireResultMoles);
    }
    static void removeFireReactionResultsInChunk(Map<BlockPos, Double> results, ChunkPos chunk) {
        results.keySet().removeIf(pos -> new ChunkPos(pos).equals(chunk));
    }
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
        return retryDirtyChunks(level, queue, budget, () -> false);
    }

    private int retryDirtyChunks(ServerLevel level, WorkQueue queue, int budget, BooleanSupplier deadlineReached) {
        int steps = 0;
        while (steps < budget && (steps == 0 || !deadlineReached.getAsBoolean())) {
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
        if (a == null || b == null) return a == b;
        if (a.temperatureKelvin() != b.temperatureKelvin()
                || a.volumeCubicMeters() != b.volumeCubicMeters()) return false;
        for (GasType type : GasType.values()) if (a.moles(type) != b.moles(type)) return false;
        return true;
    }

    static boolean finiteSnapshotMatches(Map<BlockPos, GasMixture> expected,
                                         Function<BlockPos, GasMixture> currentMixture) {
        for (Map.Entry<BlockPos, GasMixture> entry : expected.entrySet())
            if (!same(entry.getValue(), currentMixture.apply(entry.getKey()))) return false;
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

        BlockPos poll(Set<BlockPos> eligible) {
            for (var iterator = urgent.iterator(); iterator.hasNext();) {
                BlockPos pos = iterator.next();
                if (!eligible.contains(pos)) continue;
                iterator.remove();
                queued.remove(pos);
                consecutiveUrgentPolls++;
                return pos;
            }
            for (var iterator = pending.iterator(); iterator.hasNext();) {
                BlockPos pos = iterator.next();
                if (!eligible.contains(pos)) continue;
                iterator.remove();
                queued.remove(pos);
                consecutiveUrgentPolls = 0;
                return pos;
            }
            return null;
        }

        Set<BlockPos> snapshotPositions() {
            Set<BlockPos> snapshot = new LinkedHashSet<>(pending);
            snapshot.addAll(urgent);
            return snapshot;
        }

        void removePosition(BlockPos pos) {
            pending.remove(pos);
            urgent.remove(pos);
            queued.remove(pos);
        }

        boolean contains(BlockPos pos) { return queued.contains(pos); }

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
        Set<BlockPos> snapshotPositions() {
            Set<BlockPos> result = new LinkedHashSet<>(pending);
            result.addAll(overflowSeeds.values());
            return result;
        }
        void remove(BlockPos pos) {
            pending.remove(pos);
            queued.remove(pos);
            overflowSeeds.remove(new ChunkPos(pos), pos);
        }
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
