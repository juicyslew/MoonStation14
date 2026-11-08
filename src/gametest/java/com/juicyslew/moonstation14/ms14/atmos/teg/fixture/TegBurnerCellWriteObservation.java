package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fixture-only observation of persisted writes in the TEG's burner cells. */
final class TegBurnerCellWriteObservation implements AutoCloseable {
    private final ServerLevel level;
    private final java.util.List<Group> groups;
    private boolean closed;

    private TegBurnerCellWriteObservation(ServerLevel level, java.util.List<Group> groups) {
        this.level = level;
        this.groups = java.util.List.copyOf(groups);
    }

    static TegBurnerCellWriteObservation acquire(GameTestHelper helper, ServerLevel level, Set<BlockPos> positions) {
        if (level == null || positions == null) throw new NullPointerException();
        Set<BlockPos> unique = new HashSet<>();
        for (BlockPos pos : positions) unique.add(java.util.Objects.requireNonNull(pos).immutable());
        if (unique.size() != 27) throw new IllegalArgumentException("expected 27 unique burner cells");
        Map<ChunkPos, Set<AtmosphereChunkData.CellPosition>> byChunk = new HashMap<>();
        for (BlockPos pos : unique) byChunk.computeIfAbsent(new ChunkPos(pos), ignored -> new HashSet<>())
                .add(new AtmosphereChunkData.CellPosition(pos.getX() & 15, pos.getY(), pos.getZ() & 15));
        var groups = new ArrayList<Group>();
        Map<ChunkPos, Set<AtmosphereChunkData.CellPosition>> cleanupByChunk = byChunk;
        try {
            for (var entry : byChunk.entrySet()) {
                ChunkPos cp = entry.getKey();
                var chunk = level.getChunkSource().getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk full)
                        || !full.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))
                    throw new IllegalStateException("burner chunk is not loaded and full: " + cp);
                AtmosphereChunkData data = full.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
                if (data == null) throw new IllegalStateException("burner chunk has no existing atmosphere data: " + cp);
                var observer = data.observeWrites(entry.getValue());
                var cleanupObserver = data.observeNumericalCleanup(cleanupByChunk.get(cp));
                groups.add(new Group(cp, full, data, observer, cleanupObserver));
            }
            TegBurnerCellWriteObservation observation = new TegBurnerCellWriteObservation(level, groups);
            try {
                helper.testInfo.addListener(new GameTestListener() {
                    @Override public void testStructureLoaded(GameTestInfo ignored) { }
                    @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { observation.close(); }
                    @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { observation.close(); }
                    @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { observation.close(); }
                });
            } catch (RuntimeException | Error failure) { observation.close(); throw failure; }
            return observation;
        } catch (RuntimeException | Error failure) {
            groups.forEach(group -> { group.observer.close(); group.cleanupObserver.close(); });
            throw failure;
        }
    }

    Snapshot snapshot() {
        if (closed) throw new IllegalStateException("observation is closed");
        long writes = 0;
        Map<GasType, Double> bounds = new EnumMap<>(GasType.class);
        Map<GasType, Double> cleanup = new EnumMap<>(GasType.class);
        for (Group group : groups) {
            var current = level.getChunkSource().getChunk(group.position.x, group.position.z, ChunkStatus.FULL, false);
            if (current != group.chunk || current == null
                    || current.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()) != group.data)
                throw new IllegalStateException("burner chunk or atmosphere data was replaced: " + group.position);
            var snap = group.observer.snapshot();
            try { writes = Math.addExact(writes, snap.writeCount()); }
            catch (ArithmeticException exception) { throw new IllegalStateException("write count overflow", exception); }
            snap.speciesBounds().forEach((gas, amount) -> {
                if (!Double.isFinite(amount)) throw new IllegalStateException("non-finite write allowance");
                double sum = bounds.getOrDefault(gas, 0d) + amount;
                if (!Double.isFinite(sum)) throw new IllegalStateException("write allowance overflow");
                bounds.put(gas, sum);
            });
            group.cleanupObserver.snapshot().forEach((gas, amount) -> cleanup.merge(gas, amount, Double::sum));
        }
        return new Snapshot(writes, bounds, cleanup);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        groups.forEach(group -> { group.observer.close(); group.cleanupObserver.close(); });
    }

    record Snapshot(long writeCount, Map<GasType, Double> speciesBounds, Map<GasType, Double> cleanupMoles) {
        Snapshot { speciesBounds = Map.copyOf(speciesBounds); cleanupMoles = Map.copyOf(cleanupMoles); }
    }
    private record Group(ChunkPos position, LevelChunk chunk, AtmosphereChunkData data,
                         AtmosphereChunkData.WriteObserver observer,
                         AtmosphereChunkData.NumericalCleanupObserver cleanupObserver) { }
}
