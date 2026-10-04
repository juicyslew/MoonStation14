package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-thread-only adapter over one active persisted lane. */
final class PipeGasInventoryEndpoint implements PipeGasTransaction.Endpoint {
    private record Identity(ServerLevel level, PipeTopology.NodeKey node, UUID id, VisualPipeBlockEntity entity) { }
    private final ServerLevel level;
    private final BlockPos pos;
    private final int lane;
    private final PipeGasInventorySavedData data;
    private final PipeGasInventory inventoryAtCreation;
    private final PipeGasInventory.Entry initial;
    private final Identity identity;
    private final PipeTopology.Layer sealedLayer;
    private final Direction portDirection;
    private final long tick;
    private PipeGasInventorySavedData.PendingRawSwap pending;
    private boolean rolledBack;

    private PipeGasInventoryEndpoint(ServerLevel level, BlockPos pos, int lane, PipeGasInventorySavedData data,
                                     PipeGasInventory inventory, PipeGasInventory.Entry entry, VisualPipeBlockEntity entity,
                                     PipeTopology.Layer sealedLayer, Direction portDirection) {
        this.level = level; this.pos = pos.immutable(); this.lane = lane; this.data = data;
        this.inventoryAtCreation = inventory; this.initial = entry;
        this.identity = new Identity(level, entry.node(), entry.incarnation(), entity); this.tick = level.getGameTime();
        this.sealedLayer = sealedLayer; this.portDirection = portDirection;
    }

    static Optional<PipeGasInventoryEndpoint> create(ServerLevel level, BlockPos pos, int lane) {
        return create(level, pos, lane, null);
    }

    static Optional<PipeGasInventoryEndpoint> create(ServerLevel level, BlockPos pos, int lane, Direction towardDevice) {
        Objects.requireNonNull(level); Objects.requireNonNull(pos);
        if (!level.getServer().isSameThread()) return Optional.empty();
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if (data == null) return Optional.empty();
        PipeGasInventory inventory = data.snapshot();
        PipeGasInventory.Entry entry = LoadedPipeGasOwner.activeSnapshot(level, pos, lane).orElse(null);
        if (entry == null || Double.compare(entry.volume(), Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) != 0
                || inventory.get(entry.node()).orElse(null) != entry
                || !(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual)) return Optional.empty();
        if (towardDevice != null && !LoadedPipeGasOwner.activeSnapshot(level, pos, lane).isPresent()) return Optional.empty();
        return Optional.of(new PipeGasInventoryEndpoint(level, pos, lane, data, inventory, entry, visual, null, towardDevice));
    }

    static Optional<PipeGasInventoryEndpoint> createSealed(ServerLevel level, BlockPos pos,
            PipeTopology.Layer layer, Direction towardDevice) {
        if (level == null || pos == null || layer == null || towardDevice == null || !level.getServer().isSameThread()) return Optional.empty();
        PipeChunkData.Record record = LoadedSealedWallSegment.matchingLive(level, pos, layer).orElse(null);
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        PipeGasInventory inventory = data == null ? null : data.snapshot();
        PipeGasInventory.Entry entry = LoadedSealedWallGasOwner.activeSnapshot(level, pos, layer).orElse(null);
        if (record == null || entry == null || !record.id().equals(entry.incarnation())
                || entry.volume() != Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3
                || (record.ports() & (1 << towardDevice.ordinal())) == 0
                || inventory.get(entry.node()).orElse(null) != entry) return Optional.empty();
        return Optional.of(new PipeGasInventoryEndpoint(level, pos, layer.ordinal() == 0 ? VisualPipeBlockEntity.PRIMARY
                : layer.ordinal() == 1 ? VisualPipeBlockEntity.SECONDARY : VisualPipeBlockEntity.TERTIARY,
                data, inventory, entry, null, layer, towardDevice));
    }

    private Optional<PipeGasInventory.Entry> activeOwner() {
        return sealedLayer == null ? LoadedPipeGasOwner.activeSnapshot(level, pos, lane)
                : LoadedSealedWallGasOwner.activeSnapshot(level, pos, sealedLayer)
                .filter(entry -> LoadedSealedWallSegment.matchingLive(level, pos, sealedLayer)
                        .filter(record -> record.id().equals(entry.incarnation())
                                && (record.ports() & (1 << portDirection.ordinal())) != 0).isPresent());
    }

    private boolean onThread() { return level.getServer().isSameThread(); }
    @Override public Object identity() { return identity; }
    @Override public long version() { return initial.version(); }
    @Override public GasMixture snapshot() {
        return data.snapshot().get(initial.node()).map(PipeGasInventory.Entry::mixture).orElse(null);
    }
    @Override public boolean eligible() {
        if (!onThread() || level.getGameTime() != tick || level.getBlockEntity(pos) != identity.entity()) return false;
        PipeGasInventory.Entry current = data.snapshot().get(initial.node()).orElse(null);
        return current != null && current.active() && current.incarnation().equals(initial.incarnation())
                && current.version() == initial.version() && current.volume() == initial.volume()
                && activeOwner().filter(owner -> owner == current).isPresent();
    }
    @Override public boolean writeRaw(GasMixture expectedBefore, GasMixture next) {
        if (!onThread()) return false;
        if (expectedBefore == null) {
            if (pending == null) return rolledBack && data.snapshot() == inventoryAtCreation;
            if (!data.rollbackRawSwap(pending)) return false;
            pending = null; rolledBack = true; return true;
        }
        if (!eligible() || pending != null || next == null || !same(snapshot(), expectedBefore)) return false;
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry current = before.get(initial.node()).orElse(null);
        if (current == null || current.version() != initial.version()) return false;
        PipeGasInventory after;
        try { after = before.withMixture(initial.node(), initial.incarnation(), current.version(), next); }
        catch (RuntimeException failure) { return false; }
        pending = data.beginRawSwap(before, after);
        return pending != null;
    }
    @Override public void publish() {
        if (!onThread() || pending == null || level.getGameTime() != tick
                || level.getBlockEntity(pos) != identity.entity()
                || (sealedLayer == null && !(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity))
                || !matchesExpectedAfter())
            throw new PipeGasTransaction.RecoveryRequiredException("raw pipe gas publication invalidated", null, null);
        try { data.publishRawSwap(pending); pending = null; }
        catch (RuntimeException failure) {
            throw new PipeGasTransaction.RecoveryRequiredException("raw pipe gas publication invalidated", failure, null);
        }
    }
    private boolean matchesExpectedAfter() {
        PipeGasInventory.Entry current = data.snapshot().get(initial.node()).orElse(null);
        if (current == null || !current.active() || !current.incarnation().equals(initial.incarnation())
                || current.version() != initial.version() + 1 || current.volume() != initial.volume()) return false;
        return activeOwner().map(active -> active == current).orElse(false);
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (a == null || b == null || Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0) return false;
        for (GasType gas : GasType.values()) if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return true;
    }
}
