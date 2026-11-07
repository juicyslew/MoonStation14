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
    private record NetworkIdentity(ServerLevel level, UUID networkId) { }
    private final ServerLevel level;
    private final BlockPos pos;
    private final int lane;
    private final PipeGasInventorySavedData data;
    private final PipeGasInventory inventoryAtCreation;
    private final PipeGasInventory.Entry initial;
    private final PipeGasInventory.Network initialNetwork;
    private final NetworkIdentity identity;
    private final VisualPipeBlockEntity visualEntity;
    private final PipeTopology.Layer sealedLayer;
    private final Direction portDirection;
    private final long tick;
    private PipeGasInventorySavedData.PendingRawSwap pending;
    private GasMixture pendingAfter;
    private boolean rolledBack;

    private PipeGasInventoryEndpoint(ServerLevel level, BlockPos pos, int lane, PipeGasInventorySavedData data,
                                     PipeGasInventory inventory, PipeGasInventory.Entry entry, VisualPipeBlockEntity entity,
                                     PipeTopology.Layer sealedLayer, Direction portDirection) {
        this.level = level; this.pos = pos.immutable(); this.lane = lane; this.data = data;
        this.inventoryAtCreation = inventory; this.initial = entry;
        this.initialNetwork = inventory.network(entry.node()).orElseThrow();
        this.identity = new NetworkIdentity(level, initialNetwork.id()); this.visualEntity=entity; this.tick = level.getGameTime();
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
        PipeTopology.Layer layer = switch(lane) {
            case VisualPipeBlockEntity.PRIMARY -> PipeTopology.Layer.PRIMARY;
            case VisualPipeBlockEntity.SECONDARY -> PipeTopology.Layer.SECONDARY;
            case VisualPipeBlockEntity.TERTIARY -> PipeTopology.Layer.TERTIARY;
            default -> null;
        };
        if(layer==null || !LoadedPipeNetworks.reconcile(level)) return Optional.empty();
        PipeGasInventory inventory = data.snapshot();
        PipeGasInventory.Entry entry = LoadedPipeGasOwner.activeSnapshot(level, pos, lane).orElse(null);
        if (entry == null || Double.compare(entry.volume(), Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) != 0
                || inventory.get(entry.node()).orElse(null) != entry
                || !LoadedPipeNetworks.ready(level,entry.node()) || data.snapshot()!=inventory || inventory.network(entry.node()).isEmpty()
                || !(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual)) return Optional.empty();
        return Optional.of(new PipeGasInventoryEndpoint(level, pos, lane, data, inventory, entry, visual, null, towardDevice));
    }

    static Optional<PipeGasInventoryEndpoint> createSealed(ServerLevel level, BlockPos pos,
            PipeTopology.Layer layer, Direction towardDevice) {
        if (level == null || pos == null || layer == null || towardDevice == null || !level.getServer().isSameThread()) return Optional.empty();
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if(data==null || !LoadedPipeNetworks.reconcile(level)) return Optional.empty();
        PipeChunkData.Record record = LoadedSealedWallSegment.matchingLive(level, pos, layer).orElse(null);
        PipeGasInventory inventory = data.snapshot();
        PipeGasInventory.Entry entry = LoadedSealedWallGasOwner.activeSnapshot(level, pos, layer).orElse(null);
        if (record == null || entry == null || !record.id().equals(entry.incarnation())
                || entry.volume() != Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3
                || (record.ports() & (1 << towardDevice.ordinal())) == 0
                || inventory.get(entry.node()).orElse(null) != entry
                || !LoadedPipeNetworks.ready(level,entry.node()) || data.snapshot()!=inventory || inventory.network(entry.node()).isEmpty()) return Optional.empty();
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
    @Override public long version() { return initialNetwork.gasRevision(); }
    @Override public GasMixture snapshot() {
        PipeGasInventory current = data.snapshot();
        return networkCurrent(current) ? current.network(initial.node()).map(PipeGasInventory.Network::mixture).orElse(null) : null;
    }
    @Override public boolean eligible() {
        if (!onThread() || level.getGameTime() != tick || level.getBlockEntity(pos) != visualEntity
                || !LoadedPipeNetworks.ready(level,initial.node())) return false;
        PipeGasInventory.Entry current = data.snapshot().get(initial.node()).orElse(null);
        return current != null && current.active() && current.incarnation().equals(initial.incarnation())
                && current.volume() == initial.volume()
                && networkCurrent(data.snapshot())
                && activeOwner().filter(owner -> owner == current).isPresent();
    }
    @Override public boolean writeRaw(GasMixture expectedBefore, GasMixture next) {
        if (!onThread()) return false;
        if (expectedBefore == null) {
            if (pending == null) return rolledBack && data.snapshot() == inventoryAtCreation;
            if (!data.rollbackRawSwap(pending)) return false;
            pending = null; pendingAfter = null; rolledBack = true; return true;
        }
        if (!eligible() || pending != null || next == null || Double.compare(next.volumeCubicMeters(), initialNetwork.mixture().volumeCubicMeters()) != 0
                || !same(snapshot(), expectedBefore)) return false;
        PipeGasInventory before = data.snapshot();
        if (!networkCurrent(before)) return false;
        PipeGasInventory.Entry current = before.get(initial.node()).orElse(null);
        if (current == null || !current.active() || !current.incarnation().equals(initial.incarnation())) return false;
        PipeGasInventory after;
        try { after = before.withNetworkMixtures(java.util.Map.of(initial.node(), next)); }
        catch (RuntimeException failure) { return false; }
        pending = data.beginRawSwap(before, after);
        if (pending != null) pendingAfter = next;
        return pending != null;
    }
    @Override public void publish() {
        if (!onThread() || pending == null || level.getGameTime() != tick
                || level.getBlockEntity(pos) != visualEntity
                || (sealedLayer == null && !(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity))
                || !matchesExpectedAfter())
            throw new PipeGasTransaction.RecoveryRequiredException("raw pipe gas publication invalidated", null, null);
        try { data.publishRawSwap(pending); pending = null; pendingAfter = null; }
        catch (RuntimeException failure) {
            throw new PipeGasTransaction.RecoveryRequiredException("raw pipe gas publication invalidated", failure, null);
        }
    }
    private boolean matchesExpectedAfter() {
        PipeGasInventory.Entry current = data.snapshot().get(initial.node()).orElse(null);
        if (current == null || !current.active() || !current.incarnation().equals(initial.incarnation())
                || current.volume() != initial.volume()) return false;
        PipeGasInventory.Network network=data.snapshot().network(initial.node()).orElse(null);
        return network!=null && network.id().equals(initialNetwork.id())
                && network.topologyGeneration()==initialNetwork.topologyGeneration()
                && network.gasRevision()==initialNetwork.gasRevision()+1
                && network.members().equals(initialNetwork.members()) && same(network.mixture(),pendingAfter)
                && activeOwner().map(active -> active == current).orElse(false);
    }
    private boolean networkCurrent(PipeGasInventory inventory) {
        PipeGasInventory.Network current=inventory.network(initial.node()).orElse(null);
        return current!=null && current.id().equals(initialNetwork.id())
                && current.topologyGeneration()==initialNetwork.topologyGeneration()
                && current.gasRevision()==initialNetwork.gasRevision()
                && current.members().equals(initialNetwork.members());
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (a == null || b == null || Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0
                || Double.compare(a.volumeCubicMeters(), b.volumeCubicMeters()) != 0) return false;
        for (GasType gas : GasType.values()) if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return true;
    }
}

