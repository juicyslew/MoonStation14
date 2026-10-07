package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalContents;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalWorldPayloadStage;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.exposure.FiniteExposureAir;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Persisted disposal machine identity/configuration and timing adapter. Payload transport is deliberately staged. */
public final class DisposalUnitBlockEntity extends BlockEntity implements ContainedBodyOwner {
    private UUID identity = UUID.randomUUID();
    private boolean powerSwitch = true;
    private DisposalUnitTimingKernel timing;
    private DisposalUnitTimingKernel.StateSnapshot restoredTiming;
    private boolean clockRebaseWarned;
    private long lastTick;
    private FlushSink flushSink;
    private DisposalContents contents;
    private final Set<UUID> bodies = new LinkedHashSet<>();
    private final java.util.Map<UUID, ContainedBodyFlags> bodyFlags = new java.util.HashMap<>();
    private final ContainedBodyOwner owner = this;
    private CompoundTag savedContents;
    private CompoundTag savedReservoir;
    private FiniteExposureAir reservoir;
    // Package-private, unset in production; mechanics GameTests can isolate timing/transport from APC fixture setup.
    private Boolean gameTestPowerOverride;
    private boolean failNextReservoirHandoffForTest;
    private GasMixture failedHandoffReservoirSnapshotForTest;
    @FunctionalInterface public interface FlushSink { boolean flush(DisposalUnitBlockEntity unit); }

