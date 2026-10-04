package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Narrow identity bridge for live exposed lanes. It deliberately performs no gas exchange. */
public final class LoadedPipeGasOwner {
    private LoadedPipeGasOwner() { }

    /** Trusted developer/test setup only: creates a genuinely new vacuum lane. */
    public static Optional<UUID> createVacuumLane(ServerLevel level, BlockPos pos, int lane) {
        Objects.requireNonNull(level); Objects.requireNonNull(pos);
        if (!valid(level, pos, lane)) return Optional.empty();
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        PipeTopology.NodeKey key = key(pos, lane);
        if (visual.functionalIdentity(lane).isPresent()) return Optional.empty();
        PipeGasInventorySavedData data = PipeGasInventorySavedData.forMutation(level);
        PipeGasInventory before = data.snapshot();
        if (before.get(key).isPresent()) return Optional.empty();
        UUID id = UUID.randomUUID();
        PipeGasInventory after;
        try { after = before.admit(key, id, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3); }
        catch (RuntimeException failure) { return Optional.empty(); }
        if (!data.replace(before.revision(), before, after)) return Optional.empty();
        if (visual.markFunctionalLayer(lane, id)) return Optional.of(id);
        // Only remove the fresh, still-empty vacuum record created above; never remove gas.
        PipeGasInventory current = data.snapshot();
        try {
            PipeGasInventory.Entry entry = current.get(key).orElseThrow();
            if (entry.incarnation().equals(id) && entry.mixture().gasMoles().isEmpty()
                    && entry.mixture().thermalEnergy() == 0.0) {
                PipeGasInventory rollback = current.tombstoneEmpty(key, id);
                data.replace(current.revision(), current, rollback);
            }
        } catch (RuntimeException ignored) { /* Fail closed; an unmatched record remains inert. */ }
        return Optional.empty();
    }

    /** Removes only a just-admitted, unchanged vacuum lane after follow-up admission failed. */
    static boolean rollbackFreshVacuumLane(ServerLevel level, BlockPos pos, int lane, UUID expectedId) {
        if (expectedId == null || !valid(level, pos, lane)) return false;
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        if (!visual.functionalIdentity(lane).filter(expectedId::equals).isPresent()) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeTopology.NodeKey key = key(pos, lane);
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.active() || entry.version() != 1 || !entry.incarnation().equals(expectedId)
                || Double.compare(entry.volume(), Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) != 0
                || !entry.mixture().gasMoles().isEmpty() || entry.mixture().thermalEnergy() != 0.0) return false;
        if (!visual.clearFunctionalLayer(lane, expectedId)) return false;
        try {
            PipeGasInventory after = before.tombstoneEmpty(key, expectedId);
            if (data.replace(before.revision(), before, after)) return true;
        } catch (RuntimeException ignored) { }
        // The record remains inert if restoration is impossible; never manufacture a replacement.
        visual.restoreFunctionalLayer(lane, expectedId);
        return false;
    }

    /** Re-activates only the exact persisted incarnation; never creates a missing entry. */
    public static boolean reconcileExisting(ServerLevel level, BlockPos pos, int lane) {
        if (!valid(level, pos, lane)) return false;
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        UUID id = visual.functionalIdentity(lane).orElse(null);
        if (id == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key(pos, lane)).orElse(null);
        if (entry == null || !entry.incarnation().equals(id)
                || Double.compare(entry.volume(), Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) != 0) return false;
        try {
            PipeGasInventory after = before.admit(key(pos, lane), id, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3);
            return after == before || data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) { return false; }
    }

    /** Chunk-unload hook target; persists dormancy without altering the mixture. */
    public static boolean deactivate(ServerLevel level, BlockPos pos, int lane) {
        if (!valid(level, pos, lane)) return false;
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        UUID id = visual.functionalIdentity(lane).orElse(null);
        if (id == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeGasInventory before = data.snapshot();
        try {
            PipeGasInventory after = before.unload(key(pos, lane), id);
            return after == before || data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) { return false; }
    }

    /** Retires the exact physically removed lane, archiving any non-vacuum ownership. */
    static boolean retireRemovedLane(ServerLevel level, BlockPos pos, int lane, UUID id) {
        if (id == null || lane < 1 || !level.getServer().isSameThread()) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeTopology.NodeKey key = key(pos, lane);
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.incarnation().equals(id)) return false;
        try {
            PipeGasInventory after = before.retire(key, id);
            return data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Retires every stale lane record at this position in one save commit when the loaded holder is proven AIR.
     * AIR proves the whole holder is gone; this deliberately does not touch any other position. */
    static boolean retireOrphanAtAir(ServerLevel level, BlockPos pos, int lane) {
        if ((lane != VisualPipeBlockEntity.PRIMARY && lane != VisualPipeBlockEntity.SECONDARY
                && lane != VisualPipeBlockEntity.TERTIARY) || !level.getServer().isSameThread() || pos.getY() < level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight()) return false;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk loaded) || !loaded.getBlockState(pos).isAir()
                || !loaded.getFluidState(pos).isEmpty() || loaded.getBlockEntity(pos) != null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return true;
        PipeGasInventory before = data.snapshot();
        try {
            Map<PipeTopology.NodeKey, UUID> owners = new java.util.LinkedHashMap<>();
            for (PipeTopology.Layer layer : PipeTopology.Layer.values()) {
                PipeTopology.NodeKey key = new PipeTopology.NodeKey(pos, layer);
                PipeGasInventory.Entry entry = before.get(key).orElse(null);
                if (entry != null) owners.put(key, entry.incarnation());
            }
            if (owners.isEmpty()) return true;
            PipeGasInventory after = before.retireBatch(owners);
            return data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) { return false; }
    }

    /** Read-only and fail-closed: a stale or unloaded visual identity is never exposed. */
    public static Optional<PipeGasInventory.Entry> activeSnapshot(ServerLevel level, BlockPos pos, int lane) {
        if (!valid(level, pos, lane)) return Optional.empty();
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        UUID id = visual.functionalIdentity(lane).orElse(null);
        if (id == null) return Optional.empty();
        PipeGasInventory.Entry entry = PipeGasInventorySavedData.existing(level).map(d -> d.snapshot().get(key(pos, lane)).orElse(null)).orElse(null);
        return entry != null && entry.active() && entry.incarnation().equals(id)
                && Double.compare(entry.volume(), Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) == 0
                ? Optional.of(entry) : Optional.empty();
    }

    private static boolean valid(ServerLevel level, BlockPos pos, int lane) {
        if (lane != VisualPipeBlockEntity.PRIMARY && lane != VisualPipeBlockEntity.SECONDARY
                && lane != VisualPipeBlockEntity.TERTIARY) return false;
        if (!level.getServer().isSameThread() || pos.getY() < level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight()) return false;
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false)
                instanceof LevelChunk chunk
                && chunk.getBlockState(pos).is(com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get())
                && chunk.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual && visual.hasLayer(lane);
    }

    private static PipeTopology.NodeKey key(BlockPos pos, int lane) {
        PipeTopology.Layer layer = switch (lane) {
            case VisualPipeBlockEntity.PRIMARY -> PipeTopology.Layer.PRIMARY;
            case VisualPipeBlockEntity.SECONDARY -> PipeTopology.Layer.SECONDARY;
            case VisualPipeBlockEntity.TERTIARY -> PipeTopology.Layer.TERTIARY;
            default -> throw new IllegalArgumentException("invalid lane");
        };
        return new PipeTopology.NodeKey(pos, layer);
    }
}
