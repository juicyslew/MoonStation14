package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Optional;

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
        LevelChunk roomChunk = fullChunk(level, roomPos);
        LevelChunk pipeChunk = fullChunk(level, pipePos);
        if (roomChunk == null || pipeChunk == null) return Result.none();
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
        PipeGasTransaction.Endpoint room = atmosphere.finiteRoomEndpointForTrustedSimulation(level, roomPos).orElse(null);
        if (pipe == null || room == null) return Result.none();

        GasMixture roomBefore = room.snapshot();
        GasMixture pipeBefore = pipe.snapshot();
        if (roomBefore == null || pipeBefore == null) return Result.none();
        Optional<PipeGasTransfer.Plan> reference;
        boolean vent = exactDevice.kind() == VentilationDeviceConfig.Kind.VENT;
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
