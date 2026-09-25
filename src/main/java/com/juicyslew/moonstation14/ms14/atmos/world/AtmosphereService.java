package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereTransfer;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Server-authoritative sparse atmosphere queries, mutations, and bounded diffusion. */
public final class AtmosphereService {
    public static final AtmosphereService INSTANCE = new AtmosphereService();
    public static final int TICK_CADENCE = 4;
    public static final int MAX_PAIR_EDGES_PER_TICK = 128;
    public static final int MAX_CELLS_INSPECTED_PER_TICK = 512;
    static final int MAX_DEFERRED_CHUNK_LOADS_PER_TICK = 16;
    private static final int MAX_QUEUED_POSITIONS = 8192;
    private static final double TRANSFER_FRACTION = 0.125;
    private static final double CONVERGENCE_EPSILON = 1.0e-8;

    private final Map<ServerLevel, WorkQueue> queues = new WeakHashMap<>();
    private final Map<ServerLevel, DeferredChunkQueue> deferredChunkLoads = new WeakHashMap<>();
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
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        GasMixture ambient = ambient(level);
        GasMixture stored = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        return Optional.of(stored == null ? ambient : stored);
    }

    public boolean addGas(ServerLevel level, BlockPos pos, GasType type, double moles, double injectionTemperature) {
        if (!enabled || type == null || !Double.isFinite(moles) || moles <= 0.0 || !Double.isFinite(injectionTemperature) || injectionTemperature < 0.0) return false;
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
        Optional<GasMixture> current = sample(level, pos);
        if (current.isEmpty()) return false;
        try { return write(level, pos, current.get().withEnergyDelta(joules)); }
        catch (IllegalArgumentException | IllegalStateException exception) { return false; }
    }

    /** Re-activates a changed cell and its local stencil, never scanning the world. */
    public void invalidate(ServerLevel level, BlockPos pos) {
        if (!enabled || level == null || pos == null || !inBounds(level, pos) || loadedChunk(level, pos) == null) return;
        WorkQueue queue = queue(level);
        enqueue(queue, pos);
        for (Direction direction : Direction.values()) enqueue(queue, pos.relative(direction));
    }

    public void tick(ServerLevel level, long gameTime) {
        if (!enabled || level == null || gameTime % TICK_CADENCE != 0) return;
        WorkQueue queue = queue(level);
        int edges = 0;
        int cellsInspected = 0;
        // Charge the worst case (source plus its six neighbors) before processing one
        // queued cell, so even blocked/ambient stencils cannot exceed the inspection budget.
        while (edges < MAX_PAIR_EDGES_PER_TICK && cellsInspected + Direction.values().length + 1 <= MAX_CELLS_INSPECTED_PER_TICK && !queue.isEmpty()) {
            BlockPos pos = queue.poll();
            cellsInspected += Direction.values().length + 1;
            if (sample(level, pos).isEmpty()) continue;
            for (Direction direction : Direction.values()) {
                if (edges >= MAX_PAIR_EDGES_PER_TICK) { enqueue(queue, pos); break; }
                BlockPos neighbor = pos.relative(direction);
                if (sample(level, neighbor).isEmpty() || !AtmosphereTopology.canExchange(level, pos, neighbor)) continue;
                edges++;
                exchange(level, queue, pos, neighbor);
            }
        }
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
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        ChunkPos cp = chunk.getPos();
        if (data != null) {
            for (AtmosphereChunkData.CellPosition cell : data.snapshot().keySet()) {
                BlockPos pos = new BlockPos(cp.getMinBlockX() + cell.x(), cell.y(), cp.getMinBlockZ() + cell.z());
                invalidate(level, pos);
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
        DeferredChunkQueue deferred = deferredChunkLoads.get(level);
        if (deferred != null) {
            deferred.remove(chunk);
            if (deferred.isEmpty()) deferredChunkLoads.remove(level);
        }
        WorkQueue queue = queue(level);
        queue.pending.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
        queue.queued.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
        queue.overflow.removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
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

    public void clear(ServerLevel level) { queues.remove(level); deferredChunkLoads.remove(level); }
    public void clearAll() { queues.clear(); deferredChunkLoads.clear(); }

    int queueCountForTesting() { return queues.size(); }
    int deferredChunkCountForTesting() { return deferredChunkLoads.values().stream().mapToInt(DeferredChunkQueue::size).sum(); }

    private void exchange(ServerLevel level, WorkQueue queue, BlockPos a, BlockPos b) {
        Optional<GasMixture> beforeA = sample(level, a);
        Optional<GasMixture> beforeB = sample(level, b);
        if (beforeA.isEmpty() || beforeB.isEmpty()) return;
        // Bounded FIFO pair processing is an intentional approximation and can introduce
        // neighbor-order bias, while keeping server-tick work finite.
        AtmosphereTransfer.Result result = AtmosphereTransfer.step(beforeA.get(), beforeB.get(), TRANSFER_FRACTION);
        if (!meaningful(beforeA.get(), result.first()) && !meaningful(beforeB.get(), result.second())) return;
        // Read both sides first, then commit both immutable results. Each cell is one cubic meter;
        // ambient is a finite local starting state, not a boundary reservoir.
        write(level, a, result.first());
        write(level, b, result.second());
        enqueue(queue, a);
        enqueue(queue, b);
        for (Direction direction : Direction.values()) {
            enqueue(queue, a.relative(direction));
            enqueue(queue, b.relative(direction));
        }
    }

    private boolean write(ServerLevel level, BlockPos pos, GasMixture state) {
        if (!enabled || level == null || pos == null) return false;
        if (!inBounds(level, pos)) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null || !AtmosphereTopology.isPassable(level, pos)) return false;
        GasMixture ambient = ambient(level);
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        if (data == null && same(state, ambient)) return false;
        if (data == null) data = chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        boolean changed = data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state, ambient);
        if (changed) {
            chunk.setUnsaved(true);
            invalidate(level, pos);
        }
        return changed;
    }

    private static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk full ? full : null;
    }

    private static boolean inBounds(ServerLevel level, BlockPos pos) { return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight(); }
    private GasMixture ambient(ServerLevel level) { return ambient(level.dimension()); }
    GasMixture ambient(ResourceKey<Level> dimension) { return AtmosphereProfiles.ambient(dimension, vacuumDimensions); }
    private WorkQueue queue(ServerLevel level) { return queues.computeIfAbsent(level, ignored -> new WorkQueue()); }
    private void enqueue(WorkQueue queue, BlockPos pos) {
        queue.offer(pos);
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

    static final class WorkQueue {
        static final int HOT_QUEUE_CAPACITY = MAX_QUEUED_POSITIONS;
        final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        final java.util.LinkedHashSet<BlockPos> overflow = new java.util.LinkedHashSet<>();
        final Set<BlockPos> queued = new HashSet<>();

        boolean offer(BlockPos pos) {
            BlockPos key = pos.immutable();
            if (!queued.add(key)) return false;
            if (pending.size() < MAX_QUEUED_POSITIONS) pending.addLast(key);
            else overflow.add(key);
            return true;
        }

        BlockPos poll() {
            BlockPos pos = pending.pollFirst();
            if (pos != null) {
                queued.remove(pos);
                if (!overflow.isEmpty()) {
                    BlockPos next = overflow.iterator().next();
                    overflow.remove(next);
                    pending.addLast(next);
                }
            }
            return pos;
        }

        boolean isEmpty() { return pending.isEmpty(); }
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
