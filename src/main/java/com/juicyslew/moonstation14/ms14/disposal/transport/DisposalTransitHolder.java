package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.ms14.atmos.exposure.FiniteExposureAir;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalContents;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalWorldPayloadStage;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitLedger;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;

import java.util.*;

/** Server-owned transit identity; payload admission/ejection remains delegated to the real stores. */
public final class DisposalTransitHolder extends Entity {
    private double segmentSeconds;
    private UUID identity = UUID.randomUUID();
    private final Set<UUID> bodies = new LinkedHashSet<>();
    private final Map<UUID, com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags> originalBodyFlags = new LinkedHashMap<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private DisposalContents contents;
    private FiniteExposureAir air;
    private BlockPosState previous;
    private BlockPosState current;
    private net.minecraft.core.Direction lastDirection;
    private net.minecraft.core.BlockPos savedCurrentPos, savedPreviousPos;
    private UUID savedCurrentId, savedPreviousId;
    private Vec3 segmentFrom, segmentTo;
    private int ticksRemaining;
    private boolean exited;
    private boolean pendingRelease;
    private boolean pendingUnitReturn;
    private TransitCallbacks callbacks;
    private final ContainedBodyOwner owner = new TransitOwner();

    public DisposalTransitHolder(EntityType<? extends DisposalTransitHolder> type, Level level) { super(type, level); }

