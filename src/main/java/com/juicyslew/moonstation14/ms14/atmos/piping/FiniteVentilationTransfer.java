package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Synchronous room/device exchange. Invoke only after a paid PowerRuntime ventilation interval. */
final class FiniteVentilationTransfer {
    private static final double ROOM_VOLUME_M3 = 1.0;
    private FiniteVentilationTransfer() { }

    record Result(GasMixture packet) {
        static Result none() { return new Result(null); }
        boolean moved() { return packet != null; }
    }

    /**
     * One Minecraft atmosphere cell is locally modeled as 1.0 m3, unlike an SS14 tile; pipe
     * nodes retain the SS14-derived 0.2 m3 volume. No chunk is force-loaded by this operation.
     */
    static Result executeTrustedAfterPaidInterval(ServerLevel level, AtmosphereService atmosphere,
            VentilationDeviceBlockEntity exactDevice, double solverPaidSeconds) {
        if (level == null || atmosphere == null || exactDevice == null || !Double.isFinite(solverPaidSeconds)
                || solverPaidSeconds <= 0 || level.getServer() == null || !level.getServer().isSameThread()
                || exactDevice.getLevel() != level) return Result.none();
        BlockPos devicePos = exactDevice.getBlockPos();
        LevelChunk deviceChunk = fullChunk(level, devicePos);
        if (deviceChunk == null || deviceChunk.getBlockEntity(devicePos) != exactDevice
                || !(deviceChunk.getBlockState(devicePos).getBlock() instanceof VentilationDeviceBlock block)
                || block.kind() != exactDevice.kind() || !exactDevice.settingsValid()) return Result.none();
        VentilationDeviceConfig.Settings settings = exactDevice.config().settings();
        if (!settings.configuredEnabled()) return Result.none();

        Direction facing = deviceChunk.getBlockState(devicePos).getValue(VentilationDeviceBlock.FACING);
        BlockPos roomPos = devicePos.relative(facing);
        BlockPos pipePos = devicePos.relative(facing.getOpposite());
        if (roomPos.equals(pipePos)) return Result.none();
        LevelChunk pipeChunk = fullChunk(level, pipePos);
        if (pipeChunk == null) return Result.none();
        int lane = layerBit(settings.selectedLayer());
        Direction towardDevice = facing;
        PipeGasInventoryEndpoint pipe;
        if (pipeChunk.getBlockEntity(pipePos) instanceof VisualPipeBlockEntity visual) {
            if (!visual.hasLayer(lane)) return Result.none();
            pipe = PipeGasInventoryEndpoint.create(level, pipePos, lane, towardDevice).orElse(null);
        } else {
            pipe = PipeGasInventoryEndpoint.createSealed(level, pipePos,
                    PipeTopology.Layer.valueOf(settings.selectedLayer().name()), towardDevice).orElse(null);
        }
        if (pipe == null) return Result.none();

        boolean wide = exactDevice.kind() == VentilationDeviceConfig.Kind.SCRUBBER && settings.wideNet();
        // Center is the trusted foreground anchor: do not let a known neighbor become a bridge
        // when the original foreground cell has unknown/provisional ownership.
        java.util.Map<BlockPos, PipeGasTransaction.Endpoint> roomEndpoints = new java.util.HashMap<>();
        PipeGasTransaction.Endpoint center = atmosphere.finiteRoomEndpointForTrustedSimulation(level, roomPos).orElse(null);
        if (center == null || !center.eligible() || fullChunk(level, roomPos) == null) return Result.none();
        roomEndpoints.put(roomPos, center);
        List<BlockPos> targets = trustedScrubberTargets(roomPos, wide,
                target -> fullChunk(level, target) != null && AtmosphereTopology.canExchange(level, roomPos, target),
                target -> {
                    PipeGasTransaction.Endpoint endpoint = atmosphere.finiteRoomEndpointForTrustedSimulation(level, target).orElse(null);
                    if (endpoint == null || !endpoint.eligible()) return false;
                    roomEndpoints.put(target, endpoint);
                    return true;
                });
        Result total = Result.none();
        for (BlockPos target : targets) {
            PipeGasTransaction.Endpoint room = roomEndpoints.get(target);
            if (room == null) continue;
            Result moved = transferCell(exactDevice.kind(), settings, room, pipe, solverPaidSeconds);
            if (moved.moved()) total = moved;
        }
        return total;
    }

