package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;

import java.util.Optional;

/** Synchronous room/device exchange. Invoke only after a paid PowerRuntime ventilation interval. */
final class FiniteVentilationTransfer {
    private static final double ROOM_VOLUME_M3 = 1.0;
    private FiniteVentilationTransfer() { }

    record ExportReceipt(AtmosphereService.ExteriorBoundary.Kind kind, GasMixture packet) {
        ExportReceipt { packet = copy(packet); }
        @Override public GasMixture packet() { return copy(packet); }
        double thermalEnergyJoules() { return packet.thermalEnergy(); }
    }
    record Result(GasMixture packet, ExportReceipt export) {
        Result(GasMixture packet) { this(packet, null); }
        static Result none() { return new Result(null, null); }
        boolean moved() { return packet != null; }
    }

    /**
     * One Minecraft atmosphere cell is locally modeled as 1.0 m3, unlike an SS14 tile; pipe
     * pipe gas uses its resolved shared-network volume. No chunk is force-loaded by this operation.
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
        var pumpProfile = ventProfile(exactDevice);
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
        if (pipe == null) return Result.none();
        boolean vent = exactDevice.kind() == VentilationDeviceConfig.Kind.VENT;

        if (room == null && vent) {
            AtmosphereService.ExteriorBoundary boundary = atmosphere
                    .exteriorBoundaryForImmediateTransfer(level, roomPos).orElse(null);
            if (boundary == null) return Result.none();
            GasMixture sink = boundary.ambient();
            GasMixture pipeBefore = pipe.snapshot();
            if (pipeBefore == null) {
                recordDecision(level, exactDevice, pipePos, pipe, lane, null, roomPos, boundary.kind(), sink, settings,
                        solverPaidSeconds, pumpProfile, false, null, null, null, null, null, null, "UNAVAILABLE_PIPE_INPUT", null);
                return Result.none();
            }
            Optional<PipeGasTransfer.Plan> reference;
            double pipeVolume = pipeBefore.volumeCubicMeters();
            try {
                reference = VentilationGasFlow.planVentRelease(pipeBefore, sink,
                        pipeVolume, ROOM_VOLUME_M3, solverPaidSeconds, settings, pumpProfile);
            } catch (RuntimeException invalid) {
                recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, boundary.kind(), sink, settings,
                        solverPaidSeconds, pumpProfile, false, null, null, null, null, null, null, "PLAN_EXCEPTION", null);
                return Result.none();
            }
            if (reference.isEmpty()) {
                recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, boundary.kind(), sink, settings,
                        solverPaidSeconds, pumpProfile, false, null, null, null, null, null, null, "NO_PLAN", null);
                return Result.none();
            }
            double maxMoles = reference.get().packet().totalMoles();
            PipeGasTransfer.Plan actual;
            try {
                actual = PipeGasTransfer.plan(pipeBefore, sink,
                        pipeVolume, ROOM_VOLUME_M3, maxMoles,
                         VentilationGasFlow.releaseDestinationPressureLimit(settings, pumpProfile),
                        java.util.Set.of(GasType.values())).orElse(null);
            } catch (RuntimeException invalid) {
                recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, boundary.kind(), sink, settings,
                        solverPaidSeconds, pumpProfile, true, reference.get().packet(), reference.get().sourceAfter(),
                        reference.get().destinationAfter(), null, null, null, "REFERENCE_BOUND_EXCEPTION", null);
                return Result.none();
            }
            if (actual == null || !withinReference(actual, reference.get()) || !boundary.validate()) {
                recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, boundary.kind(), sink, settings,
                        solverPaidSeconds, pumpProfile, true, reference.get().packet(), reference.get().sourceAfter(),
                        reference.get().destinationAfter(), null, null, null, "REJECTED", null);
                return Result.none();
            }
            try { PipeGasTransaction.commit(PipeGasTransaction.prepare(pipe, pipeBefore, actual.sourceAfter())); }
            catch (RuntimeException failure) {
                recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, boundary.kind(), sink, settings,
                        solverPaidSeconds, pumpProfile, true, reference.get().packet(), reference.get().sourceAfter(),
                        reference.get().destinationAfter(), null, null, null, "COMMIT_EXCEPTION", null);
                throw failure;
            }
            var receipt = new ExportReceipt(boundary.kind(), actual.packet());
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, boundary.kind(), sink,
                    settings, solverPaidSeconds, pumpProfile, true, reference.get().packet(), reference.get().sourceAfter(),
                    reference.get().destinationAfter(), actual.packet(), pipe.snapshot(), sink, "COMMITTED", receipt);
            return new Result(actual.packet(), receipt);
        }
        if (room == null) return Result.none();

        GasMixture roomBefore = room.snapshot();
        GasMixture pipeBefore = pipe.snapshot();
        if (roomBefore == null || pipeBefore == null) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                    solverPaidSeconds, pumpProfile, false, null, null, null, null, null, null, "UNAVAILABLE_ENDPOINT_INPUT", null);
            return Result.none();
        }
        Optional<PipeGasTransfer.Plan> reference;
        double pipeVolume = pipeBefore.volumeCubicMeters();
        try {
            reference = vent
                    ? VentilationGasFlow.planVentRelease(pipeBefore, roomBefore,
                         pipeVolume, ROOM_VOLUME_M3, solverPaidSeconds, settings, pumpProfile)
                    : VentilationGasFlow.planScrubberCapture(roomBefore, pipeBefore,
                        ROOM_VOLUME_M3, pipeVolume, solverPaidSeconds, settings);
        } catch (RuntimeException invalid) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                    solverPaidSeconds, pumpProfile, false, null, null, null, null, null, null, "PLAN_EXCEPTION", null);
            return Result.none();
        }
        if (reference.isEmpty()) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                    solverPaidSeconds, pumpProfile, false, null, null, null, null, null, null, "NO_PLAN", null);
            return Result.none();
        }
        PipeGasTransaction.Endpoint source = vent ? pipe : room;
        PipeGasTransaction.Endpoint destination = vent ? room : pipe;
        PipeGasTransfer.Plan spec = reference.get();
        double maximum = spec.packet().totalMoles();
        double pressureLimit = vent
                ? VentilationGasFlow.releaseDestinationPressureLimit(settings, pumpProfile)
                : 5_066.25;
        java.util.Set<GasType> filter = vent ? java.util.Set.of(GasType.values()) : settings.gasesToScrub();
        PipeGasTransaction.Plan transaction;
        try {
            transaction = PipeGasTransaction.plan(source, destination,
                    vent ? pipeVolume : ROOM_VOLUME_M3,
                    vent ? ROOM_VOLUME_M3 : pipeVolume,
                    maximum, pressureLimit, filter).orElse(null);
        } catch (RuntimeException invalid) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                    solverPaidSeconds, pumpProfile, true, spec.packet(), spec.sourceAfter(), spec.destinationAfter(),
                    null, null, null, "TRANSACTION_PLAN_EXCEPTION", null);
            return Result.none();
        }
        if (transaction == null) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                    solverPaidSeconds, pumpProfile, true, spec.packet(), spec.sourceAfter(), spec.destinationAfter(),
                    null, null, null, "REJECTED", null);
            return Result.none();
        }
        PipeGasTransfer.Plan actual = transaction.transfer();
        if (!withinReference(actual, spec)) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                    solverPaidSeconds, pumpProfile, true, spec.packet(), spec.sourceAfter(), spec.destinationAfter(),
                    null, null, null, "REJECTED", null);
            return Result.none();
        }
        try { PipeGasTransaction.commit(source, destination, transaction); }
        catch (RuntimeException failure) {
            recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore,
                    settings, solverPaidSeconds, pumpProfile, true, spec.packet(), spec.sourceAfter(), spec.destinationAfter(),
                    null, null, null, "COMMIT_EXCEPTION", null);
            throw failure;
        }
        recordDecision(level, exactDevice, pipePos, pipe, lane, pipeBefore, roomPos, null, roomBefore, settings,
                solverPaidSeconds, pumpProfile, true, spec.packet(), spec.sourceAfter(), spec.destinationAfter(),
                actual.packet(), pipe.snapshot(), room.snapshot(), "COMMITTED", null);
        return new Result(actual.packet());
    }

    private static void recordDecision(ServerLevel level, VentilationDeviceBlockEntity device, BlockPos pipePos,
            PipeGasTransaction.Endpoint pipeEndpoint, int lane, GasMixture pipeBefore, BlockPos roomPos,
            AtmosphereService.ExteriorBoundary.Kind boundary, GasMixture roomBefore,
            VentilationDeviceConfig.Settings settings, double paidSeconds,
            com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump pump,
            boolean planPresent, GasMixture plannedPacket, GasMixture plannedSourceAfter,
            GasMixture plannedDestinationAfter, GasMixture actualPacket, GasMixture pipeAfter,
            GasMixture roomAfter, String outcome, ExportReceipt export) {
        double powerFactor = pump == null ? Double.NaN : pump.pumpPower();
        device.transferObservation().recordDecision(level.getGameTime(), PowerRuntime.currentSampleTick(level),
                paidSeconds, device, pipePos, String.valueOf(pipeEndpoint.identity()), lane, pipeBefore, roomPos,
                boundary, roomBefore, settings, powerFactor, pump != null, pump,
                device.settingsValid() && device.prototype() != null,
                planPresent, plannedPacket, plannedSourceAfter,
                plannedDestinationAfter, actualPacket, pipeAfter, roomAfter, outcome, export);
    }

    private static com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump ventProfile(
            VentilationDeviceBlockEntity device) {
        var prototype = device.prototype();
        return prototype == null ? null : prototype.gasVentPump();
    }

    private static boolean withinReference(PipeGasTransfer.Plan actual, PipeGasTransfer.Plan spec) {
        for (GasType gas : GasType.values()) {
            double moved = actual.perGasMoved().getOrDefault(gas, 0.0);
            if (moved > spec.perGasMoved().getOrDefault(gas, 0.0)) return false;
        }
        return actual.packet().totalMoles() <= spec.packet().totalMoles()
                && actual.energyMoved() <= spec.energyMoved();
    }

    private static GasMixture copy(GasMixture gas) {
        java.util.EnumMap<GasType, Double> species = new java.util.EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) species.put(type, gas.moles(type));
        return new GasMixture(species, gas.temperatureKelvin(), gas.volumeCubicMeters());
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

