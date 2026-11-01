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
        if (!footprintLoaded(level, origin)) return "Fixture " + origin.toShortString() + "; UNKNOWN (fixture footprint chunks are not fully loaded).";
        var marker = VentilationFixtureSavedData.existing(level).flatMap(data -> data.entry(origin));
        if (marker.isEmpty()) return "No saved fixture marker at " + origin.toShortString() + ".";
        var entry = marker.get();
        BlockPos scrubPos = origin.offset(PLAN.scrubber()), ventPos = origin.offset(PLAN.vent());
        if (!(level.getBlockEntity(scrubPos) instanceof VentilationDeviceBlockEntity scrubber)
                || !entry.scrubberUUID().equals(scrubber.deviceId())
                || !(level.getBlockEntity(ventPos) instanceof VentilationDeviceBlockEntity vent)
                || !entry.ventUUID().equals(vent.deviceId()))
            return "Fixture " + origin.toShortString() + " initialized=" + entry.initialized() + "; devices STALE (identity mismatch).";

        StringBuilder out = new StringBuilder("Fixture ").append(origin.toShortString())
                .append(" initialized=").append(entry.initialized());
        if(entry.firelockVariant()) {
            BlockPos door=origin.offset(VentilationFixturePlan.firelockPlan().firelock());
            if(!(level.getBlockEntity(door) instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity firelock)
                    || !firelock.identity().equals(entry.firelockUUID())) out.append("; firelock STALE (identity mismatch)");
            else {
                var state=firelock.state();
                out.append("; firelock position=").append(door.toShortString()).append(" phase=").append(state==null?"UNKNOWN":state.phase())
                        .append(" closedLatch=").append(state!=null&&com.juicyslew.moonstation14.ms14.doors.FirelockController.authoritativeLatch(state))
                        .append(" power=");
                var meter=firelock.poweredObservation(); var receiver=firelock.prototype()==null?null:firelock.prototype().apcPowerReceiver();
                boolean knownPower=state!=null&&receiver!=null&&meter.known();
                out.append(knownPower?"known delivered="+fmt(meter.deliveredWatts())+"/requested="+fmt(receiver.requestedWatts())+" W":"UNKNOWN");
                if(state!=null)out.append(" locked=").append(state.locked()).append(" localPressureWarning=").append(state.pressureWarning()).append(" localTemperatureWarning=").append(state.temperatureWarning());
                if(state!=null)out.append(" ordinaryOpenAdmission=").append(knownPower?(state.withPower(meter.deliveredWatts()==receiver.requestedWatts()).canOpen(false)?"admitted":"denied"):"UNKNOWN (denied)")
                        .append("; pryHint=").append(!knownPower?"UNKNOWN (requires known power observation)":meter.deliveredWatts()==receiver.requestedWatts()?"barehand denied; strong crowbar-capability may pry":"barehand pry only while known unpowered");
                BlockPos first=door.relative(level.getBlockState(door).getValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.FACING));
                BlockPos second=door.relative(level.getBlockState(door).getValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.FACING).getOpposite());
                appendDifferential(out,level,first,second);
            }
        }
        if(entry.firelockVariant()) {
            BlockPos alarmPos=origin.offset(VentilationFixturePlan.firelockPlan().alarm());
            if(level.getBlockEntity(alarmPos) instanceof AirAlarmBlockEntity alarm && java.util.Objects.equals(entry.alarmUUID(),alarm.controllerId()))
                out.append("; receivedSeverity=").append(alarm.alarmSeverity());
            else out.append("; receivedSeverity=UNKNOWN");
            appendPower(out, level, scrubber, vent, origin, entry);
            return out.toString();
        }
        appendRoom(out, "source", level, PLAN.sourceCells(), origin);
        appendRoom(out, "recipient", level, PLAN.recipientCells(), origin);
        if (scrubber.config() == null || vent.config() == null) return out.append("; device settings UNKNOWN (prototype/config unavailable)").toString();
        out.append("; scrubber enabled=").append(scrubber.config().settings().configuredEnabled())
                .append(" mode=").append(scrubber.config().settings().scrubberMode())
                .append("; vent enabled=").append(vent.config().settings().configuredEnabled())
                .append(" targetKPa=").append(fmt(vent.config().settings().targetPressureKPa()))
                .append(" rateM3s=").append(fmt(vent.config().settings().volumeRateM3PerSecond()));
        appendPipe(out, "exposed", level, origin.offset(PLAN.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position()), VisualPipeBlockEntity.PRIMARY);
        appendSealedPipe(out, level, origin.offset(PLAN.pipes().stream().filter(VentilationFixturePlan.PipeNode::concealed).findFirst().orElseThrow().position()));
        appendPower(out, level, scrubber, vent, origin, entry);
        return out.toString();
    }

    private static void appendDifferential(StringBuilder out,ServerLevel level,BlockPos first,BlockPos second){
        var a=com.juicyslew.moonstation14.ms14.doors.FirelockController.sampleEnvironment(level,first);
        var b=com.juicyslew.moonstation14.ms14.doors.FirelockController.sampleEnvironment(level,second);
        out.append(" localSides=");if(a.observation()!=com.juicyslew.moonstation14.ms14.doors.FirelockState.Observation.AVAILABLE
                ||b.observation()!=com.juicyslew.moonstation14.ms14.doors.FirelockState.Observation.AVAILABLE){out.append("UNKNOWN (both adjacent samples required)");return;}
        double dp=Math.abs(a.pressureKpa()-b.pressureKpa()),dt=Math.abs(a.temperatureK()-b.temperatureK());
        out.append("P=").append(fmt(a.pressureKpa())).append("/").append(fmt(b.pressureKpa())).append(" kPa dP=").append(fmt(dp)
                ).append(" kPa T=").append(fmt(a.temperatureK())).append("/").append(fmt(b.temperatureK())).append(" K dT=").append(fmt(dt)).append(" K");
    }

    private static boolean footprintLoaded(ServerLevel level, BlockPos origin) {
        for (int x=0;x<VentilationFixturePlan.WIDTH;x++) for (int y=0;y<VentilationFixturePlan.HEIGHT;y++) for(int z=0;z<VentilationFixturePlan.DEPTH;z++) {
            BlockPos p=origin.offset(x,y,z);
            if (p.getY()<level.getMinBuildHeight() || p.getY()>=level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(p)
                    || level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null) return false;
        }
        return true;
    }

    private static void appendRoom(StringBuilder out, String label, ServerLevel level, java.util.List<BlockPos> cells, BlockPos origin) {
        GasMixture total = GasMixture.vacuum();
        // A successful finite endpoint proves enclosure before samples are accepted as physical measurements.
        boolean enclosed = cells.stream().anyMatch(pos -> AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(pos)).isPresent());
        if (!enclosed) { out.append("; ").append(label).append(" UNKNOWN/outside (0/").append(cells.size()).append(" finite)"); return; }
        for (BlockPos relative : cells) {
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, origin.offset(relative));
            if (reading.isEmpty() || reading.get().status() != AtmosphereReading.Status.FINITE) {
                out.append("; ").append(label).append(" UNKNOWN (finite cells incomplete)"); return;
            }
            total = merge(total, reading.get().mixture());
        }
        out.append("; ").append(label).append(' ').append(cells.size()).append('/').append(cells.size())
                .append(" finite cells ").append(PLAN.testGasLabel()).append('=').append(fmt(total.moles(PLAN.testGas())))
                .append(" mol total=").append(fmt(total.totalMoles())).append(" mol averagePressure=").append(fmt(averagePressureKpa(total, cells.size())))
                .append(" kPa at ").append(fmt(total.temperatureKelvin())).append(" K");
    }

    private static GasMixture merge(GasMixture a, GasMixture b) {
        var m = new java.util.EnumMap<GasType, Double>(GasType.class);
        m.putAll(a.gasMoles()); b.gasMoles().forEach((gas, n) -> m.merge(gas, n, Double::sum));
        GasMixture combined = new GasMixture(m, b.temperatureKelvin());
        double energy = a.thermalEnergy() + b.thermalEnergy();
        if (combined.totalMoles() > 0 && Double.isFinite(energy)) combined = combined.withThermalEnergy(energy);
        return combined;
    }

    private static void appendPipe(StringBuilder out, String label, ServerLevel level, BlockPos first, int lane) {
        Optional<LoadedPipeComponent.Snapshot> snapshot = LoadedPipeComponent.discover(level, first, lane);
        out.append("; ").append(label).append(" pipe=");
        if (snapshot.isEmpty()) { out.append("UNKNOWN (component unvalidated)"); return; }
        var s = snapshot.get();
        GasMixture gas = GasMixture.vacuum();
        for (var node : s.nodes()) gas = merge(gas, node.mixture());
        java.util.Set<PipeTopology.NodeKey> expected = PLAN.pipes().stream()
                .map(p -> new PipeTopology.NodeKey(levelPosition(first, p.position()), PipeTopology.Layer.PRIMARY))
                .collect(java.util.stream.Collectors.toSet());
        boolean matches = s.nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet()).equals(expected);
        long expectedCount = PLAN.pipes().size();
        out.append(s.nodes().size()).append('/').append(expectedCount)
                .append(matches ? " expected" : " MISMATCH")
                .append(" connected nodes ").append(PLAN.testGasLabel()).append('=').append(fmt(gas.moles(PLAN.testGas()))).append(" mol total=")
                .append(fmt(gas.totalMoles())).append(" mol pressure=").append(fmt(gas.pressureKpa(s.totalVolume()))).append(" kPa");
    }

    private static BlockPos levelPosition(BlockPos first, BlockPos relative) {
        // first is the exposed pipe anchor; recover fixture origin by subtracting that anchor.
        BlockPos anchor = PLAN.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position();
        return first.offset(relative.getX() - anchor.getX(), relative.getY() - anchor.getY(), relative.getZ() - anchor.getZ());
    }

    private static void appendPower(StringBuilder out, ServerLevel level, VentilationDeviceBlockEntity scrubber,
                                    VentilationDeviceBlockEntity vent, BlockPos origin, VentilationFixtureSavedData.Entry entry) {
        var scrubberPower = PowerRuntime.ventilationMeter(level, scrubber);
        var ventPower = PowerRuntime.ventilationMeter(level, vent);
        out.append("; scrubber power=").append(powerText(scrubberPower));
        out.append("; vent power=").append(powerText(ventPower));
        if (!(level.getBlockEntity(origin.offset(PLAN.alarm())) instanceof AirAlarmBlockEntity alarm)
                || alarm.prototype() == null || !alarm.deviceLinksValid()
                || entry.alarmUUID()!=null&&!entry.alarmUUID().equals(alarm.controllerId())) {
            out.append("; alarm power=UNKNOWN (alarm/paired identity invalid)");
            return;
        }
        var links=alarm.deviceLinks();
        boolean paired = links.stream().anyMatch(link -> link.deviceId().equals(scrubber.deviceId()) && alarm.resolveDeviceLink(link) == scrubber)
                && links.stream().anyMatch(link -> link.deviceId().equals(vent.deviceId()) && alarm.resolveDeviceLink(link) == vent);
        if(entry.firelockVariant()) paired &= links.stream().anyMatch(link -> link.role()==com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmDeviceLinks.EndpointRole.FIRELOCK
                && entry.firelockUUID()!=null && link.deviceId().equals(entry.firelockUUID())
                && com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmDeviceLinks.resolvesEndpoint(level,link));
        if (!paired) { out.append("; alarm power=UNKNOWN (paired identity mismatch)"); return; }
        out.append("; alarm power=").append(powerText(PowerRuntime.airAlarmMeter(level, alarm)));
    }

    static String powerText(PowerRuntime.VentMeterObservation observation) {
        return observation.known() ? "meter known delivered=" + fmt(observation.deliveredWatts()) + " W"
                : "UNKNOWN (meter unavailable/stale/not delivered)";
    }

    static double averagePressureKpa(GasMixture total, int cellCount) {
        return total.pressureKpa(cellCount * 1.0);
    }

    private static void appendSealedPipe(StringBuilder out, ServerLevel level, BlockPos pos) {
        var entry = LoadedSealedWallGasOwner.activeSnapshot(level, pos, PipeTopology.Layer.PRIMARY);
        out.append("; concealed pipe=");
        if (entry.isEmpty()) { out.append("UNKNOWN (owner inactive)"); return; }
        var gas = entry.get().mixture();
        out.append("owner active ").append(PLAN.testGasLabel()).append('=').append(fmt(gas.moles(PLAN.testGas()))).append(" mol total=")
                .append(fmt(gas.totalMoles())).append(" mol pressure=").append(fmt(gas.pressureKpa(entry.get().volume()))).append(" kPa");
    }

    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}