    /** Create only from a resolved, anchored entry segment; supplied air is the flush sample, not unit reservoir. */
    public static Optional<DisposalTransitHolder> spawn(ServerLevel level, BlockPosState entry,
            com.juicyslew.moonstation14.ms14.atmos.core.GasMixture air, TransitCallbacks callbacks) {
        Objects.requireNonNull(level); Objects.requireNonNull(entry); Objects.requireNonNull(air); Objects.requireNonNull(callbacks);
        if (!callbacks.anchoredResolvedTube(entry.pos())) return Optional.empty();
        var composition = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.HOLDER);
        if (composition == null || composition.holder() == null) return Optional.empty();
        var holder = new DisposalTransitHolder(ModEntities.DISPOSAL_TRANSIT_HOLDER.get(), level);
        holder.current = entry; holder.callbacks = callbacks; holder.segmentSeconds = composition.holder().segmentSeconds();
        holder.contents = new DisposalContents(holder.identity, DisposalUnitLedger.forLevel(level));
        holder.ticksRemaining = Math.max(1, (int)Math.round(holder.segmentSeconds * 20));
        holder.air = new FiniteExposureAir(air, composition.holder().fallbackAirLiters() / 1000.0);
        holder.setPos(entry.pos().getX() + .5, entry.pos().getY() + .5, entry.pos().getZ() + .5);
        return level.addFreshEntity(holder) ? Optional.of(holder) : Optional.empty();
    }

    /** Unit flush seam: caller passes the already-sampled finite gas and resolved holder volume. */
    public static Optional<DisposalTransitHolder> spawnFlushed(ServerLevel level, net.minecraft.core.BlockPos entry,
            com.juicyslew.moonstation14.ms14.atmos.core.GasMixture sampledAir) {
        var routing = new LoadedWorldDisposalRouting(level);
        var resolvedEntry = routing.record(entry);
        return resolvedEntry == null ? Optional.empty() : spawn(level, resolvedEntry, sampledAir, routing);
    }

    /** Gas-only durable carrier used when its unit owner is actually removed. */
    public static Optional<DisposalTransitHolder> spawnRemovalAir(ServerLevel level, BlockPos floor,
            com.juicyslew.moonstation14.ms14.atmos.core.GasMixture gas) {
        Objects.requireNonNull(level); Objects.requireNonNull(floor); Objects.requireNonNull(gas);
        var composition = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.HOLDER);
        if (composition == null || composition.holder() == null || !level.hasChunkAt(floor)) return Optional.empty();
        var holder = new DisposalTransitHolder(ModEntities.DISPOSAL_TRANSIT_HOLDER.get(), level);
        holder.segmentSeconds = composition.holder().segmentSeconds();
        holder.savedCurrentPos = floor.immutable();
        holder.callbacks = new LoadedWorldDisposalRouting(level);
        holder.air = new FiniteExposureAir(gas, composition.holder().fallbackAirLiters() / 1000.0);
        holder.exited = true; holder.pendingRelease = true;
        holder.setPos(floor.getX() + .5, floor.getY() + .5, floor.getZ() + .5);
        return level.addFreshEntity(holder) ? Optional.of(holder) : Optional.empty();
    }

    /** Atomically admits an existing unit's whole payload and exact bodies before the caller clears its store. */
    public static Optional<DisposalTransitHolder> spawnFlushed(ServerLevel level, BlockPos entry,
            com.juicyslew.moonstation14.ms14.atmos.core.GasMixture sampledAir, DisposalContents sourceContents,
            Collection<String> sourceEntryIds, Collection<LivingEntity> sourceBodies, ContainedBodyOwner sourceOwner) {
        Objects.requireNonNull(sourceContents); Objects.requireNonNull(sourceEntryIds); Objects.requireNonNull(sourceBodies); Objects.requireNonNull(sourceOwner);
        Set<String> uniqueEntries = new HashSet<>(sourceEntryIds);
        if (uniqueEntries.size() != sourceEntryIds.size() || !sourceContents.snapshot().keySet().containsAll(uniqueEntries)) return Optional.empty();
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        Set<UUID> uniqueBodies = new HashSet<>();
        Map<UUID, com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags> flags = new LinkedHashMap<>();
        for (LivingEntity body : sourceBodies) {
            if (body == null || !uniqueBodies.add(body.getUUID()) || body.level() != level || body.isRemoved() || !body.isAlive()
                    || containment.ownerOf(body).orElse(null) != sourceOwner) return Optional.empty();
            var original = containment.export(body, sourceOwner);
            if (original.isEmpty()) return Optional.empty();
            flags.put(body.getUUID(), original.get());
        }
        var holderOpt = spawnFlushed(level, entry, sampledAir);
        if (holderOpt.isEmpty()) return Optional.empty();
        DisposalTransitHolder holder = holderOpt.get();
        holder.originalBodyFlags.putAll(flags);
        List<Map.Entry<String, String>> movedItems = new ArrayList<>();
        List<LivingEntity> movedBodies = new ArrayList<>();
        ItemLocationLedger ledger = DisposalUnitLedger.forLevel(level);
        for (String sourceId : sourceEntryIds) {
            String targetId = UUID.randomUUID().toString();
            if (sourceContents.transferTo(sourceId, holder.contents, targetId, ledger.snapshot().revision()).isEmpty()) {
                rollbackItems(sourceContents, holder, movedItems, ledger); holder.discard(); return Optional.empty();
            }
            movedItems.add(Map.entry(sourceId, targetId));
        }
        for (LivingEntity body : sourceBodies) {
            if (body == null || containment.ownerOf(body).orElse(null) != sourceOwner || !holder.addBody(level, body, sourceOwner)) {
                for (LivingEntity moved : movedBodies) { containment.transfer(moved, holder.owner, sourceOwner); holder.removeBody(moved.getUUID()); }
                rollbackItems(sourceContents, holder, movedItems, ledger); holder.discard(); return Optional.empty();
            }
            movedBodies.add(body);
        }
        return Optional.of(holder);
    }

    private static void rollbackItems(DisposalContents source, DisposalTransitHolder holder,
                                      List<Map.Entry<String, String>> moved, ItemLocationLedger ledger) {
        ListIterator<Map.Entry<String, String>> it = moved.listIterator(moved.size());
        while (it.hasPrevious()) { var m = it.previous(); holder.contents.transferTo(m.getValue(), source, m.getKey(), ledger.snapshot().revision()); }
    }

    /** Advances exactly one fifth-second slice: each segment consumes two 20 TPS ticks. */
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || (exited && !pendingRelease) || callbacks == null) return;
        pinBodies();
        tickOwnedContents();
        if (pendingRelease) {
            if (pendingUnitReturn && callbacks.tryReturnToUnit(this, currentPos())) {
                pendingUnitReturn = false;
                if (callbacks.releaseAir(this, currentPos())) { pendingRelease = false; callbacks.exit(this, currentPos()); discard(); }
                return;
            }
            if (pendingUnitReturn && callbacks.deferToUnitReturn(currentPos())) return;
            pendingUnitReturn = false;
            if (ejectBodies(currentPos(), lastDirection) && callbacks.releaseAir(this, currentPos())) { pendingRelease = false; callbacks.exit(this, currentPos()); discard(); }
            return;
        }
        if (current == null) {
            BlockPos lookupPos = savedCurrentPos == null ? blockPosition() : savedCurrentPos;
            if (!callbacks.loaded(lookupPos)) return; // Unknown/unloaded is not permission to load the chunk.
            current = callbacks.record(lookupPos);
            if (current == null) { exit(lookupPos, lastDirection); return; }
            if (savedCurrentId != null && !savedCurrentId.equals(current.recordId())) { exit(lookupPos, lastDirection); return; }
            savedCurrentPos = null; savedCurrentId = null;
        }
        if (previous == null && savedPreviousPos != null) {
            previous = callbacks.record(savedPreviousPos);
            if (previous == null || savedPreviousId != null && !savedPreviousId.equals(previous.recordId()))
                previous = new BlockPosState(savedPreviousPos, savedPreviousId, null);
        }
        if (callbacks.disconnected(current.pos())) { exit(current.pos(), null); return; }
        if (segmentFrom != null && segmentTo != null) {
            if (ticksRemaining <= 0) { segmentFrom = null; segmentTo = null; return; }
            int elapsed = Math.max(0, (int)Math.round(segmentSeconds * 20) - ticksRemaining + 1);
            Vec3 interpolated = interpolateSegment(segmentFrom, segmentTo, elapsed, Math.max(1, (int)Math.round(segmentSeconds * 20)));
            setPos(interpolated.x, interpolated.y, interpolated.z); pinBodies(); ticksRemaining--;
            if (ticksRemaining == 0) { segmentFrom = null; segmentTo = null; }
            else return;
        }
        if (ticksRemaining > 0 && --ticksRemaining > 0) return;
        var step = callbacks.next(current, previous, tags);
        if (step == null) { exit(current.pos(), null); return; }
        if (!callbacks.loaded(step.position())) { exit(current.pos(), step.direction()); return; }
        previous = current; current = callbacks.record(step.position());
        if (current == null || !callbacks.reciprocal(previous, current, step.direction())) { current = previous; exit(current.pos(), step.direction()); return; }
        if (lastDirection != null && lastDirection != step.direction()) {
            com.juicyslew.moonstation14.ms14.disposal.DisposalAudio.playTurnClang((ServerLevel) level(), previous.pos());
            double blunt = previous.tube() == null ? 0.0 : previous.tube().turnDamage();
            if (blunt > 0.0 && level() instanceof ServerLevel server) {
                for (UUID bodyId : List.copyOf(bodies)) if (server.getEntity(bodyId) instanceof LivingEntity body)
                    com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(body,
                            Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, (float) blunt), 1.0F, false);
            }
        }
        Vec3 from = new Vec3(previous.pos().getX() + .5, previous.pos().getY() + .5, previous.pos().getZ() + .5);
        Vec3 to = new Vec3(current.pos().getX() + .5, current.pos().getY() + .5, current.pos().getZ() + .5);
        segmentFrom = from; segmentTo = to; setPos(from.x, from.y, from.z); pinBodies(); lastDirection = step.direction();
        ticksRemaining = Math.max(1, (int)Math.round(segmentSeconds * 20));
        callbacks.moved(this, current);
    }
    static Vec3 interpolateSegment(Vec3 from, Vec3 to, int elapsedTicks, int segmentTicks) {
        if (segmentTicks <= 0) throw new IllegalArgumentException("segment ticks must be positive");
        return from.lerp(to, Math.min(1.0, Math.max(0, elapsedTicks) / (double) segmentTicks));
    }

    private void pinBodies() {
        if (!(level() instanceof ServerLevel server)) return;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(server);
        for (UUID id : List.copyOf(bodies)) if (server.getEntity(id) instanceof LivingEntity body) {
            var existing = containment.ownerOf(body).orElse(null);
            if (existing == null) {
                var flags = originalBodyFlags.get(id);
                if (flags == null || !containment.bindRestored(body, owner, flags)) containment.bind(body, owner);
            }
            else if (existing != owner) bodies.remove(id);
            if (existing == owner || existing == null) {
                Vec3 target = owner.positionFor(body);
                body.teleportTo(target.x, target.y, target.z);
            }
        }
    }
    private void tickOwnedContents() {
        if (!(level() instanceof ServerLevel server) || contents == null) return;
        boolean[] expired = {false};
        contents.tick(server, server.getGameTime(), stack -> com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverTools()
                .get(com.juicyslew.moonstation14.ms14.construction.tool.ModTools.WELDER), ignored -> expired[0] = true);
        if (expired[0]) {
            // The expired item has already been consumed by DisposalContents.tick. Stage all
            // surviving payloads before the explosion can remove the holder/world contents.
            ejectItems(currentPos());
            BlockPos safe = safePayloadExplosionCell(server, currentPos());
            if (safe != null) com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService.detonate(server, safe);
        }
    }

    public boolean addBody(ServerLevel level, LivingEntity body, ContainedBodyOwner source) {
        if (exited || level != level() || body == null || source == null || !bodies.add(body.getUUID())) return false;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        if (!containment.transfer(body, source, owner)) {
            bodies.remove(body.getUUID()); return false;
        }
        var original = containment.export(body, owner);
        if (original.isEmpty()) {
            containment.transfer(body, owner, source);
            bodies.remove(body.getUUID());
            return false;
        }
        originalBodyFlags.putIfAbsent(body.getUUID(), original.get());
        return true;
    }
    public void removeBody(UUID id) { bodies.remove(id); originalBodyFlags.remove(id); }
    public Set<UUID> bodyIds() { return Set.copyOf(bodies); }
    public Map<UUID, com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags> originalBodyFlags() { return Map.copyOf(originalBodyFlags); }
    public Set<String> tags() { return Set.copyOf(tags); }
    public ContainedBodyOwner containmentOwner() { return owner; }
    public Optional<FiniteExposureAir> finiteAir() { return Optional.ofNullable(air); }
    public DisposalContents contents() { return contents; }
    public boolean ejectItems(net.minecraft.core.BlockPos exitAt) {
        if (!(level() instanceof ServerLevel server) || contents == null) return true;
        var ledger = DisposalUnitLedger.forLevel(server);
        net.minecraft.core.Direction preferred = lastDirection == null ? net.minecraft.core.Direction.NORTH : lastDirection;
        var candidates = java.util.List.of(preferred, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.DOWN).stream().distinct().toList();
        for (var e : contents.snapshot().entrySet()) {
            boolean staged = false;
            for (net.minecraft.core.Direction direction : candidates) {
                BlockPos feetCell = exitAt.relative(direction);
                if (!server.hasChunkAt(feetCell)) continue;
                double ejectionSpeed = DisposalsEjection.speedBlocksPerTick();
                double vertical = direction.getStepY() * ejectionSpeed
                        + (direction.getAxis().isHorizontal() ? ejectionSpeed : 0.0);
                Vec3 velocity = new Vec3(direction.getStepX() * ejectionSpeed, vertical,
                        direction.getStepZ() * ejectionSpeed);
                var worldEntity = DisposalWorldPayloadStage.stage(server, e.getValue().stack(),
                        new Vec3(feetCell.getX() + .5, feetCell.getY() + .05, feetCell.getZ() + .5), velocity).orElse(null);
                if (worldEntity == null) continue;
                if (contents.removeForEjection(e.getKey(), worldEntity.location(), ledger.snapshot().revision()).isPresent()) staged = true;
                else worldEntity.rollback();
                if (staged) break;
            }
            if (!staged) return false;
        }
        return true;
    }
    private BlockPos safePayloadExplosionCell(ServerLevel server, BlockPos exitAt) {
        var directions = new ArrayList<net.minecraft.core.Direction>();
        if (lastDirection != null) directions.add(lastDirection);
        for (var direction : net.minecraft.core.Direction.values()) if (!directions.contains(direction)) directions.add(direction);
        for (var direction : directions) {
            BlockPos candidate = exitAt.relative(direction);
            if (server.hasChunkAt(candidate) && server.getBlockState(candidate).getCollisionShape(server, candidate).isEmpty()) return candidate;
        }
        return null;
    }
    public net.minecraft.core.BlockPos currentPos() { return current == null ? (savedCurrentPos == null ? blockPosition() : savedCurrentPos) : current.pos(); }
    public net.minecraft.core.Direction lastDirection() { return lastDirection; }
    public UUID currentTubeId() { return current == null ? savedCurrentId : current.recordId(); }
    public void disconnectAt(Vec3 position, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        if (exited) return; setPos(position.x, position.y, position.z); exit(pos, direction);
    }
    private void exit(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        if (exited) return; exited = true;
        pendingUnitReturn = callbacks.deferToUnitReturn(pos) && (!bodies.isEmpty() || contents != null && !contents.snapshot().isEmpty());
        if (pendingUnitReturn) {
            pendingRelease = true; setDeltaMovement(Vec3.ZERO);
            if (callbacks.tryReturnToUnit(this, pos)) {
                pendingUnitReturn = false;
                if (callbacks.releaseAir(this, pos)) { pendingRelease = false; callbacks.exit(this, pos); discard(); }
            }
            return;
        }
        if (!ejectBodies(pos, direction)) { pendingRelease = true; setDeltaMovement(Vec3.ZERO); return; }
        if (!callbacks.releaseAir(this, pos)) { pendingRelease = true; setDeltaMovement(Vec3.ZERO); return; }
        callbacks.exit(this, pos); discard();
    }
    private boolean ejectBodies(BlockPos pos, net.minecraft.core.Direction direction) {
        if (!(level() instanceof ServerLevel server)) return bodies.isEmpty();
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(server);
        for (UUID id : List.copyOf(bodies)) if (server.getEntity(id) instanceof LivingEntity body) {
            if (!containment.unbind(body, owner)) continue;
            if (!callbacks.ejectBody(body, pos, direction, DisposalsEjection.SPEED_BLOCKS_PER_SECOND)) { containment.bind(body, owner); return false; }
            bodies.remove(id);
            originalBodyFlags.remove(id);
        }
        return bodies.isEmpty();
    }
    private final class TransitOwner implements ContainedBodyOwner {
        @Override public boolean acceptDroppedItem(com.juicyslew.moonstation14.ms14.hands.ItemToken token,
            ItemLocation source, net.minecraft.world.item.ItemStack stack) {
            return contents != null && contents.acceptDropped(token, source, stack);
        }
        @Override public void preserveContentsBeforeExplosion() { ejectItems(currentPos()); }
        @Override public UUID identity() { return identity; }
        @Override public ServerLevel level() { return (ServerLevel) DisposalTransitHolder.this.level(); }
        @Override public Vec3 position() { return DisposalTransitHolder.this.position(); }
        @Override public com.juicyslew.moonstation14.ms14.containment.ContainedBodyView view() {
            var data = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                    .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.HOLDER);
            boolean cameraCollision = data == null || data.containedView().cameraCollision();
            return new com.juicyslew.moonstation14.ms14.containment.ContainedBodyView(identity, Optional.empty(), cameraCollision);
        }
        @Override public Vec3 positionFor(LivingEntity body) {
            return DisposalTransitHolder.this.position().subtract(0, body.getEyeHeight(), 0);
        }
        @Override public Stage stage() { return Stage.TRANSIT; }
        @Override public boolean isValid() { return (!exited || pendingRelease) && isAlive() && (current != null || savedCurrentPos != null); }
        @Override public Optional<com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource> airSource() { return Optional.ofNullable(air); }
        @Override public boolean tryEscape(LivingEntity body) { return false; }
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("TransitOwner")) identity = tag.getUUID("TransitOwner");
        savedCurrentPos = tag.contains("TubePos") ? BlockPos.of(tag.getLong("TubePos")) : blockPosition();
        if (tag.hasUUID("TubeId")) savedCurrentId = tag.getUUID("TubeId");
        if (tag.contains("PreviousPos")) savedPreviousPos = BlockPos.of(tag.getLong("PreviousPos"));
        if (tag.hasUUID("PreviousId")) savedPreviousId = tag.getUUID("PreviousId");
        if (tag.contains("LastDirection")) lastDirection = net.minecraft.core.Direction.byName(tag.getString("LastDirection"));
        ticksRemaining = Math.max(0, tag.getInt("TicksRemaining")); exited = tag.getBoolean("Exited"); pendingRelease = tag.getBoolean("PendingAirRelease"); pendingUnitReturn = tag.getBoolean("PendingUnitReturn");
        ListTag savedTags = tag.getList("RoutingTags", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < savedTags.size(); i++) tags.add(savedTags.getString(i));
        ListTag savedBodies = tag.getList("BodyUUIDs", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < savedBodies.size(); i++) try { bodies.add(UUID.fromString(savedBodies.getString(i))); } catch (IllegalArgumentException ignored) { }
        ListTag savedFlags = tag.getList("OriginalBodyFlags", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < savedFlags.size(); i++) {
            var decoded = decodeContainedBodyFlags(savedFlags.getCompound(i));
            if (decoded != null) originalBodyFlags.put(decoded.getKey(), decoded.getValue());
        }
        if (tag.contains("Air", net.minecraft.nbt.Tag.TAG_COMPOUND)) air = decodeAir(tag.getCompound("Air"));
        if (level() instanceof ServerLevel server) {
            callbacks = new LoadedWorldDisposalRouting(server);
            current = callbacks.record(savedCurrentPos);
            if (current != null && savedCurrentId != null && !savedCurrentId.equals(current.recordId())) current = null;
            if (savedPreviousPos != null) previous = callbacks.record(savedPreviousPos);
            var config = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                    .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.HOLDER);
            if (config != null && config.holder() != null) segmentSeconds = config.holder().segmentSeconds();
            if (tag.contains("SegmentFrom")) segmentFrom = readVector(tag.getCompound("SegmentFrom"));
            if (tag.contains("SegmentTo")) segmentTo = readVector(tag.getCompound("SegmentTo"));
            if (tag.contains("Contents", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                DisposalContents.codec().parse(ops, tag.get("Contents")).result()
                        .flatMap(data -> DisposalContents.decode(data, DisposalUnitLedger.forLevel(server)))
                        .ifPresent(value -> contents = value);
            }
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putUUID("TransitOwner", identity);
        BlockPos cp = current == null ? (savedCurrentPos == null ? blockPosition() : savedCurrentPos) : current.pos();
        tag.putLong("TubePos", cp.asLong());
        UUID cid = current == null ? savedCurrentId : current.recordId(); if (cid != null) tag.putUUID("TubeId", cid);
        if (previous != null) { tag.putLong("PreviousPos", previous.pos().asLong()); tag.putUUID("PreviousId", previous.recordId()); }
        else if (savedPreviousPos != null) { tag.putLong("PreviousPos", savedPreviousPos.asLong()); if (savedPreviousId != null) tag.putUUID("PreviousId", savedPreviousId); }
        if (lastDirection != null) tag.putString("LastDirection", lastDirection.getName());
        tag.putInt("TicksRemaining", ticksRemaining); tag.putBoolean("Exited", exited); tag.putBoolean("PendingAirRelease", pendingRelease); tag.putBoolean("PendingUnitReturn", pendingUnitReturn);
        ListTag savedTags = new ListTag(); tags.forEach(value -> savedTags.add(net.minecraft.nbt.StringTag.valueOf(value))); tag.put("RoutingTags", savedTags);
        ListTag savedBodies = new ListTag(); bodies.forEach(value -> savedBodies.add(net.minecraft.nbt.StringTag.valueOf(value.toString()))); tag.put("BodyUUIDs", savedBodies);
        ListTag savedFlags = new ListTag(); originalBodyFlags.forEach((body, flags) -> savedFlags.add(encodeContainedBodyFlags(body, flags)));
        tag.put("OriginalBodyFlags", savedFlags);
        if (air != null) tag.put("Air", encodeAir(air.sample().orElseThrow().mixture(), air.sample().orElseThrow().volumeCubicMeters()));
        if (segmentFrom != null) tag.put("SegmentFrom", writeVector(segmentFrom)); if (segmentTo != null) tag.put("SegmentTo", writeVector(segmentTo));
        if (contents != null && level() instanceof ServerLevel server) {
            DisposalContents.codec().encodeStart(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), contents.encodedData())
                    .result().ifPresent(value -> tag.put("Contents", value));
        }
    }
    static CompoundTag encodeAir(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture mix, double volume) {
        CompoundTag tag = new CompoundTag(); tag.putDouble("Temperature", mix.temperatureKelvin()); tag.putDouble("Volume", volume);
        CompoundTag gases = new CompoundTag(); mix.gasMoles().forEach((type, moles) -> gases.putDouble(type.name(), moles)); tag.put("Gases", gases); return tag;
    }
    static FiniteExposureAir decodeAir(CompoundTag tag) {
        var gases = new EnumMap<com.juicyslew.moonstation14.ms14.atmos.core.GasType, Double>(com.juicyslew.moonstation14.ms14.atmos.core.GasType.class);
        CompoundTag stored = tag.getCompound("Gases");
        for (String key : stored.getAllKeys()) try { gases.put(com.juicyslew.moonstation14.ms14.atmos.core.GasType.valueOf(key), stored.getDouble(key)); } catch (IllegalArgumentException ignored) { }
        return new FiniteExposureAir(new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(gases, tag.getDouble("Temperature")), tag.getDouble("Volume"));
    }
    private static CompoundTag writeVector(Vec3 v) { CompoundTag t = new CompoundTag(); t.putDouble("X", v.x); t.putDouble("Y", v.y); t.putDouble("Z", v.z); return t; }
    private static Vec3 readVector(CompoundTag t) { return new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")); }
    static CompoundTag encodeContainedBodyFlags(UUID body, com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags flags) {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Body", body); tag.putBoolean("Invisible", flags.invisible());
        tag.putBoolean("NoPhysics", flags.noPhysics()); tag.putBoolean("NoGravity", flags.noGravity()); return tag;
    }
    static Map.Entry<UUID, com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags> decodeContainedBodyFlags(CompoundTag tag) {
        if (!tag.hasUUID("Body")) return null;
        return Map.entry(tag.getUUID("Body"), new com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.ContainedBodyFlags(
                tag.getBoolean("Invisible"), tag.getBoolean("NoPhysics"), tag.getBoolean("NoGravity")));
    }

    public record BlockPosState(net.minecraft.core.BlockPos pos, UUID recordId, com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Tube tube,
                                com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame) {
        public BlockPosState(net.minecraft.core.BlockPos pos, UUID recordId, com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Tube tube) {
            this(pos, recordId, tube, new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP));
        }
    }
    public record Step(net.minecraft.core.BlockPos position, net.minecraft.core.Direction direction) { }
    public interface TransitCallbacks {
        boolean anchoredResolvedTube(net.minecraft.core.BlockPos pos);
        boolean loaded(net.minecraft.core.BlockPos pos);
        BlockPosState record(net.minecraft.core.BlockPos pos);
        boolean reciprocal(BlockPosState from, BlockPosState to, net.minecraft.core.Direction direction);
        Step next(BlockPosState current, BlockPosState previous, Set<String> tags);
        boolean disconnected(net.minecraft.core.BlockPos pos);
        void moved(DisposalTransitHolder holder, BlockPosState segment);
        boolean ejectBody(LivingEntity body, net.minecraft.core.BlockPos lastLoaded, net.minecraft.core.Direction direction, double blocksPerSecond);
        void exit(DisposalTransitHolder holder, net.minecraft.core.BlockPos lastLoaded);
        boolean releaseAir(DisposalTransitHolder holder, net.minecraft.core.BlockPos lastLoaded);
        boolean deferToUnitReturn(net.minecraft.core.BlockPos lastLoaded);
        boolean tryReturnToUnit(DisposalTransitHolder holder, net.minecraft.core.BlockPos lastLoaded);
    }
}
