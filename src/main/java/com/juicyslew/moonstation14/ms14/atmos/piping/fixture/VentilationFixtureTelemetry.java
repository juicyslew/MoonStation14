package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;

import java.util.Optional;

/** Read-only measurement of the explicitly admitted ventilation fixture. */
public final class VentilationFixtureTelemetry {
    private static final VentilationFixturePlan PLAN = VentilationFixturePlan.standard();
    private VentilationFixtureTelemetry() { }

    public static String status(ServerLevel level, BlockPos origin) {
        var marker = VentilationFixtureSavedData.existing(level).flatMap(data -> data.entry(origin));
        if (marker.isEmpty()) return "No saved fixture marker at " + origin.toShortString() + ".";
        var entry = marker.get();
        // Validate the entire footprint before any block entity or atmosphere lookup can request ownership.
        for (var cell : PLAN.blocks()) {
            BlockPos pos = origin.offset(cell.pos());
            if (level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null)
                return "Fixture " + origin.toShortString() + " initialized=" + entry.initialized() + "; telemetry UNKNOWN (footprint chunks unloaded).";
        }
        BlockPos scrubPos = origin.offset(PLAN.scrubber()), ventPos = origin.offset(PLAN.vent());
        if (!(level.getBlockEntity(scrubPos) instanceof VentilationDeviceBlockEntity scrubber)
                || !entry.scrubberUUID().equals(scrubber.deviceId())
                || !(level.getBlockEntity(ventPos) instanceof VentilationDeviceBlockEntity vent)
                || !entry.ventUUID().equals(vent.deviceId()))
            return "Fixture " + origin.toShortString() + " initialized=" + entry.initialized() + "; devices STALE (identity mismatch).";

        StringBuilder out = new StringBuilder("Fixture ").append(origin.toShortString())
                .append(" initialized=").append(entry.initialized());
        appendRoom(out, "source", roomTotals(level, origin, true), PLAN.sourceCells().size());
        appendRoom(out, "recipient", roomTotals(level, origin, false), PLAN.recipientCells().size());
        if (scrubber.config() == null || vent.config() == null) return out.append("; device settings UNKNOWN (prototype/config unavailable)").toString();
        out.append("; scrubber enabled=").append(scrubber.config().settings().configuredEnabled())
                .append(" mode=").append(scrubber.config().settings().scrubberMode())
                .append("; vent enabled=").append(vent.config().settings().configuredEnabled())
                .append(" targetKPa=").append(fmt(vent.config().settings().targetPressureKPa()))
                .append(" rateM3s=").append(fmt(vent.config().settings().volumeRateM3PerSecond()));
        appendPipe(out, "exposed", level, origin.offset(PLAN.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position()), VisualPipeBlockEntity.PRIMARY);
        appendSealedPipe(out, level, origin.offset(PLAN.pipes().stream().filter(VentilationFixturePlan.PipeNode::concealed).findFirst().orElseThrow().position()));
        appendPower(out, level, scrubber, vent, origin);
        return out.toString();
    }

