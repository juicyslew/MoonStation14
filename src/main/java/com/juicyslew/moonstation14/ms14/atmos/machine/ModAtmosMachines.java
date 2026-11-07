package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;

/** Prototype metadata only. Runtime/bootstrap registration is intentionally owned by the integration worker. */
public final class ModAtmosMachines {
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name); }
    public static final PrototypeType<AtmosMachineProfile> TYPE = new PrototypeType<>(id("atmos_machine"), "moonstation14/atmos_machine", AtmosMachineProfile.CODEC,
            catalog -> catalog.values().forEach(ModAtmosMachines::validate));
    private ModAtmosMachines() { }
    public static PrototypeCatalog<AtmosMachineProfile> catalog(Level level) {
        return (level.isClientSide() ? com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager() : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
    public static void validate(AtmosMachineProfile p) {
        if (p.kind() == null || p.ports() == null) throw new IllegalArgumentException("missing atmos machine kind/ports");
        Set<AtmosMachineProfile.PortName> names = new HashSet<>();
        for (var port : p.ports()) if (port == null || port.name() == null || port.direction() == null
                || !finitePositive(port.volumeM3()) || !names.add(port.name())) throw new IllegalArgumentException("invalid or duplicate atmos machine port");
        if (p.networkNodes() == null) throw new IllegalArgumentException("missing network nodes");
        Set<AtmosMachineProfile.NetworkNodeName> nodeNames = new HashSet<>();
        for (var node : p.networkNodes()) if (node == null || node.name() == null || node.group() == null || !nodeNames.add(node.name())) throw new IllegalArgumentException("invalid or duplicate network node");
        if (p.power() != null && (!Double.isFinite(p.power().watts()) || p.power().watts() < 0)) throw new IllegalArgumentException("invalid power");
        if (p.activationAudio().isPresent()) {
            var audio = p.activationAudio().get();
            if (audio.sound().isBlank() || !Double.isFinite(audio.volume()) || audio.volume() < 0
                    || !Float.isFinite(audio.pitchVariation()) || audio.pitchVariation() < 0 || audio.pitchVariation() > 1
                    || p.kind() != AtmosMachineProfile.Kind.MANUAL_VALVE)
                throw new IllegalArgumentException("invalid activation audio composition");
        }
        if (p.ambientAudio().isPresent()) {
            var audio = p.ambientAudio().get();
            boolean supported = switch (p.kind()) {
                case PRESSURE_PUMP, VOLUME_PUMP, MIXER, FILTER -> audio.gate() == com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio.Gate.TRANSFER;
                case MANUAL_VALVE -> audio.gate() == com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio.Gate.OPEN;
                case TEG_CENTER -> audio.gate() == com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio.Gate.TEG_DISPLAY;
                default -> false;
            };
            if (!supported || audio.sound().isBlank() || !Float.isFinite(audio.volumeDb())
                    || !Float.isFinite(audio.range()) || audio.range() <= 0)
                throw new IllegalArgumentException("invalid/unsupported atmos ambient composition");
        }
        if (p.pump() != null && !validPump(p.pump())) throw new IllegalArgumentException("invalid pump");
        if (p.mixer() != null && (!finitePositive(p.mixer().targetPressureKpa()) || !finitePositive(p.mixer().maxPressureKpa()) || p.mixer().targetPressureKpa() > p.mixer().maxPressureKpa() || p.mixer().ratios().size() != 2 || p.mixer().ratios().stream().anyMatch(x -> !Double.isFinite(x) || x < 0 || x > 1) || Math.abs(p.mixer().ratios().stream().mapToDouble(Double::doubleValue).sum() - 1) > 1e-9)) throw new IllegalArgumentException("invalid mixer");
        if (p.filter() != null && (!finitePositive(p.filter().transferRate()) || !finitePositive(p.filter().maxTransferRate()) || p.filter().transferRate() > p.filter().maxTransferRate())) throw new IllegalArgumentException("invalid filter");
        if (p.thermostat() != null && (!finitePositive(p.thermostat().inputPowerWatts()) || !Double.isFinite(p.thermostat().coefficientOfPerformance()) || p.thermostat().coefficientOfPerformance() == 0 || !finitePositive(p.thermostat().minimumTemperatureK()) || !finitePositive(p.thermostat().maximumTemperatureK()) || p.thermostat().minimumTemperatureK() >= p.thermostat().maximumTemperatureK() || !finitePositive(p.thermostat().targetK()) || p.thermostat().targetK() < p.thermostat().minimumTemperatureK() || p.thermostat().targetK() > p.thermostat().maximumTemperatureK() || !Double.isFinite(p.thermostat().toleranceK()) || p.thermostat().toleranceK() < 0)) throw new IllegalArgumentException("invalid thermostat");
        if (p.teg() != null && (!Double.isFinite(p.teg().efficiency()) || p.teg().efficiency() < 0 || p.teg().efficiency() > 1 || !finitePositive(p.teg().powerFactor()) || !finitePositive(p.teg().rampFactor()) || !finitePositive(p.teg().minimumPowerWatts()) || !Double.isFinite(p.teg().smoothing()) || p.teg().smoothing() < 0 || p.teg().smoothing() > 1)) throw new IllegalArgumentException("invalid TEG");
        if (p.heatExchanger() != null && (!finitePositive(p.heatExchanger().conductivityMolesPerKpaPerS()) || !finitePositive(p.heatExchanger().convectionCoefficientJPerKPerS()) || !finitePositive(p.heatExchanger().radiationCoefficient()))) throw new IllegalArgumentException("invalid heat exchanger");
        boolean valid = switch (p.kind()) {
            case TEG_CENTER -> p.ports().isEmpty() && Set.copyOf(p.networkNodes()).equals(Set.of(
                    new AtmosMachineProfile.NetworkNode(AtmosMachineProfile.NetworkNodeName.OUTPUT, AtmosMachineProfile.NetworkGroup.HV_POWER),
                    new AtmosMachineProfile.NetworkNode(AtmosMachineProfile.NetworkNodeName.TEG, AtmosMachineProfile.NetworkGroup.TEG))) && only(p, true, false, false, false, false, false, true);
            case TEG_CIRCULATOR -> binary(p) && Set.copyOf(p.networkNodes()).equals(Set.of(new AtmosMachineProfile.NetworkNode(AtmosMachineProfile.NetworkNodeName.CIRCULATOR, AtmosMachineProfile.NetworkGroup.TEG))) && only(p, false, false, false, false, false, false, false);
            case PRESSURE_PUMP -> binary(p) && p.power() != null && p.pump() != null && p.pump().mode() == AtmosMachineProfile.PumpMode.PRESSURE && p.mixer() == null && p.filter() == null && p.valve() == null && p.thermostat() == null && p.teg() == null && p.heatExchanger() == null;
            case VOLUME_PUMP -> binary(p) && p.power() != null && p.pump() != null && p.pump().mode() == AtmosMachineProfile.PumpMode.VOLUME && p.mixer() == null && p.filter() == null && p.valve() == null && p.thermostat() == null && p.teg() == null && p.heatExchanger() == null;
            case MANUAL_VALVE -> binary(p) && p.power() == null && p.pump() == null && p.mixer() == null && p.filter() == null && p.valve() != null && p.thermostat() == null && p.teg() == null && p.heatExchanger() == null;
            case MIXER -> ports(p, AtmosMachineProfile.PortName.INLET, AtmosMachineProfile.PortName.FILTER, AtmosMachineProfile.PortName.OUTLET) && p.power() == null && p.pump() == null && p.mixer() != null && p.filter() == null && p.valve() == null && p.thermostat() == null && p.teg() == null && p.heatExchanger() == null;
            case FILTER -> ports(p, AtmosMachineProfile.PortName.INLET, AtmosMachineProfile.PortName.FILTER, AtmosMachineProfile.PortName.OUTLET) && p.power() == null && p.pump() == null && p.mixer() == null && p.filter() != null && p.valve() == null && p.thermostat() == null && p.teg() == null && p.heatExchanger() == null;
            case HEATER -> thermostat(p, AtmosMachineProfile.ThermostatMode.HEATER);
            case FREEZER -> thermostat(p, AtmosMachineProfile.ThermostatMode.FREEZER);
            case PASSIVE_HEAT_EXCHANGER -> binary(p) && p.power() == null && p.pump() == null && p.mixer() == null && p.filter() == null && p.valve() == null && p.thermostat() == null && p.teg() == null && p.heatExchanger() != null;
            case GAS_PIPE_MANIFOLD -> manifold(p);
        };
        if (!valid || (p.kind() != AtmosMachineProfile.Kind.TEG_CENTER && p.kind() != AtmosMachineProfile.Kind.TEG_CIRCULATOR && !p.networkNodes().isEmpty()))
            throw new IllegalArgumentException("incompatible atmos-machine composition for " + p.kind());
    }
    private static boolean binary(AtmosMachineProfile p) { return ports(p, AtmosMachineProfile.PortName.INLET, AtmosMachineProfile.PortName.OUTLET); }
    private static boolean validPump(AtmosMachineProfile.Pump p) {
        if (p.mode() == AtmosMachineProfile.PumpMode.PRESSURE) return p.targetPressureKpa().filter(ModAtmosMachines::finitePositive).isPresent()
                && p.maxPressureKpa().filter(ModAtmosMachines::finitePositive).isPresent() && p.targetPressureKpa().get() <= p.maxPressureKpa().get()
                && (p.maxTransferRate().isEmpty() || finitePositive(p.maxTransferRate().get())) && p.transferRate().isEmpty() && p.leakRatio().isEmpty()
                && p.lowerThresholdKpa().isEmpty() && p.higherThresholdKpa().isEmpty() && p.overclockThresholdKpa().isEmpty();
        return p.targetPressureKpa().isEmpty() && p.maxPressureKpa().isEmpty() && p.transferRate().filter(ModAtmosMachines::finitePositive).isPresent()
                && p.maxTransferRate().filter(ModAtmosMachines::finitePositive).isPresent() && p.transferRate().get() <= p.maxTransferRate().get()
                && p.leakRatio().filter(x -> Double.isFinite(x) && x >= 0 && x <= 1).isPresent()
                && p.lowerThresholdKpa().filter(x -> Double.isFinite(x) && x >= 0).isPresent()
                && p.higherThresholdKpa().filter(ModAtmosMachines::finitePositive).isPresent() && p.lowerThresholdKpa().get() < p.higherThresholdKpa().get()
                && p.overclockThresholdKpa().filter(ModAtmosMachines::finitePositive).isPresent();
    }
    private static boolean ports(AtmosMachineProfile p, AtmosMachineProfile.PortName... expected) { return p.ports().size() == expected.length && Set.copyOf(p.ports().stream().map(AtmosMachineProfile.Port::name).toList()).equals(Set.of(expected)); }
    private static boolean thermostat(AtmosMachineProfile p, AtmosMachineProfile.ThermostatMode mode) {
        return ports(p, AtmosMachineProfile.PortName.PIPE) && p.power() == null && p.pump() == null && p.mixer() == null && p.filter() == null && p.valve() == null && p.thermostat() != null && p.thermostat().mode() == mode && (mode == AtmosMachineProfile.ThermostatMode.HEATER ? p.thermostat().coefficientOfPerformance() > 0 : p.thermostat().coefficientOfPerformance() < 0) && p.teg() == null && p.heatExchanger() == null;
    }
    private static boolean manifold(AtmosMachineProfile p) {
        if (p.ports().size() != 6 || p.power() != null || p.pump() != null || p.mixer() != null || p.filter() != null
                || p.valve() != null || p.thermostat() != null || p.teg() != null || p.heatExchanger() != null
                || p.pipeLayers().isEmpty()) return false;
        var layers = p.pipeLayers().get();
        if (layers.count() != 3 || !layers.locked()) return false;
        var southPeers = java.util.List.of(AtmosMachineProfile.PortName.SOUTH0, AtmosMachineProfile.PortName.SOUTH1, AtmosMachineProfile.PortName.SOUTH2);
        var northPeers = java.util.List.of(AtmosMachineProfile.PortName.NORTH0, AtmosMachineProfile.PortName.NORTH1, AtmosMachineProfile.PortName.NORTH2);
        for (int lane = 0; lane < 3; lane++) {
            var northName = AtmosMachineProfile.PortName.values()[AtmosMachineProfile.PortName.NORTH0.ordinal() + lane];
            var southName = AtmosMachineProfile.PortName.values()[AtmosMachineProfile.PortName.SOUTH0.ordinal() + lane];
            var north = p.ports().stream().filter(port -> port.name() == northName).findFirst().orElse(null);
            var south = p.ports().stream().filter(port -> port.name() == southName).findFirst().orElse(null);
            var layer = com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.values()[lane];
            if (north == null || south == null || north.direction() != AtmosMachineProfile.PortDirection.NORTH
                    || south.direction() != AtmosMachineProfile.PortDirection.SOUTH || north.fixedLayer().orElse(null) != layer
                    || south.fixedLayer().orElse(null) != layer || north.volumeM3() != 0.05 || south.volumeM3() != 0.05
                    || !north.alwaysReachablePeers().equals(southPeers) || !south.alwaysReachablePeers().equals(northPeers)) return false;
        }
        return p.networkNodes().isEmpty();
    }
    private static boolean only(AtmosMachineProfile p, boolean hasPower, boolean hasPump, boolean hasMixer, boolean hasFilter, boolean hasThermostat, boolean hasExchanger, boolean hasTeg) {
        return (p.power() != null) == hasPower && (p.pump() != null) == hasPump && (p.mixer() != null) == hasMixer && (p.filter() != null) == hasFilter && (p.thermostat() != null) == hasThermostat && (p.heatExchanger() != null) == hasExchanger && (p.teg() != null) == hasTeg && p.valve() == null;
    }
    private static boolean finitePositive(double value) { return Double.isFinite(value) && value > 0; }
}