    static List<BlockPos> trustedScrubberTargets(BlockPos center, boolean wide,
            Predicate<BlockPos> loadedAndExchangeable, Predicate<BlockPos> finiteOwned) {
        List<BlockPos> selected = new ArrayList<>();
        if (!finiteOwned.test(center)) return List.of();
        selected.add(center);
        if (!wide) return List.copyOf(selected);
        for (BlockPos target : new BlockPos[]{center.west(), center.east(), center.north(), center.south()}) {
            if (target.getY() == center.getY() && loadedAndExchangeable.test(target) && finiteOwned.test(target))
                selected.add(target);
        }
        return List.copyOf(selected);
    }

    static Result transferCell(VentilationDeviceConfig.Kind kind,
            VentilationDeviceConfig.Settings settings,
            PipeGasTransaction.Endpoint room, PipeGasTransaction.Endpoint pipe,
            double solverPaidSeconds) {

        GasMixture roomBefore = room.snapshot();
        GasMixture pipeBefore = pipe.snapshot();
        if (roomBefore == null || pipeBefore == null) return Result.none();
        Optional<PipeGasTransfer.Plan> reference;
        boolean vent = kind == VentilationDeviceConfig.Kind.VENT;
        try {
            reference = vent
                    ? VentilationGasFlow.planVentRelease(pipeBefore, roomBefore,
                        Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3, ROOM_VOLUME_M3, solverPaidSeconds, settings)
                    : VentilationGasFlow.planScrubberCapture(roomBefore, pipeBefore,
                        ROOM_VOLUME_M3, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3, solverPaidSeconds, settings);
        } catch (RuntimeException invalid) { return Result.none(); }
        if (reference.isEmpty()) return Result.none();
        PipeGasTransaction.Endpoint source = vent ? pipe : room;
        PipeGasTransaction.Endpoint destination = vent ? room : pipe;
        PipeGasTransfer.Plan spec = reference.get();
        double maximum = spec.packet().totalMoles();
        double pressureLimit = vent
                ? Math.min(settings.externalPressureBoundKPa(), settings.targetPressureKPa())
                : 5_066.25;
        java.util.Set<GasType> filter = vent ? java.util.Set.of(GasType.values()) : settings.gasesToScrub();
        PipeGasTransaction.Plan transaction;
        try {
            transaction = PipeGasTransaction.plan(source, destination,
                    vent ? Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3 : ROOM_VOLUME_M3,
                    vent ? ROOM_VOLUME_M3 : Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3,
                    maximum, pressureLimit, filter).orElse(null);
        } catch (RuntimeException invalid) { return Result.none(); }
        if (transaction == null) return Result.none();
        PipeGasTransfer.Plan actual = transaction.transfer();
        if (!withinReference(actual, spec)) return Result.none();
        PipeGasTransaction.commit(source, destination, transaction);
        return new Result(actual.packet());
    }

    private static boolean withinReference(PipeGasTransfer.Plan actual, PipeGasTransfer.Plan spec) {
        for (GasType gas : GasType.values()) {
            double moved = actual.perGasMoved().getOrDefault(gas, 0.0);
            if (moved > spec.perGasMoved().getOrDefault(gas, 0.0)) return false;
        }
        return actual.packet().totalMoles() <= spec.packet().totalMoles()
                && actual.energyMoved() <= spec.energyMoved();
    }

    private static int layerBit(VentilationDeviceConfig.Layer layer) {
        return switch (layer) {
            case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
            case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
            case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
        };
    }

    private static LevelChunk fullChunk(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return null;
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false)
                instanceof LevelChunk chunk ? chunk : null;
    }
}