    public DisposalUnitBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.DISPOSAL_UNIT.get(), pos, state); }
    @Override public void onLoad() {
        super.onLoad();
        if (ensureRuntime() && level instanceof ServerLevel server) {
            if (com.juicyslew.moonstation14.ms14.power.PowerSimulationGate.isEnabled()) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitChanged(server, worldPosition);
            else updatePower(false);
        }
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitRemoved(server, worldPosition);
        super.setRemoved();
    }
    public UUID identity() { return identity; }
    @Override public ServerLevel level() { return level instanceof ServerLevel server ? server : null; }
    @Override public Vec3 position() { return new Vec3(worldPosition.getX() + .5, worldPosition.getY(), worldPosition.getZ() + .5); }
    @Override public com.juicyslew.moonstation14.ms14.containment.ContainedBodyView view() {
        boolean cameraCollision = resolvedData() == null || resolvedData().containedView().cameraCollision();
        return new com.juicyslew.moonstation14.ms14.containment.ContainedBodyView(identity,
                java.util.Optional.of(worldPosition.immutable()), cameraCollision);
    }
    @Override public java.util.Optional<ExposureAirSource> airSource() {
        return level instanceof ServerLevel server ? java.util.Optional.of(new DisposalUnitWorldAirSource(server, worldPosition)) : java.util.Optional.empty();
    }
    @Override public Stage stage() { return Stage.UNIT; }
    @Override public boolean isValid() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                && chunk.getBlockEntities().get(worldPosition) == this
                && chunk.getBlockState(worldPosition).getBlock() instanceof DisposalUnitBlock;
    }
    /** Idempotently initializes runtime ownership only after this exact block entity is installed in a resolved unit. */
    private boolean ensureRuntime() {
        if (!(level instanceof ServerLevel server) || isRemoved() || prototype() == null) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                || chunk.getBlockEntities().get(worldPosition) != this
                || !(chunk.getBlockState(worldPosition).getBlock() instanceof DisposalUnitBlock)) return false;
        if (contents == null) {
            contents = DisposalUnitLedger.existing(server, identity);
            if (contents == null && savedContents != null) {
                var data = DisposalContents.codec().parse(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), savedContents).result();
                contents = data.flatMap(value -> DisposalContents.decode(value, DisposalUnitLedger.forLevel(server))).orElse(null);
                if (contents != null) savedContents = null;
            }
            if (contents == null) contents = new DisposalContents(identity, DisposalUnitLedger.forLevel(server));
            contents = DisposalUnitLedger.retain(server, contents);
        }
        DisposalEntityData.Unit config = prototype();
        if (reservoir == null) {
            if (savedReservoir != null) { reservoir = decodeReservoir(savedReservoir, config); savedReservoir = null; }
            else reservoir = emptyReservoir(config);
        }
        bindSavedBodies(server);
        return true;
    }
    @Override public boolean tryEscape(LivingEntity body) {
        if (!ensureRuntime() || !(level instanceof ServerLevel server) || body == null || !bodies.contains(body.getUUID())
                || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canMove(body)) return false;
        advanceBeforeAction();
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.handIds().isEmpty() || !kernel().exitAttemptAllowed()) return false;
        return ejectBody(server, body);
    }
    public DisposalContents contents() { ensureRuntime(); return contents; }
    public FiniteExposureAir reservoirAir() { ensureRuntime(); return reservoir; }
    public DisposalUnitTimingKernel.StateSnapshot timingSnapshot() { return kernel().snapshot(); }
    public Set<UUID> bodyIds() { ensureRuntime(); return Set.copyOf(bodies); }
    public boolean insertBody(LivingEntity body) {
        if (!ensureRuntime()) return false;
        advanceBeforeAction();
        if (!(level instanceof ServerLevel server) || !isValid() || body == null || body.level() != server
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || server.getEntity(body.getUUID()) != body
                || bodies.contains(body.getUUID())) return false;
        if (!BodyContainmentService.forLevel(server).bind(body, owner)) return false;
        BodyContainmentService.forLevel(server).export(body, owner).ifPresent(flags -> bodyFlags.put(body.getUUID(), flags));
         bodies.add(body.getUUID()); kernel().insert(1); setChanged();
          com.juicyslew.moonstation14.ms14.disposal.DisposalAudio.playInsert(server, worldPosition, prototype());
         return true;
    }
    public boolean acceptWorldItem(ItemEntity source) {
        if (!ensureRuntime()) return false;
        advanceBeforeAction();
        if (!(level instanceof ServerLevel server) || contents == null || source == null || source.isRemoved()
                || source.level() != server || !source.isAddedToLevel() || server.getEntity(source.getUUID()) != source
                || source.getItem().isEmpty()) return false;
        ItemStack stack = source.getItem().copy(); ItemToken token = new ItemToken(UUID.randomUUID().toString());
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(server);
        var registered = ledger.register(token, new ItemLocation.WorldEntity(worldIdentity(server), source.getUUID()));
        if (!registered.accepted()) return false;
        ItemLocation sourceLocation = registered.snapshot().locations().get(token);
        source.setItem(ItemStack.EMPTY);
        if (!contents.accept(token, sourceLocation, UUID.randomUUID().toString(), stack, ledger.snapshot().revision())) {
            source.setItem(stack); ledger.consume(ledger.snapshot().revision(), token, sourceLocation); return false;
        }
         source.discard(); kernel().insert(1); setChanged();
         com.juicyslew.moonstation14.ms14.disposal.DisposalAudio.playInsert(server, worldPosition, prototype());
         return true;
    }
    public boolean completeBodyAdmission(ItemToken token, ItemLocation source, ItemStack stack) {
        if (!ensureRuntime()) return false;
        advanceBeforeAction();
        if (contents == null || !(level instanceof ServerLevel server)) return false;
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(server);
        if (!ledger.register(token, source).accepted()) return false;
        boolean accepted = contents.accept(token, source, UUID.randomUUID().toString(), stack, ledger.snapshot().revision());
        if (!accepted) ledger.consume(ledger.snapshot().revision(), token, source);
        if (accepted) {
            kernel().insert(1);
            com.juicyslew.moonstation14.ms14.disposal.DisposalAudio.playInsert(server, worldPosition, prototype());
        }
        return accepted;
    }
    @Override public boolean acceptDroppedItem(ItemToken token, ItemLocation source, ItemStack stack) {
        if (!ensureRuntime() || contents == null || !(level instanceof ServerLevel)) return false;
        if (!contents.acceptDropped(token, source, stack)) return false;
        kernel().insert(1);
        setChanged();
        return true;
    }
    /** Atomically accepts a holder returning through this unit's exact same-floor trunk, then ejects immediately. */
    public boolean receiveReturnedHolder(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder holder) {
        if (!ensureRuntime() || !(level instanceof ServerLevel server) || holder == null || holder.level() != server || !isValid() || !anchored()
                || contents == null || !worldPosition.below().equals(holder.currentPos())) return false;
        // A live unit is the terminal destination; a disconnected exit trunk does not revoke
        // its ability to receive the exact holder at its below-cell.
        var sourceContents = holder.contents();
        if (sourceContents == null || sourceContents.ownerId().equals(identity)) return false;
        var containment = BodyContainmentService.forLevel(server);
        var sourceOwner = holder.containmentOwner();
        Set<String> entryIds = sourceContents.snapshot().keySet();
        Set<UUID> bodyIds = holder.bodyIds();
        Map<UUID, LivingEntity> liveBodies = new java.util.LinkedHashMap<>();
        Map<UUID, ContainedBodyFlags> originals = new java.util.HashMap<>();
        for (UUID id : bodyIds) {
            if (!(server.getEntity(id) instanceof LivingEntity body) || body.isRemoved() || !body.isAlive()
                    || containment.ownerOf(body).orElse(null) != sourceOwner) return false;
            liveBodies.put(id, body);
            ContainedBodyFlags flags = containment.export(body, sourceOwner).orElse(null);
            if (flags == null) return false;
            originals.put(id, flags);
        }
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(server);
        java.util.List<Map.Entry<String, String>> movedItems = new java.util.ArrayList<>();
        java.util.List<UUID> movedBodies = new java.util.ArrayList<>();
        for (String sourceEntry : entryIds) {
            String destinationEntry = UUID.randomUUID().toString();
            if (sourceContents.transferTo(sourceEntry, contents, destinationEntry, ledger.snapshot().revision()).isEmpty()) {
                rollbackReturned(holder, sourceContents, movedItems, movedBodies, liveBodies, ledger, containment, sourceOwner);
                return false;
            }
            movedItems.add(Map.entry(sourceEntry, destinationEntry));
        }
        for (var entry : liveBodies.entrySet()) {
            UUID id = entry.getKey(); LivingEntity body = entry.getValue();
            if (containment.ownerOf(body).orElse(null) != sourceOwner || !containment.transfer(body, sourceOwner, owner)) {
                rollbackReturned(holder, sourceContents, movedItems, movedBodies, liveBodies, ledger, containment, sourceOwner);
                return false;
            }
            bodies.add(id); bodyFlags.put(id, originals.get(id)); movedBodies.add(id);
        }
        for (UUID id : movedBodies) holder.removeBody(id);
        setChanged();
        // Spatial ejection is best-effort in Minecraft; accepted payloads remain owned by this live unit if blocked.
        ejectAll();
        return true;
    }
    public boolean canReceiveReturnedHolderAt(BlockPos below) {
        return isValid() && anchored() && contents != null && worldPosition.below().equals(below);
    }
    static boolean validUnitTrunk(ServerLevel server, BlockPos trunkPos) {
        var route = new com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting(server);
        var trunk = route.record(trunkPos);
        return trunk != null && trunk.tube().kind() == DisposalEntityData.Kind.TRUNK
                && !com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting
                .worldConnections(trunk.tube(), trunk.frame()).contains(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection.UP);
    }
    private void rollbackReturned(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder holder,
            DisposalContents source, java.util.List<Map.Entry<String, String>> items, java.util.List<UUID> movedBodies,
            java.util.Map<UUID, LivingEntity> liveBodies, ItemLocationLedger ledger,
            BodyContainmentService containment, ContainedBodyOwner sourceOwner) {
        for (int i = movedBodies.size() - 1; i >= 0; i--) {
            UUID id = movedBodies.get(i); LivingEntity body = liveBodies.get(id);
            if (body != null && containment.transfer(body, owner, sourceOwner)) { bodies.remove(id); bodyFlags.remove(id); }
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            var moved = items.get(i);
            contents.transferTo(moved.getValue(), source, moved.getKey(), ledger.snapshot().revision());
        }
    }
    private boolean ejectBody(ServerLevel server, LivingEntity body) { return ejectBody(server, body, false); }
    private boolean ejectBody(ServerLevel server, LivingEntity body, boolean unitCellFreed) {
        Vec3 destination = safeEjection(server, body, unitCellFreed);
        if (destination == null) return false;
        if (!BodyContainmentService.forLevel(server).unbind(body, owner)) return false;
        bodies.remove(body.getUUID()); bodyFlags.remove(body.getUUID()); body.teleportTo(destination.x, destination.y, destination.z); body.setDeltaMovement(Vec3.ZERO); setChanged(); return true;
    }
    private Vec3 safeEjection(ServerLevel server, LivingEntity body, boolean unitCellFreed) {
        for (BlockPos p : ejectionCandidates(server, unitCellFreed)) {
            Vec3 v = new Vec3(p.getX() + .5, p.getY(), p.getZ() + .5);
            if (server.noCollision(body, body.getBoundingBox().move(v.subtract(body.position())))) return v;
        }
        return null;
    }
    private java.util.List<BlockPos> ejectionCandidates(ServerLevel server, boolean unitCellFreed) {
        java.util.LinkedHashSet<BlockPos> candidates = new java.util.LinkedHashSet<>();
        if (unitCellFreed && server.hasChunkAt(worldPosition) && server.getBlockState(worldPosition).getCollisionShape(server, worldPosition).isEmpty()) candidates.add(worldPosition);
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos p = worldPosition.relative(direction);
            if (server.hasChunkAt(p) && server.getBlockState(p).getCollisionShape(server, p).isEmpty()) candidates.add(p);
        }
        for (int radius = 2; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            BlockPos p = worldPosition.offset(dx, 0, dz);
            if (server.hasChunkAt(p) && server.getBlockState(p).getCollisionShape(server, p).isEmpty()) candidates.add(p);
        }
        return java.util.List.copyOf(candidates);
    }
    private boolean canEjectAll() {
        if (!(level instanceof ServerLevel server)) return false;
        for (UUID id : bodies) if (server.getEntity(id) instanceof LivingEntity body && safeEjection(server, body, false) == null) return false;
        return contents == null || contents.snapshot().isEmpty() || !ejectionCandidates(server, false).isEmpty();
    }
    private static UUID worldIdentity(ServerLevel server) {
        return UUID.nameUUIDFromBytes(server.dimension().location().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static FiniteExposureAir emptyReservoir(DisposalEntityData.Unit config) {
        return new FiniteExposureAir(new GasMixture(java.util.Map.of(), 293.15), config.airLiters() / 1000.0);
    }
    private static CompoundTag encodeReservoir(GasMixture mixture) {
        CompoundTag tag = new CompoundTag(); tag.putDouble("Temperature", mixture.temperatureKelvin());
        CompoundTag gases = new CompoundTag(); mixture.gasMoles().forEach((gas, amount) -> gases.putDouble(gas.name(), amount)); tag.put("Gases", gases); return tag;
    }
    private static FiniteExposureAir decodeReservoir(CompoundTag tag, DisposalEntityData.Unit config) {
        java.util.EnumMap<GasType, Double> gases = new java.util.EnumMap<>(GasType.class);
        CompoundTag saved = tag.getCompound("Gases");
        for (String key : saved.getAllKeys()) try { gases.put(GasType.valueOf(key), saved.getDouble(key)); } catch (IllegalArgumentException ignored) { }
        try { return new FiniteExposureAir(new GasMixture(gases, tag.getDouble("Temperature")), config.airLiters() / 1000.0); }
        catch (RuntimeException invalid) { return emptyReservoir(config); }
    }
    public void ejectAll() { ejectAll(false); }
    @Override public void preserveContentsBeforeExplosion() { ejectAll(); }
    public void ejectAll(boolean unitCellFreed) {
        if (!(level instanceof ServerLevel server)) return;
        for (UUID id : Set.copyOf(bodies)) if (server.getEntity(id) instanceof LivingEntity body) ejectBody(server, body, unitCellFreed);
        if (contents == null) return;
        for (String entry : Set.copyOf(contents.snapshot().keySet())) {
            DisposalContents.Payload payload = contents.snapshot().get(entry);
            for (BlockPos candidate : ejectionCandidates(server, unitCellFreed)) {
                var staged = DisposalWorldPayloadStage.stage(server, payload.stack(),
                        new net.minecraft.world.phys.Vec3(candidate.getX() + .5, candidate.getY() + .1, candidate.getZ() + .5),
                        net.minecraft.world.phys.Vec3.ZERO).orElse(null);
                if (staged == null) continue;
                ItemLocation.WorldEntity destination = staged.location();
                var moved = contents.removeForEjection(entry, destination, DisposalUnitLedger.forLevel(server).snapshot().revision());
                if (moved.isEmpty()) {
                    staged.rollback();
                    continue;
                }
                setChanged();
                break;
            }
        }
    }
    /** Transfer exact owned reservoir before a block replacement; failure leaves it owned for caller retry. */
    public boolean handoffReservoirOnRemoval() {
        if (!(level instanceof ServerLevel server)) return false;
        ensureReservoir();
        if (reservoir == null) return true;
        GasMixture before = reservoir.sample().orElseThrow().mixture();
        if (before.totalMoles() <= 0) return true;
        GasMixture empty = new GasMixture(java.util.Map.of(), before.temperatureKelvin());
        var carrier = com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder
                .spawnRemovalAir(server, worldPosition, before);
        if (carrier.isEmpty()) {
            var data = com.juicyslew.moonstation14.ms14.disposal.transport.DisposalPendingAirSavedData.forMutation(server);
            var record = new com.juicyslew.moonstation14.ms14.disposal.transport.DisposalPendingAirSavedData.Record(identity, worldPosition, before);
            if (!data.put(record)) return false;
            if (!reservoir.replaceInventory(before, empty)) { data.remove(identity, record); return false; }
        } else if (!reservoir.replaceInventory(before, empty)) { carrier.get().discard(); return false; }
        setChanged();
        return true;
    }
    private void bindSavedBodies(ServerLevel server) {
        for (UUID id : Set.copyOf(bodies)) if (server.getEntity(id) instanceof LivingEntity body) {
            ContainedBodyFlags flags = bodyFlags.get(id);
            if (flags != null && BodyContainmentService.forLevel(server).bindRestored(body, owner, flags)) { }
        }
    }
    public DisposalEntityData.Unit prototype() {
        DisposalEntityData data = resolvedData();
        return data == null ? null : data.unit();
    }
    private DisposalEntityData resolvedData() {
        if (level == null || !(getBlockState().getBlock() instanceof DisposalUnitBlock)) return null;
        DisposalEntityData data = (level.isClientSide ? PrototypeRuntime.clientDisposals() : PrototypeRuntime.serverDisposals()).get(ModDisposals.UNIT);
        return data;
    }
    public boolean powerSwitch() { return powerSwitch; }
    public void setPowerSwitch(boolean value) { if (powerSwitch != value) { advanceBeforeAction(); powerSwitch = value; projectFreshPower(); if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitChanged(server, worldPosition); setChanged(); } }
    public boolean anchored() { return getBlockState().getValue(DisposalUnitBlock.ANCHORED); }
    public boolean setAnchored(boolean value) { if (level == null || level.isClientSide) return false; advanceBeforeAction(); if (!value) { if (!canEjectAll()) return false; ejectAll(); } level.setBlock(worldPosition, getBlockState().setValue(DisposalUnitBlock.ANCHORED, value), 3); kernel().setAnchored(value); return true; }
    /** Caller signal is advisory only; the projection always re-reads the exact fresh receiver meter. */
    public void updatePower(boolean requestedPowered) { if (level != null && !level.isClientSide) { advanceBeforeAction(); projectFreshPower(); setChanged(); } }
    public void engage() { if (level != null && !level.isClientSide) { advanceBeforeAction(); kernel().manualEngage(); setChanged(); } }
    /** Binding is explicit; an unbound sink prevents the timing kernel from consuming a scheduled flush. */
    public void bindFlushSink(FlushSink sink) { flushSink = sink; }
    public boolean flushBound() { return flushSink != null; }
    private boolean flush(ServerLevel server) {
        if (!ensureRuntime()) return false;
        ensureReservoir();
        if (!isValid() || !anchored() || !freshPowered() || !powerSwitch) return false;
        var routing = new com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting(server);
        var entry = routing.record(worldPosition.below());
        if (entry == null || !validUnitTrunk(server, worldPosition.below()) || contents == null || reservoir == null) return false;
        GasMixture flushedAir = DisposalUnitFlushGas.prepare(server, this);
        java.util.List<LivingEntity> containedBodies = bodies.stream().map(server::getEntity)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
        if (containedBodies.size() != bodies.size()) return false;
        GasMixture reservoirBeforeHandoff = reservoir.sample().orElseThrow().mixture();
        GasMixture empty = new GasMixture(java.util.Map.of(), reservoirBeforeHandoff.temperatureKelvin());
        if (failNextReservoirHandoffForTest) {
            failNextReservoirHandoffForTest = false;
            failedHandoffReservoirSnapshotForTest = reservoirBeforeHandoff;
            // Simulates an intervening inventory writer so the compare-and-replace reserve fails before ownership moves.
            reservoir.replaceInventory(reservoirBeforeHandoff,
                    new GasMixture(reservoirBeforeHandoff.gasMoles(), reservoirBeforeHandoff.temperatureKelvin()));
        }
        if (!reservoir.replaceInventory(reservoirBeforeHandoff, empty)) return false;
        java.util.Optional<com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder> spawned;
        try {
            spawned = com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder.spawnFlushed(server,
                    worldPosition.below(), flushedAir, contents, java.util.List.copyOf(contents.snapshot().keySet()), containedBodies, owner);
        } catch (RuntimeException rejected) {
            restoreReservoirAfterRejectedHandoff(empty, reservoirBeforeHandoff);
            return false;
        }
        if (spawned.isEmpty()) {
            restoreReservoirAfterRejectedHandoff(empty, reservoirBeforeHandoff);
            return false;
        }
        com.juicyslew.moonstation14.ms14.disposal.DisposalAudio.playFlush(server, worldPosition);
        bodies.clear(); bodyFlags.clear();
        setChanged(); return true;
    }
    private void restoreReservoirAfterRejectedHandoff(GasMixture expectedEmpty, GasMixture ownedGas) {
        GasMixture current = reservoir.sample().orElseThrow().mixture();
        if (!reservoir.replaceInventory(expectedEmpty, ownedGas) && current != expectedEmpty)
            reservoir.replaceInventory(current, ownedGas);
    }
    void failNextReservoirHandoffForTest() { failNextReservoirHandoffForTest = true; }
    boolean attemptFlushForGameTest() { return level instanceof ServerLevel server && flush(server); }
    GasMixture failedHandoffReservoirSnapshotForTest() { return failedHandoffReservoirSnapshotForTest; }
    private DisposalUnitTimingKernel kernel() {
        if (!ensureRuntime()) throw new IllegalStateException("disposal unit runtime unavailable before exact block entity installation");
        DisposalEntityData.Unit config = prototype();
        if (config == null) throw new IllegalStateException("resolved disposal unit prototype unavailable");
        if (timing == null) {
            if (restoredTiming != null) {
                try {
                    timing = DisposalUnitTimingKernel.restore(config, restoredTiming);
                    rebaseClock(level instanceof ServerLevel server ? server.getGameTime() : lastTick);
                }
                catch (RuntimeException invalid) { restoredTiming = null; }
            }
            if (timing == null) {
                long now = level instanceof ServerLevel server ? server.getGameTime() : lastTick;
                lastTick = now;
                timing = new DisposalUnitTimingKernel(config, now); timing.setAnchored(anchored()); timing.setPowered(freshPowered() && powerSwitch);
                if (contents != null && (!contents.snapshot().isEmpty() || !bodies.isEmpty())) timing.insert(1);
            }
            restoredTiming = null;
        }
        return timing;
    }
    private boolean freshPowered() {
        if (gameTestPowerOverride != null) return gameTestPowerOverride;
        if (!(level instanceof ServerLevel server) || !com.juicyslew.moonstation14.ms14.power.PowerSimulationGate.isEnabled()) return false;
        DisposalEntityData.Unit config = prototype();
        var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitMeter(server, this);
        return config != null && meter.known() && meter.deliveredWatts() == config.powerWatts();
    }
    private void rebaseClock(long worldTick) {
        long previous = timing.nowTick();
        if (worldTick >= previous) return;
        timing.rebaseClock(worldTick);
        lastTick = worldTick;
        if (!clockRebaseWarned) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                    "Rebased disposal unit timing clock at {} from logical tick {} to world tick {}",
                    worldPosition, previous, worldTick);
            clockRebaseWarned = true;
        }
    }
    void setGameTestPowerOverride(Boolean powered) { gameTestPowerOverride = powered; }
    /** Test-only isolated power projection is not a production electrical load. */
    public boolean participatesInPowerLoad() { return participatesInPowerLoad(powerSwitch, gameTestPowerOverride); }
    public static boolean participatesInPowerLoad(boolean powerSwitch, Boolean gameTestPowerOverride) {
        return powerSwitch && gameTestPowerOverride == null;
    }
    private void projectFreshPower() {
        if (!(level instanceof ServerLevel)) return;
        boolean powered = freshPowered() && powerSwitch;
        if (getBlockState().getValue(DisposalUnitBlock.POWERED) != powered)
            level.setBlock(worldPosition, getBlockState().setValue(DisposalUnitBlock.POWERED, powered), 3);
        kernel().setPowered(powered);
    }
    private void advanceBeforeAction() {
        if (ensureRuntime() && level instanceof ServerLevel server) serverTick(server, worldPosition, getBlockState(), this);
    }
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, DisposalUnitBlockEntity be) {
        if (!be.ensureRuntime()) return;
        be.ensureReservoir();
        be.lastTick = level.getGameTime();
        DisposalUnitTimingKernel kernel = be.kernel();
        be.rebaseClock(be.lastTick);
        kernel.setAnchored(state.getValue(DisposalUnitBlock.ANCHORED));
        boolean powered = be.freshPowered() && be.powerSwitch;
        if (level.getBlockState(pos).getValue(DisposalUnitBlock.POWERED) != powered)
            level.setBlock(pos, level.getBlockState(pos).setValue(DisposalUnitBlock.POWERED, powered), 3);
        kernel.setPowered(powered);
        if (be.flushSink == null) be.bindFlushSink(unit -> unit.flush(level));
        var routing = new com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting(level);
        var entry = routing.record(pos.below());
        boolean trunk = entry != null && validUnitTrunk(level, pos.below());
        var result = kernel.advanceTo(be.lastTick, trunk);
        if (result == DisposalUnitTimingKernel.FlushResult.FLUSH_REQUESTED) {
            if (be.flushSink == null || !be.flushSink.flush(be)) {
                if (be.contents != null && (!be.contents.snapshot().isEmpty() || !be.bodies.isEmpty())) kernel.insert(1);
                be.setChanged();
            }
            // This staged runtime has no payload owner; do not represent empty sink as completed transport.
        }
        BlockState projectedState = level.getBlockState(pos);
        DisposalUnitBlock.VisualState visual = !projectedState.getValue(DisposalUnitBlock.POWERED)
                ? DisposalUnitBlock.VisualState.UNPOWERED
                : switch (kernel.currentState()) {
                    case READY -> DisposalUnitBlock.VisualState.READY;
                    case PRESSURIZING -> DisposalUnitBlock.VisualState.CHARGING;
                    case FLUSHED -> DisposalUnitBlock.VisualState.FLUSHED;
                };
        if (state.getValue(DisposalUnitBlock.VISUAL_STATE) != visual)
            level.setBlock(pos, level.getBlockState(pos).setValue(DisposalUnitBlock.VISUAL_STATE, visual), 3);
        be.bindSavedBodies(level);
        if (be.contents != null) {
            boolean[] detonate = {false};
            be.contents.tick(level, level.getGameTime(), stack ->
                    PrototypeRuntime.serverTools().get(com.juicyslew.moonstation14.ms14.construction.tool.ModTools.WELDER), expired -> detonate[0] = true);
            if (detonate[0]) {
                be.ejectAll();
                com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService.detonate(level, pos);
            }
        }
        be.setChanged();
    }
    private void ensureReservoir() {
        if (reservoir != null || prototype() == null) return;
        if (savedReservoir != null) { reservoir = decodeReservoir(savedReservoir, prototype()); savedReservoir = null; }
        else reservoir = emptyReservoir(prototype());
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putUUID("DisposalUnitId", identity); tag.putBoolean("PowerSwitch", powerSwitch); tag.putLong("LastTick", lastTick);
        net.minecraft.nbt.ListTag savedBodies = new net.minecraft.nbt.ListTag(); bodies.forEach(id -> savedBodies.add(net.minecraft.nbt.StringTag.valueOf(id.toString()))); tag.put("Bodies", savedBodies);
        net.minecraft.nbt.ListTag flagsTag = new net.minecraft.nbt.ListTag();
        bodyFlags.forEach((id, flags) -> { CompoundTag saved = new CompoundTag(); saved.putUUID("Body", id); saved.putBoolean("Invisible", flags.invisible()); saved.putBoolean("NoPhysics", flags.noPhysics()); saved.putBoolean("NoGravity", flags.noGravity()); flagsTag.add(saved); });
        tag.put("BodyOriginalFlags", flagsTag);
        tag.putBoolean("Anchored", anchored()); tag.putBoolean("Powered", getBlockState().getValue(DisposalUnitBlock.POWERED));
        DisposalUnitTimingKernel.StateSnapshot snapshot = timing == null ? restoredTiming : timing.snapshot();
        if (snapshot != null) {
            tag.putLong("TimingNow", snapshot.logicalTick()); tag.putBoolean("TimingEngaged", snapshot.engaged()); tag.putBoolean("TimingPowered", snapshot.powered());
            tag.putBoolean("TimingAnchored", snapshot.anchored()); tag.putLong("TimingPressure", snapshot.pressureDeadlineTick()); tag.putLong("TimingVisual", snapshot.visualDeadlineTick());
            if (snapshot.nextFlushTick() != null) tag.putLong("TimingFlush", snapshot.nextFlushTick());
            if (snapshot.lastExitAttemptTick() != null) tag.putLong("TimingExit", snapshot.lastExitAttemptTick());
        }
        if (contents != null && level instanceof ServerLevel server) {
            DisposalContents.codec().encodeStart(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), contents.encodedData())
                    .result().ifPresent(value -> tag.put("Contents", value));
        } else if (savedContents != null) tag.put("Contents", savedContents.copy());
        if (reservoir != null) tag.put("Reservoir", encodeReservoir(reservoir.sample().orElseThrow().mixture()));
        else if (savedReservoir != null) tag.put("Reservoir", savedReservoir.copy());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); if (tag.hasUUID("DisposalUnitId")) identity = tag.getUUID("DisposalUnitId");
        powerSwitch = !tag.contains("PowerSwitch") || tag.getBoolean("PowerSwitch"); lastTick = tag.getLong("LastTick");
        if (tag.contains("Contents", net.minecraft.nbt.Tag.TAG_COMPOUND)) savedContents = tag.getCompound("Contents").copy();
        if (tag.contains("Reservoir", net.minecraft.nbt.Tag.TAG_COMPOUND)) savedReservoir = tag.getCompound("Reservoir").copy();
        bodies.clear(); if (tag.contains("Bodies", net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList("Bodies", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) try { bodies.add(UUID.fromString(list.getString(i))); } catch (IllegalArgumentException ignored) { }
        }
        bodyFlags.clear(); if (tag.contains("BodyOriginalFlags", net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList("BodyOriginalFlags", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag saved = list.getCompound(i);
                if (saved.hasUUID("Body")) bodyFlags.put(saved.getUUID("Body"), new ContainedBodyFlags(saved.getBoolean("Invisible"), saved.getBoolean("NoPhysics"), saved.getBoolean("NoGravity")));
            }
        }
        if (tag.contains("TimingNow")) restoredTiming = new DisposalUnitTimingKernel.StateSnapshot(tag.getLong("TimingNow"), tag.getBoolean("TimingEngaged"),
                tag.getBoolean("TimingPowered"), tag.getBoolean("TimingAnchored"), tag.getLong("TimingPressure"),
                tag.contains("TimingFlush") ? tag.getLong("TimingFlush") : null, tag.getLong("TimingVisual"),
                tag.contains("TimingExit") ? tag.getLong("TimingExit") : null);
    }
}
