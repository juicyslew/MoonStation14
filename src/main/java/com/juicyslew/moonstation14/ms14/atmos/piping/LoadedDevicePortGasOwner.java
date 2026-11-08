package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved-inventory adapter for exact host-owned device port descriptors. */
public final class LoadedDevicePortGasOwner {
    private LoadedDevicePortGasOwner() { }

    public static boolean admit(ServerLevel level, BlockPos pos, String portId) {
        LoadedPipeNetworks.invalidateTopology(level, pos);
        PipeGasPortHost.Port port = port(level, pos, portId).orElse(null);
        if (port == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.forMutation(level);
        PipeGasInventory before = data.snapshot();
        try {
            PipeGasInventory after = before.admit(port.key(pos), port.incarnation(), port.volume());
            boolean admitted = stillExact(level, pos, port)
                    && (after == before || data.replace(before.revision(), before, after));
            if (admitted) LoadedPipeNetworks.invalidateTopology(level, pos);
            return admitted;
        } catch (RuntimeException failure) { return false; }
    }

    public static boolean reconcileExisting(ServerLevel level, BlockPos pos, String portId) {
        LoadedPipeNetworks.invalidateTopology(level, pos);
        PipeGasPortHost.Port port = port(level, pos, portId).orElse(null);
        if (port == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry old = before.get(port.key(pos)).orElse(null);
        if (old == null || old.active() || !old.incarnation().equals(port.incarnation())
                || Double.compare(old.volume(), port.volume()) != 0) return false;
        try {
            PipeGasInventory after = before.admit(port.key(pos), port.incarnation(), port.volume());
            return stillExact(level, pos, port) && data.replace(before.revision(), before, after);
        } catch (RuntimeException failure) { return false; }
    }

    public static boolean deactivate(ServerLevel level, BlockPos pos, String portId) {
        PipeGasPortHost.Port port = port(level, pos, portId).orElse(null);
        if (port == null) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeGasInventory before = data.snapshot();
        try {
            PipeGasInventory after = before.unload(port.key(pos), port.incarnation());
            return stillExact(level, pos, port) && (after == before || data.replace(before.revision(), before, after));
        } catch (RuntimeException failure) { return false; }
    }

    /** Prepares an unpublished, exact-host relocation of every registered device port to one layer. */
    public static Optional<PipeGasTransaction.PreparedMutation> prepareLayerRelocation(
            ServerLevel level, BlockPos pos, PipeTopology.Layer destinationLayer) {
        LoadedPipeNetworks.invalidateTopology(level, pos);
        if (destinationLayer == null) return Optional.empty();
        PipeGasPortHost host = host(level, pos).orElse(null);
        if (host == null) return Optional.empty();
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElse(null);
        if (saved == null) return Optional.empty();
        try {
            List<PipeGasPortHost.Port> descriptors = List.copyOf(host.gasPorts());
            if (descriptors.isEmpty()) return Optional.empty();
            PipeGasInventory before = saved.snapshot();
            Map<PipeTopology.NodeKey, PipeGasInventory.ExpectedIdentityVersionAndDestination> moves = new LinkedHashMap<>();
            for (PipeGasPortHost.Port port : descriptors) {
                if (port == null || moves.containsKey(port.key(pos))) return Optional.empty();
                PipeGasInventory.Entry entry = before.get(port.key(pos)).orElse(null);
                if (entry == null || !entry.active() || !entry.incarnation().equals(port.incarnation())
                        || Double.compare(entry.volume(), port.volume()) != 0) return Optional.empty();
                PipeTopology.NodeKey destination = new PipeTopology.NodeKey(pos, destinationLayer, port.id());
                moves.put(port.key(pos), new PipeGasInventory.ExpectedIdentityVersionAndDestination(
                        port.incarnation(), entry.version(), destination));
            }
            PipeGasInventory after = before.relocateBatch(moves);
            if (after == before) return Optional.empty();
            return Optional.of(LoadedPipeGasWork.preparedRawSwap(saved, before, after, () -> {
                if (PipeGasInventorySavedData.existing(level).orElse(null) != saved || saved.snapshot() != before
                        || host(level, pos).orElse(null) != host) return false;
                List<PipeGasPortHost.Port> current;
                try { current = List.copyOf(host.gasPorts()); } catch (RuntimeException invalid) { return false; }
                return current.equals(descriptors);
            }));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    public static boolean retireRemoved(ServerLevel level, BlockPos pos, PipeTopology.Layer layer,
                                         String portId, UUID expectedIncarnation) {
        LoadedPipeNetworks.invalidateTopology(level, pos);
        if (expectedIncarnation == null || layer == null || portId == null || portId.isBlank() || !validLocation(level, pos)) return false;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return false;
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pos, layer, portId);
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElse(null);
        if (entry == null || !entry.incarnation().equals(expectedIncarnation)) return false;
        try { return data.replace(before.revision(), before, before.retire(key, expectedIncarnation)); }
        catch (RuntimeException failure) { return false; }
    }

    public static Optional<PipeGasInventory.Entry> activeSnapshot(ServerLevel level, BlockPos pos, String portId) {
        PipeGasPortHost.Port port = port(level, pos, portId).orElse(null);
        if (port == null) return Optional.empty();
        return PipeGasInventorySavedData.existing(level).map(data -> data.snapshot().get(port.key(pos))
                .filter(PipeGasInventory.Entry::active).filter(e -> e.incarnation().equals(port.incarnation()))
                .filter(e -> Double.compare(e.volume(), port.volume()) == 0).orElse(null));
    }

    public static Optional<PipeGasPortHost> host(ServerLevel level, BlockPos pos) {
        if (!validLocation(level, pos)) return Optional.empty();
        LevelChunk chunk = fullChunk(level, pos);
        BlockEntity be = chunk == null ? null : chunk.getBlockEntity(pos);
        return be instanceof PipeGasPortHost h ? Optional.of(h) : Optional.empty();
    }

    private static Optional<PipeGasPortHost.Port> port(ServerLevel level, BlockPos pos, String id) {
        if (id == null || !validLocation(level, pos)) return Optional.empty();
        PipeGasPortHost host = host(level, pos).orElse(null);
        if (host == null) return Optional.empty();
        try {
            Set<String> ids = new HashSet<>();
            PipeGasPortHost.Port match = null;
            for (var candidate : host.gasPorts()) {
                if (candidate == null || !ids.add(candidate.id())) return Optional.empty();
                if (candidate.id().equals(id)) match = candidate;
            }
            return Optional.ofNullable(match);
        } catch (RuntimeException failure) { return Optional.empty(); }
    }

    private static boolean stillExact(ServerLevel level, BlockPos pos, PipeGasPortHost.Port expected) {
        return port(level, pos, expected.id()).filter(expected::equals).isPresent();
    }
    private static boolean validLocation(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.getServer().isSameThread()
                && pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && fullChunk(level, pos) != null;
    }
    private static LevelChunk fullChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false)
                instanceof LevelChunk c ? c : null;
    }
}
