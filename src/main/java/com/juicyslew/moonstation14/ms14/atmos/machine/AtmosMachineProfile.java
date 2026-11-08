package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Resolved, optional SS14 component composition and the typed values consumed by atmos-machine behavior. */
public record AtmosMachineProfile(Kind kind, List<Port> ports, List<NetworkNode> networkNodes,
        Power power, Pump pump, Mixer mixer, Filter filter, Valve valve, Thermostat thermostat,
        Teg teg, HeatExchanger heatExchanger, Visual visual, AtmosMachineCadence.Settings simulation,
        Optional<PipeLayers> pipeLayers, Optional<ActivationAudio> activationAudio, Optional<AtmosAmbientAudio> ambientAudio) {
    public enum Kind { TEG_CENTER, TEG_CIRCULATOR, PRESSURE_PUMP, VOLUME_PUMP, MANUAL_VALVE, MIXER, FILTER, HEATER, FREEZER, PASSIVE_HEAT_EXCHANGER, GAS_PIPE_MANIFOLD }
    public enum PortName { INLET, OUTLET, FILTER, PIPE, NORTH0, NORTH1, NORTH2, SOUTH0, SOUTH1, SOUTH2 }
    public enum PortDirection { NORTH, SOUTH, EAST, WEST }
    public enum NetworkNodeName { OUTPUT, TEG, CIRCULATOR }
    public enum NetworkGroup { HV_POWER, TEG }
    public enum PumpMode { PRESSURE, VOLUME }
    public enum ThermostatMode { HEATER, FREEZER }

    /** Resolved AtmosPipeLayers capability (SS14: three layers, Primary, Screwing, one second). */
    public record PipeLayers(int count, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer initialLayer,
                             boolean locked, int changeDurationTicks) {
        public static final Codec<PipeLayers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("count", 3).forGetter(PipeLayers::count),
                ProfileCodecHelpers.enumCodec(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.class)
                        .optionalFieldOf("initialLayer", com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY).forGetter(PipeLayers::initialLayer),
                Codec.BOOL.optionalFieldOf("locked", false).forGetter(PipeLayers::locked),
                Codec.INT.optionalFieldOf("changeDurationTicks", 20).forGetter(PipeLayers::changeDurationTicks)
        ).apply(i, PipeLayers::new));
        public PipeLayers {
            if (count < 1 || count > 3 || changeDurationTicks < 0 || initialLayer == null
                    || initialLayer.ordinal() >= count) throw new IllegalArgumentException("invalid pipe-layer composition");
        }
    }

    public record Port(PortName name, PortDirection direction, double volumeM3,
                       Optional<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer> fixedLayer,
                       List<PortName> alwaysReachablePeers) {
        public static final Codec<Port> CODEC = RecordCodecBuilder.create(i -> i.group(
                ProfileCodecHelpers.enumCodec(PortName.class).fieldOf("name").forGetter(Port::name),
                ProfileCodecHelpers.enumCodec(PortDirection.class).fieldOf("direction").forGetter(Port::direction),
                Codec.DOUBLE.optionalFieldOf("volumeM3", Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3).forGetter(Port::volumeM3),
                ProfileCodecHelpers.enumCodec(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.class).optionalFieldOf("fixedLayer").forGetter(Port::fixedLayer),
                ProfileCodecHelpers.enumCodec(PortName.class).listOf().optionalFieldOf("alwaysReachablePeers", List.of()).forGetter(Port::alwaysReachablePeers))
                .apply(i, Port::new));
        public Port(PortName name, PortDirection direction, double volumeM3) {
            this(name, direction, volumeM3, Optional.empty(), List.of());
        }
        public Port {
            fixedLayer = fixedLayer == null ? Optional.empty() : fixedLayer;
            alwaysReachablePeers = List.copyOf(alwaysReachablePeers);
            if (alwaysReachablePeers.contains(name)) throw new IllegalArgumentException("port cannot link to itself");
            if (new HashSet<>(alwaysReachablePeers).size() != alwaysReachablePeers.size())
                throw new IllegalArgumentException("duplicate peer port");
        }
    }
    public record NetworkNode(NetworkNodeName name, NetworkGroup group) {
        public static final Codec<NetworkNode> CODEC = RecordCodecBuilder.create(i -> i.group(ProfileCodecHelpers.enumCodec(NetworkNodeName.class).fieldOf("name").forGetter(NetworkNode::name), ProfileCodecHelpers.enumCodec(NetworkGroup.class).fieldOf("group").forGetter(NetworkNode::group)).apply(i, NetworkNode::new));
    }
    public record Power(double watts) {
        public static final Codec<Power> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.DOUBLE.fieldOf("watts").forGetter(Power::watts)).apply(i, Power::new));
    }
    public record Pump(PumpMode mode, boolean enabled, Optional<Double> targetPressureKpa, Optional<Double> maxPressureKpa,
            Optional<Double> transferRate, Optional<Double> maxTransferRate, Optional<Double> leakRatio,
            Optional<Double> lowerThresholdKpa, Optional<Double> higherThresholdKpa, Optional<Double> overclockThresholdKpa) {
        public static final Codec<Pump> CODEC = RecordCodecBuilder.create(i -> i.group(ProfileCodecHelpers.enumCodec(PumpMode.class).fieldOf("mode").forGetter(Pump::mode),
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Pump::enabled), Codec.DOUBLE.optionalFieldOf("targetPressureKpa").forGetter(Pump::targetPressureKpa),
                Codec.DOUBLE.optionalFieldOf("maxPressureKpa").forGetter(Pump::maxPressureKpa), Codec.DOUBLE.optionalFieldOf("transferRate").forGetter(Pump::transferRate),
                Codec.DOUBLE.optionalFieldOf("maxTransferRate").forGetter(Pump::maxTransferRate), Codec.DOUBLE.optionalFieldOf("leakRatio").forGetter(Pump::leakRatio),
                Codec.DOUBLE.optionalFieldOf("lowerThresholdKpa").forGetter(Pump::lowerThresholdKpa), Codec.DOUBLE.optionalFieldOf("higherThresholdKpa").forGetter(Pump::higherThresholdKpa),
                Codec.DOUBLE.optionalFieldOf("overclockThresholdKpa").forGetter(Pump::overclockThresholdKpa)).apply(i, Pump::new));
        public Pump { targetPressureKpa = safe(targetPressureKpa); maxPressureKpa = safe(maxPressureKpa); transferRate = safe(transferRate); maxTransferRate = safe(maxTransferRate); leakRatio = safe(leakRatio); lowerThresholdKpa = safe(lowerThresholdKpa); higherThresholdKpa = safe(higherThresholdKpa); overclockThresholdKpa = safe(overclockThresholdKpa); }
        private static Optional<Double> safe(Optional<Double> value) { return value == null ? Optional.empty() : value; }
    }
    public record Mixer(List<Double> ratios, double targetPressureKpa, double maxPressureKpa, boolean enabled) {
        public static final Codec<Mixer> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.DOUBLE.listOf().fieldOf("ratios").forGetter(Mixer::ratios),
                Codec.DOUBLE.optionalFieldOf("targetPressureKpa",101.325).forGetter(Mixer::targetPressureKpa), Codec.DOUBLE.optionalFieldOf("maxPressureKpa",Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA).forGetter(Mixer::maxPressureKpa),
                Codec.BOOL.optionalFieldOf("enabled",false).forGetter(Mixer::enabled)).apply(i, Mixer::new));
        public Mixer { ratios = List.copyOf(ratios); }
    }
    public record Filter(Optional<GasType> gas, double transferRate, double maxTransferRate, boolean enabled) {
        public static final Codec<Filter> CODEC = RecordCodecBuilder.create(i -> i.group(ProfileCodecHelpers.GAS_CODEC.optionalFieldOf("gas").forGetter(Filter::gas),
                Codec.DOUBLE.fieldOf("transferRate").forGetter(Filter::transferRate), Codec.DOUBLE.fieldOf("maxTransferRate").forGetter(Filter::maxTransferRate), Codec.BOOL.optionalFieldOf("enabled",false).forGetter(Filter::enabled)).apply(i, Filter::new));
        public Filter { gas = gas == null ? Optional.empty() : gas; }
    }
    public record Valve(boolean open) {
        public static final Codec<Valve> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.BOOL.optionalFieldOf("open", true).forGetter(Valve::open)).apply(i, Valve::new));
    }
    /** Optional composed accepted-action cue; unsupported/absent compositions stay silent. */
    public record ActivationAudio(String sound, float volume, float pitchVariation) {
        public static final Codec<ActivationAudio> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("sound").forGetter(ActivationAudio::sound),
                Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter(ActivationAudio::volume),
                Codec.FLOAT.optionalFieldOf("pitchVariation", .25f).forGetter(ActivationAudio::pitchVariation)
        ).apply(i, ActivationAudio::new));
    }
    /** inputPowerWatts is the thermo machine HeatCapacity rating and dynamic receiver load; no parallel Power.watts is composed. */
    public record Thermostat(ThermostatMode mode, double inputPowerWatts, double coefficientOfPerformance,
            double minimumTemperatureK, double maximumTemperatureK, double targetK, double toleranceK, boolean enabled) {
        public static final Codec<Thermostat> CODEC = RecordCodecBuilder.create(i -> i.group(ProfileCodecHelpers.enumCodec(ThermostatMode.class).fieldOf("mode").forGetter(Thermostat::mode),
                Codec.DOUBLE.fieldOf("inputPowerWatts").forGetter(Thermostat::inputPowerWatts), Codec.DOUBLE.fieldOf("coefficientOfPerformance").forGetter(Thermostat::coefficientOfPerformance),
                Codec.DOUBLE.optionalFieldOf("minimumTemperatureK",73.15).forGetter(Thermostat::minimumTemperatureK), Codec.DOUBLE.optionalFieldOf("maximumTemperatureK",593.15).forGetter(Thermostat::maximumTemperatureK),
                Codec.DOUBLE.optionalFieldOf("targetK",293.15).forGetter(Thermostat::targetK), Codec.DOUBLE.optionalFieldOf("toleranceK",2d).forGetter(Thermostat::toleranceK),
                Codec.BOOL.optionalFieldOf("enabled",false).forGetter(Thermostat::enabled)).apply(i, Thermostat::new));
    }
    public record Teg(double efficiency, double powerFactor, double rampFactor, double minimumPowerWatts, double smoothing,
                      double minimumVisualWatts, double maximumVisualWatts) {
        public static final Codec<Teg> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.DOUBLE.fieldOf("efficiency").forGetter(Teg::efficiency), Codec.DOUBLE.fieldOf("powerFactor").forGetter(Teg::powerFactor), Codec.DOUBLE.fieldOf("rampFactor").forGetter(Teg::rampFactor), Codec.DOUBLE.fieldOf("minimumPowerWatts").forGetter(Teg::minimumPowerWatts), Codec.DOUBLE.fieldOf("smoothing").forGetter(Teg::smoothing), Codec.DOUBLE.optionalFieldOf("minimumVisualWatts", 5000d).forGetter(Teg::minimumVisualWatts), Codec.DOUBLE.optionalFieldOf("maximumVisualWatts", 200000d).forGetter(Teg::maximumVisualWatts)).apply(i, Teg::new));
    }
    public record HeatExchanger(double conductivityMolesPerKpaPerS, double convectionCoefficientJPerKPerS, double radiationCoefficient) {
        public static final Codec<HeatExchanger> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.DOUBLE.fieldOf("conductivityMolesPerKpaPerS").forGetter(HeatExchanger::conductivityMolesPerKpaPerS), Codec.DOUBLE.fieldOf("convectionCoefficientJPerKPerS").forGetter(HeatExchanger::convectionCoefficientJPerKPerS), Codec.DOUBLE.fieldOf("radiationCoefficient").forGetter(HeatExchanger::radiationCoefficient)).apply(i, HeatExchanger::new));
    }
    /** Resource identifiers are references only; they do not claim bundled asset integration. */
    public record Visual(String sprite, String state, double circulatorFastDeltaKpa) {
        public static final Codec<Visual> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("sprite").forGetter(Visual::sprite), Codec.STRING.fieldOf("state").forGetter(Visual::state),
                Codec.DOUBLE.optionalFieldOf("circulatorFastDeltaKpa", 506.625).forGetter(Visual::circulatorFastDeltaKpa)).apply(i, Visual::new));
        public Visual(String sprite, String state) { this(sprite, state, 506.625); }
        public Visual { if (!Double.isFinite(circulatorFastDeltaKpa) || circulatorFastDeltaKpa < 0) throw new IllegalArgumentException("invalid circulator visual threshold"); }
    }

    public static final AtmosMachineCadence.Settings DEFAULT_SIMULATION = new AtmosMachineCadence.Settings(
            AtmosMachineCadence.DEFAULT_DEVICE_INTERVAL_SECONDS, 8, 30);
    public static final Codec<AtmosMachineCadence.Settings> SIMULATION_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("deviceIntervalSeconds", DEFAULT_SIMULATION.deviceIntervalSeconds()).forGetter(AtmosMachineCadence.Settings::deviceIntervalSeconds),
            Codec.DOUBLE.optionalFieldOf("pumpSpeedup", DEFAULT_SIMULATION.pumpSpeedup()).forGetter(AtmosMachineCadence.Settings::pumpSpeedup),
            Codec.DOUBLE.optionalFieldOf("tileRadiationLossWatts", DEFAULT_SIMULATION.tileRadiationLossWatts()).forGetter(AtmosMachineCadence.Settings::tileRadiationLossWatts)
    ).apply(i, AtmosMachineCadence.Settings::new));

    public static final Codec<AtmosMachineProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ProfileCodecHelpers.enumCodec(Kind.class).fieldOf("kind").forGetter(AtmosMachineProfile::kind), Port.CODEC.listOf().optionalFieldOf("ports", List.of()).forGetter(AtmosMachineProfile::ports), NetworkNode.CODEC.listOf().optionalFieldOf("networkNodes", List.of()).forGetter(AtmosMachineProfile::networkNodes),
            Power.CODEC.optionalFieldOf("power").forGetter(p -> Optional.ofNullable(p.power())), Pump.CODEC.optionalFieldOf("pump").forGetter(p -> Optional.ofNullable(p.pump())),
            Mixer.CODEC.optionalFieldOf("mixer").forGetter(p -> Optional.ofNullable(p.mixer())), Filter.CODEC.optionalFieldOf("filter").forGetter(p -> Optional.ofNullable(p.filter())),
            Valve.CODEC.optionalFieldOf("valve").forGetter(p -> Optional.ofNullable(p.valve())), Thermostat.CODEC.optionalFieldOf("thermostat").forGetter(p -> Optional.ofNullable(p.thermostat())),
            Teg.CODEC.optionalFieldOf("teg").forGetter(p -> Optional.ofNullable(p.teg())), HeatExchanger.CODEC.optionalFieldOf("heatExchanger").forGetter(p -> Optional.ofNullable(p.heatExchanger())),
             Visual.CODEC.optionalFieldOf("visual").forGetter(p -> Optional.ofNullable(p.visual())),
              SIMULATION_CODEC.optionalFieldOf("simulation", DEFAULT_SIMULATION).forGetter(AtmosMachineProfile::simulation),
               PipeLayers.CODEC.optionalFieldOf("pipeLayers").forGetter(AtmosMachineProfile::pipeLayers),
                ActivationAudio.CODEC.optionalFieldOf("activationAudio").forGetter(AtmosMachineProfile::activationAudio),
                AtmosAmbientAudio.CODEC.optionalFieldOf("ambientAudio").forGetter(AtmosMachineProfile::ambientAudio)
        ).apply(i, (kind, ports, nodes, power, pump, mixer, filter, valve, thermostat, teg, exchanger, visual, simulation, layers, audio, ambient) ->
                new AtmosMachineProfile(kind, ports, nodes, power.orElse(null), pump.orElse(null), mixer.orElse(null), filter.orElse(null), valve.orElse(null), thermostat.orElse(null), teg.orElse(null), exchanger.orElse(null), visual.orElse(null), simulation, layers, audio, ambient)));

    public AtmosMachineProfile(Kind kind, List<Port> ports, List<NetworkNode> networkNodes, Power power, Pump pump,
            Mixer mixer, Filter filter, Valve valve, Thermostat thermostat, Teg teg, HeatExchanger heatExchanger, Visual visual) {
        this(kind, ports, networkNodes, power, pump, mixer, filter, valve, thermostat, teg, heatExchanger, visual, DEFAULT_SIMULATION, Optional.empty(), Optional.empty(), Optional.empty());
    }
    public AtmosMachineProfile(Kind kind, List<Port> ports, List<NetworkNode> networkNodes, Power power, Pump pump,
            Mixer mixer, Filter filter, Valve valve, Thermostat thermostat, Teg teg, HeatExchanger heatExchanger,
            Visual visual, AtmosMachineCadence.Settings simulation, Optional<PipeLayers> pipeLayers) {
        this(kind, ports, networkNodes, power, pump, mixer, filter, valve, thermostat, teg, heatExchanger, visual, simulation, pipeLayers, Optional.empty(), Optional.empty());
    }
    public AtmosMachineProfile(Kind kind, List<Port> ports, List<NetworkNode> networkNodes, Power power, Pump pump,
            Mixer mixer, Filter filter, Valve valve, Thermostat thermostat, Teg teg, HeatExchanger heatExchanger,
            Visual visual, AtmosMachineCadence.Settings simulation, Optional<PipeLayers> pipeLayers,
            Optional<ActivationAudio> activationAudio) {
        this(kind, ports, networkNodes, power, pump, mixer, filter, valve, thermostat, teg, heatExchanger, visual, simulation, pipeLayers, activationAudio, Optional.empty());
    }
    public AtmosMachineProfile { ports = List.copyOf(ports); networkNodes = List.copyOf(networkNodes); java.util.Set<PortName> names = new HashSet<>(); for (Port port : ports) if (!names.add(port.name())) throw new IllegalArgumentException("duplicate port"); for (Port port : ports) for (PortName peer : port.alwaysReachablePeers()) if (!names.contains(peer)) throw new IllegalArgumentException("unknown peer port: " + peer); simulation = simulation == null ? DEFAULT_SIMULATION : simulation; pipeLayers = pipeLayers == null ? Optional.empty() : pipeLayers; activationAudio = activationAudio == null ? Optional.empty() : activationAudio; ambientAudio = ambientAudio == null ? Optional.empty() : ambientAudio; }
    /** Configured receiver rating; the thermo system may gate its actual load with thermal hysteresis. */
    public double configuredReceiverLoadWatts() {
        if (thermostat != null) return thermostat.inputPowerWatts();
        return power == null ? 0 : power.watts();
    }
}
