package com.juicyslew.moonstation14.ms14.atmos.teg;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only resolver for a core and its two correctly-oriented, already-loaded circulators. */
public final class TegAssembly {
    private TegAssembly() { }

    /** SS14 TegNodeGroup searches one tile in each core-facing direction. */
    public static Layout layout(BlockPos center, Direction facing) {
        Objects.requireNonNull(center, "center");
        if (facing == null || !facing.getAxis().isHorizontal()) throw new IllegalArgumentException("horizontal facing required");
        Direction opposite = facing.getOpposite();
        return new Layout(center.relative(facing), facing.getClockWise(),
                center.relative(opposite), opposite.getClockWise());
    }

    /** Returns empty for any missing, unloaded, stale, malformed, or misoriented member. */
    public static Optional<Assembly> resolve(ServerLevel level, AtmosMachineBlockEntity center) {
        if (level == null || center == null || level.isClientSide || !level.getServer().isSameThread()
                || center.getLevel() != level || !exactHost(level, center)) return Optional.empty();
        try {
            if (!(center.getBlockState().getBlock() instanceof AtmosMachineBlock)) return Optional.empty();
            AtmosMachineProfile core = center.resolvedProfile();
            Direction facing = center.getBlockState().getValue(AtmosMachineBlock.FACING);
            if (!isCore(core) || facing == null || !facing.getAxis().isHorizontal()) return Optional.empty();
            Layout layout = layout(center.getBlockPos(), facing);
            AtmosMachineBlockEntity a = hostAt(level, layout.circulatorA()).orElse(null);
            AtmosMachineBlockEntity b = hostAt(level, layout.circulatorB()).orElse(null);
            if (a == null || b == null || a == b || !isCirculator(a) || !isCirculator(b)) return Optional.empty();
            if (a.getBlockState().getValue(AtmosMachineBlock.FACING) != layout.circulatorAFacing()
                    || b.getBlockState().getValue(AtmosMachineBlock.FACING) != layout.circulatorBFacing()) return Optional.empty();
            PortPair portsA = ports(a);
            PortPair portsB = ports(b);
            if (portsA == null || portsB == null) return Optional.empty();
            return Optional.of(new Assembly(center, a, b, portsA.inlet(), portsA.outlet(), portsB.inlet(), portsB.outlet()));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static boolean isCore(AtmosMachineProfile profile) {
        return profile != null && profile.kind() == AtmosMachineProfile.Kind.TEG_CENTER && profile.teg() != null
                && hasNode(profile, AtmosMachineProfile.NetworkNodeName.TEG);
    }
    private static boolean isCirculator(AtmosMachineBlockEntity entity) {
        AtmosMachineProfile profile = entity.resolvedProfile();
        return profile != null && profile.kind() == AtmosMachineProfile.Kind.TEG_CIRCULATOR
                && hasNode(profile, AtmosMachineProfile.NetworkNodeName.CIRCULATOR)
                && profile.ports().size() == 2
                && profile.ports().stream().anyMatch(p -> p.name() == AtmosMachineProfile.PortName.INLET)
                && profile.ports().stream().anyMatch(p -> p.name() == AtmosMachineProfile.PortName.OUTLET);
    }
    private static boolean hasNode(AtmosMachineProfile profile, AtmosMachineProfile.NetworkNodeName name) {
        return profile.networkNodes().stream().anyMatch(n -> n.name() == name
                && n.group() == AtmosMachineProfile.NetworkGroup.TEG);
    }
    private static boolean exactHost(ServerLevel level, AtmosMachineBlockEntity entity) {
        var chunk = level.getChunkSource().getChunkNow(entity.getBlockPos().getX() >> 4, entity.getBlockPos().getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                && chunk.getBlockEntities().get(entity.getBlockPos()) == entity
                && chunk.getBlockState(entity.getBlockPos()).getBlock() == entity.getBlockState().getBlock();
    }
    private static Optional<AtmosMachineBlockEntity> hostAt(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return Optional.empty();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (!(chunk instanceof LevelChunk loaded) || !loaded.getFullStatus().isOrAfter(FullChunkStatus.FULL)) return Optional.empty();
        if (!(loaded.getBlockEntity(pos) instanceof AtmosMachineBlockEntity host) || !exactHost(level, host)
                || !(loaded.getBlockState(pos).getBlock() instanceof AtmosMachineBlock)) return Optional.empty();
        return Optional.of(host);
    }
    private static PortPair ports(AtmosMachineBlockEntity entity) {
        AtmosMachineProfile profile = entity.resolvedProfile();
        List<PipeGasPortHost.Port> actual = entity.gasPorts();
        if (profile == null || actual.size() != 2) return null;
        PipeGasPortHost.Port inlet = null, outlet = null;
        for (AtmosMachineProfile.Port declared : profile.ports()) {
            PipeGasPortHost.Port live = actual.stream().filter(p -> p.id().equals(declared.name().name().toLowerCase(java.util.Locale.ROOT))).findFirst().orElse(null);
            if (live == null || live.incarnation() == null || live.layer() != PipeTopology.Layer.PRIMARY
                    || live.volume() != declared.volumeM3()) return null;
            if (declared.name() == AtmosMachineProfile.PortName.INLET) inlet = live;
            if (declared.name() == AtmosMachineProfile.PortName.OUTLET) outlet = live;
        }
        if (inlet == null || outlet == null || inlet.equals(outlet)) return null;
        return new PortPair(key(entity, inlet), key(entity, outlet));
    }
    private static PipeTopology.NodeKey key(AtmosMachineBlockEntity host, PipeGasPortHost.Port port) {
        return port.key(host.getBlockPos());
    }

    public record Layout(BlockPos circulatorA, Direction circulatorAFacing,
                         BlockPos circulatorB, Direction circulatorBFacing) { }
    private record PortPair(PipeTopology.NodeKey inlet, PipeTopology.NodeKey outlet) { }
    public record Assembly(AtmosMachineBlockEntity center, AtmosMachineBlockEntity circulatorA,
                           AtmosMachineBlockEntity circulatorB, PipeTopology.NodeKey inletA,
                           PipeTopology.NodeKey outletA, PipeTopology.NodeKey inletB,
                           PipeTopology.NodeKey outletB) {
        public Assembly {
            Objects.requireNonNull(center); Objects.requireNonNull(circulatorA); Objects.requireNonNull(circulatorB);
            Objects.requireNonNull(inletA); Objects.requireNonNull(outletA); Objects.requireNonNull(inletB); Objects.requireNonNull(outletB);
            if (circulatorA == circulatorB || circulatorA == center || circulatorB == center) throw new IllegalArgumentException("assembly hosts must be distinct");
        }
    }
}
