package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Loaded-only adapter from authoritative saved tube records to resolved local tube composition. */
public final class LoadedWorldDisposalRouting implements DisposalTransitHolder.TransitCallbacks {
    private static final Set<DisposalTubeWorldData> LISTENING = Collections.newSetFromMap(new WeakHashMap<>());
    private final ServerLevel level;
    private final DisposalTubeWorldData world;
    private final Set<UUID> active = new HashSet<>();
    private final Random random;

    public LoadedWorldDisposalRouting(ServerLevel level) { this(level, new Random()); }
    public LoadedWorldDisposalRouting(ServerLevel level, Random random) {
        this.level = Objects.requireNonNull(level); this.world = DisposalTubeWorldData.forMutation(level);
        this.random = Objects.requireNonNull(random);
        synchronized (LISTENING) {
            if (LISTENING.add(world)) world.addDisconnectListener(record -> {
                for (var entity : level.getEntities().getAll())
                    if (entity instanceof DisposalTransitHolder holder && record.id().equals(holder.currentTubeId()))
                        holder.disconnectAt(holder.position(), record.pos(), holder.lastDirection());
            });
        }
    }

    @Override public boolean anchoredResolvedTube(BlockPos pos) { return record(pos) != null; }
    @Override public boolean loaded(BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    @Override public DisposalTransitHolder.BlockPosState record(BlockPos pos) {
        if (!loaded(pos)) return null;
        var lookup = world.lookup(level, pos);
        if (lookup.kind() != DisposalTubeWorldData.LookupKind.PRESENT || lookup.record() == null || !lookup.record().anchored()) return null;
        var r = lookup.record();
        var id = ModDisposals.id(r.prototypeId());
        DisposalEntityData data = PrototypeRuntime.serverDisposals().get(id);
        if (data == null || data.tube() == null) return null;
        var original = data.tube();
        int rotation = original.rotationQuarters();
        var tube = new DisposalEntityData.Tube(original.kind(), original.topology(), rotation,
                original.segmentSeconds(), original.turnDamage(), r.routingTag(), routerTags(r.routingFilter()));
        return new DisposalTransitHolder.BlockPosState(r.pos(), r.id(), tube, r.frame());
    }
    @Override public boolean reciprocal(DisposalTransitHolder.BlockPosState from, DisposalTransitHolder.BlockPosState to, Direction direction) {
        if (from == null || to == null || direction == null) return false;
        var toward = DisposalDirection.fromMC(direction);
        return DisposalTubeRouting.worldConnections(from.tube(), from.frame()).contains(toward)
                && DisposalTubeRouting.worldConnections(to.tube(), to.frame()).contains(toward.opposite());
    }
    @Override public DisposalTransitHolder.Step next(DisposalTransitHolder.BlockPosState at, DisposalTransitHolder.BlockPosState previous, Set<String> tags) {
        if (at == null) return null;
        if (at.tube().kind() == DisposalEntityData.Kind.TAGGER && at.tube().tag() != null && !at.tube().tag().isBlank())
            tags.add(at.tube().tag());
        Direction incoming = previous == null ? null : direction(at.pos(), previous.pos());
        DisposalDirection worldIncoming = incoming == null ? null : DisposalDirection.fromMC(incoming);
        DisposalDirection selected = DisposalTubeRouting.selectNextDirection(at.tube(), at.frame(), worldIncoming, tags, random::nextInt);
        if (selected == null) return null;
        Direction out = selected.toMC();
        BlockPos next = at.pos().relative(out);
        return new DisposalTransitHolder.Step(next, out);
    }
    @Override public boolean disconnected(BlockPos pos) { return record(pos) == null; }
    @Override public void moved(DisposalTransitHolder holder, DisposalTransitHolder.BlockPosState segment) { }
    @Override public boolean ejectBody(LivingEntity body, BlockPos pos, Direction direction, double speed) {
        var candidates = ejectionDirections(direction);
        for (Direction d : candidates) {
            BlockPos target = pos.relative(d);
            if (!level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)) continue;
            Vec3 destination = new Vec3(target.getX() + .5, target.getY(), target.getZ() + .5);
            if (!level.noCollision(body, body.getBoundingBox().move(destination.subtract(body.position())))) continue;
            body.teleportTo(destination.x, destination.y, destination.z);
            double tickSpeed = DisposalsEjection.blocksPerSecondToBlocksPerTick(speed);
            Vec3 launch = new Vec3(d.getStepX() * tickSpeed, d.getStepY() * tickSpeed, d.getStepZ() * tickSpeed);
            body.setDeltaMovement(launch);
            if (body instanceof net.minecraft.world.entity.Mob mob) {
                int flightTicks = (int) Math.ceil(DisposalsEjection.DISTANCE_BLOCKS
                        / DisposalsEjection.SPEED_BLOCKS_PER_SECOND * 20d);
                com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                        .launch(mob, launch, flightTicks);
            }
            return true;
        }
        // No safe loaded ejection cell: retain the exact living body in this loaded holder for retry.
        return false;
    }
    @Override public void exit(DisposalTransitHolder holder, BlockPos lastLoaded) { active.remove(holder.getUUID()); }
    @Override public boolean releaseAir(DisposalTransitHolder holder, BlockPos lastLoaded) {
        if (!holder.ejectItems(lastLoaded)) return false;
        var source = holder.finiteAir().flatMap(a -> a.sample().map(s -> Map.entry(a, s)));
        if (source.isEmpty()) return true;
        var mix = source.get().getValue().mixture();
        if (mix.totalMoles() == 0) return true;
        boolean unit = level.getBlockEntity(lastLoaded.above()) instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
        var dest = releaseEnvironmentCandidates(lastLoaded, unit).stream()
                .map(pos -> AtmosphereService.INSTANCE.finiteCellEndpointForImmediateTransfer(level, pos))
                .filter(Optional::isPresent).map(Optional::orElseThrow).findFirst();
        if (dest.isEmpty()) return false;
        var air = source.get().getKey();
        var endpoint = new PipeGasTransaction.Endpoint() {
            private long version;
            private GasMixture value = mix;
            @Override public Object identity() { return air; }
            @Override public long version() { return version; }
            @Override public GasMixture snapshot() { return value; }
            @Override public boolean eligible() { return true; }
            @Override public boolean writeRaw(GasMixture expected, GasMixture next) {
                if (value != expected || !air.replaceInventory(expected, next)) return false;
                value = next; return true;
            }
            @Override public void publish() { version++; }
        };
        var plan = PipeGasTransaction.plan(endpoint, dest.get(), source.get().getValue().volumeCubicMeters(),
                Double.MAX_VALUE, mix.totalMoles(), Double.MAX_VALUE, EnumSet.allOf(com.juicyslew.moonstation14.ms14.atmos.core.GasType.class));
        if (plan.isEmpty()) return false;
        try {
            PipeGasTransaction.commit(endpoint, dest.get(), plan.get());
            return true;
        } catch (RuntimeException failedOrRolledBack) {
            return false;
        }
    }
    static BlockPos releaseEnvironmentCell(BlockPos endCell, boolean unitOccupiesAbove) {
        return unitOccupiesAbove ? endCell.above(2) : endCell.above();
    }
    private List<BlockPos> releaseEnvironmentCandidates(BlockPos endCell, boolean unitOccupiesAbove) {
        BlockPos origin = unitOccupiesAbove ? endCell.above() : endCell;
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction d : ejectionDirections(null)) {
            BlockPos candidate = origin.relative(d);
            if (!loaded(candidate) || !level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    || level.getBlockState(candidate).is(com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get())) continue;
            candidates.add(candidate);
        }
        return candidates;
    }
    @Override public boolean deferToUnitReturn(BlockPos lastLoaded) {
        return level.getBlockEntity(lastLoaded.above()) instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity unit
                && unit.canReceiveReturnedHolderAt(lastLoaded);
    }
    @Override public boolean tryReturnToUnit(DisposalTransitHolder holder, BlockPos lastLoaded) {
        var blockEntity = level.getBlockEntity(lastLoaded.above());
        return blockEntity instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity unit
                && unit.receiveReturnedHolder(holder);
    }

    private static Set<String> routerTags(String filter) {
        if (filter == null || filter.isBlank()) return Set.of();
        return Arrays.stream(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static List<Direction> ejectionDirections(Direction preferred) {
        List<Direction> result = new ArrayList<>();
        if (preferred != null) result.add(preferred);
        for (Direction d : Direction.values()) if (!result.contains(d)) result.add(d);
        return result;
    }
    private static Direction direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        for (Direction direction : Direction.values())
            if (direction.getStepX() == dx && direction.getStepY() == dy && direction.getStepZ() == dz) return direction;
        return null;
    }
}