    /** Read-only finite total for the fixture source or recipient room; unavailable data stays UNKNOWN. */
    public static Optional<GasMixture> roomTotals(ServerLevel level, BlockPos origin, boolean source) {
        java.util.List<BlockPos> cells = source ? PLAN.sourceCells() : PLAN.recipientCells();
        for (BlockPos relative : cells) {
            BlockPos pos = origin.offset(relative);
            if (level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null)
                return Optional.empty();
        }
        GasMixture total = GasMixture.vacuum();
        // A successful finite endpoint proves enclosure before samples are accepted as physical measurements.
        boolean enclosed = cells.stream().anyMatch(pos -> AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(pos)).isPresent());
        if (!enclosed) return Optional.empty();
        for (BlockPos relative : cells) {
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, origin.offset(relative));
            if (reading.isEmpty() || reading.get().status() != AtmosphereReading.Status.FINITE) {
                return Optional.empty();
            }
            total = merge(total, reading.get().mixture());
        }
        return Optional.of(total);
    }

    private static void appendRoom(StringBuilder out, String label, Optional<GasMixture> total, int cellCount) {
        if (total.isEmpty()) { out.append("; ").append(label).append(" UNKNOWN (finite cells unavailable)"); return; }
        GasMixture mixture = total.get();
        out.append("; ").append(label).append(' ').append(cellCount).append('/').append(cellCount)
                .append(" finite cells ").append(PLAN.testGasLabel()).append('=').append(fmt(mixture.moles(PLAN.testGas())))
                .append(" mol total=").append(fmt(mixture.totalMoles())).append(" mol averagePressure=").append(fmt(averagePressureKpa(mixture, cellCount)))
                .append(" kPa at ").append(fmt(mixture.temperatureKelvin())).append(" K");
    }

    public static GasMixture merge(GasMixture a, GasMixture b) {
        var m = new java.util.EnumMap<GasType, Double>(GasType.class);
        m.putAll(a.gasMoles()); b.gasMoles().forEach((gas, n) -> m.merge(gas, n, Double::sum));
        GasMixture combined = new GasMixture(m, b.temperatureKelvin());
        double energy = a.thermalEnergy() + b.thermalEnergy();
        if (combined.totalMoles() > 0 && Double.isFinite(energy)) combined = combined.withThermalEnergy(energy);
        return combined;
    }

    private static void appendPipe(StringBuilder out, String label, ServerLevel level, BlockPos first, int lane) {
        var start = new PipeTopology.NodeKey(first, lane == VisualPipeBlockEntity.SECONDARY
                ? PipeTopology.Layer.SECONDARY : PipeTopology.Layer.PRIMARY);
        var snapshot = LoadedPipeGasWork.capture(level, java.util.List.of(new LoadedPipeGasWork.Port(start)));
        out.append("; ").append(label).append(" pipe=");
        if (snapshot.isEmpty()) { out.append("UNKNOWN (component unvalidated)"); return; }
        var s = snapshot.get();
        var component = s.componentFor(start).orElse(null);
        if (component == null) { out.append("UNKNOWN (component metadata unavailable)"); return; }
        // A connected component is one shared inventory. Summing node projections would
        // introduce rounding and treating each as independent gas would double-count it.
        GasMixture gas = s.inventory().network(start).map(PipeGasInventory.Network::mixture).orElse(null);
        if (gas == null) { out.append("UNKNOWN (component gas unavailable)"); return; }
        java.util.Set<PipeTopology.NodeKey> expected = PLAN.pipes().stream()
                .map(p -> new PipeTopology.NodeKey(levelPosition(first, p.position()), PipeTopology.Layer.PRIMARY))
                .collect(java.util.stream.Collectors.toSet());
        boolean matches = component.nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet()).equals(expected);
        long expectedCount = PLAN.pipes().size();
        double totalVolume = component.nodes().stream().mapToDouble(LoadedPipeComponent.Node::volume).sum();
        out.append(component.nodes().size()).append('/').append(expectedCount)
                .append(matches ? " expected" : " MISMATCH")
                .append(" connected nodes ").append(PLAN.testGasLabel()).append('=').append(fmt(gas.moles(PLAN.testGas()))).append(" mol total=")
                .append(fmt(gas.totalMoles())).append(" mol pressure=").append(fmt(gas.pressureKpa(totalVolume))).append(" kPa");
    }

    private static BlockPos levelPosition(BlockPos first, BlockPos relative) {
        // first is the exposed pipe anchor; recover fixture origin by subtracting that anchor.
        BlockPos anchor = PLAN.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position();
        return first.offset(relative.getX() - anchor.getX(), relative.getY() - anchor.getY(), relative.getZ() - anchor.getZ());
    }

    private static void appendPower(StringBuilder out, ServerLevel level, VentilationDeviceBlockEntity scrubber,
                                    VentilationDeviceBlockEntity vent, BlockPos origin) {
        var scrubberPower = PowerRuntime.ventilationMeter(level, scrubber);
        var ventPower = PowerRuntime.ventilationMeter(level, vent);
        out.append("; scrubber power=").append(powerText(scrubberPower));
        out.append("; vent power=").append(powerText(ventPower));
        if (!(level.getBlockEntity(origin.offset(PLAN.alarm())) instanceof AirAlarmBlockEntity alarm)
                || alarm.prototype() == null || !alarm.deviceLinksValid() || alarm.deviceLinks().size() != 2) {
            out.append("; alarm power=UNKNOWN (alarm/paired identity invalid)");
            return;
        }
        boolean paired = alarm.deviceLinks().stream().anyMatch(link -> link.deviceId().equals(scrubber.deviceId()) && alarm.resolveDeviceLink(link) == scrubber)
                && alarm.deviceLinks().stream().anyMatch(link -> link.deviceId().equals(vent.deviceId()) && alarm.resolveDeviceLink(link) == vent);
        if (!paired) { out.append("; alarm power=UNKNOWN (paired identity mismatch)"); return; }
        out.append("; alarm power=").append(powerText(PowerRuntime.airAlarmMeter(level, alarm)));
    }

    static String powerText(PowerRuntime.VentMeterObservation observation) {
        return observation.known() ? "meter known delivered=" + fmt(observation.deliveredWatts()) + " W"
                : "UNKNOWN (meter unavailable/stale/not delivered)";
    }

    public static double averagePressureKpa(GasMixture total, int cellCount) {
        return total.pressureKpa(cellCount * 1.0);
    }

    private static void appendSealedPipe(StringBuilder out, ServerLevel level, BlockPos pos) {
        var entry = LoadedSealedWallGasOwner.activeSnapshot(level, pos, PipeTopology.Layer.PRIMARY);
        out.append("; concealed pipe=");
        if (entry.isEmpty()) { out.append("UNKNOWN (owner inactive)"); return; }
        var gas = PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot)
                .map(inventory -> inventory.nodeShare(entry.get().node())).orElse(null);
        if (gas == null) { out.append("UNKNOWN (owner gas unavailable)"); return; }
        out.append("owner active ").append(PLAN.testGasLabel()).append('=').append(fmt(gas.moles(PLAN.testGas()))).append(" mol total=")
                .append(fmt(gas.totalMoles())).append(" mol pressure=").append(fmt(gas.pressureKpa(entry.get().volume()))).append(" kPa");
    }

    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}

