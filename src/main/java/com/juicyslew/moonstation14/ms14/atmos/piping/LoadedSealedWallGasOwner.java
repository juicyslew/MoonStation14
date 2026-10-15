package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/** Independent finite-gas ownership for persisted sealed-wall lanes. */
public final class LoadedSealedWallGasOwner {
    public enum RollbackResult { ROLLED_BACK, NOT_APPLICABLE, RECOVERY_REQUIRED }
    private LoadedSealedWallGasOwner() { }

    /** Removes only the exact, untouched vacuum ownership created by fresh admission. */
    public static RollbackResult rollbackFreshVacuumOwner(ServerLevel level, BlockPos pos,
                                                           PipeTopology.Layer lane, UUID exactId) {
        var record = LoadedSealedWallSegment.matchingLive(level, pos, lane).orElse(null);
        if (record == null || !record.id().equals(exactId)) return RollbackResult.NOT_APPLICABLE;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return RollbackResult.NOT_APPLICABLE;
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pos, lane);
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.incarnation().equals(exactId) || !entry.active()
                || entry.version() != 1) return RollbackResult.RECOVERY_REQUIRED;
        if (!entry.mixture().gasMoles().isEmpty() || entry.mixture().thermalEnergy() != 0.0)
            return RollbackResult.RECOVERY_REQUIRED;
        try {
            PipeGasInventory after = before.tombstoneEmpty(key, exactId);
            return LoadedSealedWallSegment.matchingLive(level, pos, lane).filter(record::equals).isPresent()
                    && data.replace(before.revision(), before, after)
                    ? RollbackResult.ROLLED_BACK : RollbackResult.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) { return RollbackResult.RECOVERY_REQUIRED; }
    }

    /** Admit vacuum only for a newly persisted, currently matching wall record. */
    public static boolean createVacuumOwnerForTrustedAdmission(ServerLevel level, BlockPos pos, PipeTopology.Layer lane) {
        var record = LoadedSealedWallSegment.matchingLive(level, pos, lane).orElse(null);
        if (record == null) return false;
        UUID id = record.id();
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pos, lane);
        PipeGasInventorySavedData data = PipeGasInventorySavedData.forMutation(level);
        PipeGasInventory before = data.snapshot();
        // Existing ownership (including dormant ownership) is never a vacuum admission.
        if (before.get(key).isPresent()) return false;
        PipeGasInventory after;
        try { after = before.admit(key, id, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3); }
        catch (RuntimeException failure) { return false; }
        return LoadedSealedWallSegment.matchingLive(level, pos, lane).filter(record::equals).isPresent()
                && data.replace(before.revision(), before, after);
    }

    /** Reactivate only an exact persisted matching record and its dormant owner. */
    public static boolean reconcileExisting(ServerLevel level, BlockPos pos, PipeTopology.Layer lane) {
        var record = LoadedSealedWallSegment.matchingLive(level, pos, lane).orElse(null);
        if (record == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pos, lane);
        UUID id = record.id();
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.incarnation().equals(id)
                || Double.compare(entry.volume(), record.volume()) != 0 || entry.active()) return false;
        try {
            PipeGasInventory after = before.admit(key, id, record.volume());
            return LoadedSealedWallSegment.matchingLive(level, pos, lane).filter(record::equals).isPresent()
                    && data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) { return false; }
    }

    /** Persist dormancy without changing gas, using the exact record/owner UUID. */
    public static boolean deactivate(ServerLevel level, BlockPos pos, PipeTopology.Layer lane) {
        var record = LoadedSealedWallSegment.matchingLive(level, pos, lane).orElse(null);
        if (record == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pos, lane);
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.active() || !entry.incarnation().equals(record.id())) return false;
        try {
            PipeGasInventory after = before.unload(key, record.id());
            return after == before || data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) { return false; }
    }

    /** Read-only fail-closed ownership view. */
    public static Optional<PipeGasInventory.Entry> activeSnapshot(ServerLevel level, BlockPos pos, PipeTopology.Layer lane) {
        var record = LoadedSealedWallSegment.matchingLive(level, pos, lane).orElse(null);
        if (record == null) return Optional.empty();
        return PipeGasInventorySavedData.existing(level).map(data -> data.snapshot()
                .get(new PipeTopology.NodeKey(pos, lane)).filter(PipeGasInventory.Entry::active)
                .filter(entry -> entry.incarnation().equals(record.id()))
                .filter(entry -> Double.compare(entry.volume(), record.volume()) == 0)).orElse(null);
    }
}

