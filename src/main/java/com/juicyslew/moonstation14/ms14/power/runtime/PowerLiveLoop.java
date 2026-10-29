package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.solver.PowerAllocation;

import java.util.*;

/** Pure component-wide accounting for the bounded live HV ╬ô├Ñ├å MV ╬ô├Ñ├å APC power loop. */
public final class PowerLiveLoop {
    /** Rated transfer ceiling; actual transfer is still bounded by source watts and demand. */
    private static final double SUBSTATION_LIMIT = 30_000;
    /** MV port allocation ceiling per APC (not a free source of power). */
    private static final double APC_INPUT_LIMIT = 30_000;
    /** Per-battery storage supply ceiling, matching the upstream BaseAPC maxSupply. */
    private static final double APC_MAX_SUPPLY = 10_000;
    /** Rated source ceiling; an offer can never exceed the source's actual watts. */
    private static final double SOURCE_LIMIT = 30_000;
    private static final double BATTERY_CAPACITY = 1_000_000;
    private static final double SUBSTATION_EFFICIENCY = .9;
    private static final double BATTERY_CHARGE_RATE = 5_000;
    /** Pinned SS14 unary vent/scrubber ApcPowerReceiver default desired power (5 W). */
    public static final double SS14_UNARY_ATMOS_POWER_WATTS = 5.0;
    /** Solver validation bound only; this is not device demand or a provisional SS14 value. */
    public static final double VENT_MAX_DEMAND_WATTS = 5_000;

    private PowerLiveLoop() { }

    /** One HV supplier snapshot: upstream maxSupply is its rating; watts is the current offer. */
    public record Source(String id, int component, boolean known, double watts, double ratedMaximumWatts) {
        public Source {
            if (id == null || id.isBlank() || !Double.isFinite(watts) || watts < 0
                    || !Double.isFinite(ratedMaximumWatts) || ratedMaximumWatts < 0)
                throw new IllegalArgumentException("Invalid HV source offer");
        }
        /** Legacy adapter retains the former 30 kW ceiling and clips oversized offers. */
        public Source(String id, int component, boolean known, double watts) {
            this(id, component, known, legacyOffer(watts), SOURCE_LIMIT);
        }
    }

    private static double legacyOffer(double watts) {
        if (!Double.isFinite(watts) || watts < 0) throw new IllegalArgumentException("Invalid legacy HV offer");
        return Math.min(SOURCE_LIMIT, watts);
    }
    /** Shared declaration for any genuine MV-tier machine consumer, independent of machine kind. */
    public record MvConsumer(String id, int component, boolean known, double demandWatts) {
        public MvConsumer {
            if (id == null || id.isBlank() || !Double.isFinite(demandWatts) || demandWatts < 0)
                throw new IllegalArgumentException("Invalid MV consumer demand");
        }
    }
    public record Substation(String id, int hvComponent, boolean hvKnown, int mvComponent,
                             boolean mvKnown) { }
    public record Apc(String id, int mvComponent, boolean mvKnown, int apcComponent,
                      boolean apcKnown, boolean breakerClosed, double energyJoules) { }
    public record Lamp(String id, int component, boolean known, PowerDeviceKind kind) {
        public Lamp {
            if (!kind.isLamp()) throw new IllegalArgumentException("Lamp kind required");
        }
        public Lamp(String id, int component, boolean known) {
            this(id, component, known, PowerDeviceKind.LAMP);
        }
    }
    public record VentLoad(String id, int apcComponent, boolean known, double demandWatts) {
        public VentLoad {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Vent id required");
            if (!Double.isFinite(demandWatts) || demandWatts <= 0 || demandWatts > VENT_MAX_DEMAND_WATTS)
                throw new IllegalArgumentException("Vent demand must be positive and at most " + VENT_MAX_DEMAND_WATTS);
        }

        /** Default unary.yml vent/scrubber load; both use the same APC solver path. */
        public static VentLoad defaultUnaryAtmos(String id, int apcComponent, boolean known) {
            return new VentLoad(id, apcComponent, known, SS14_UNARY_ATMOS_POWER_WATTS);
        }
    }
    /** Resolved air-alarm APC receiver; not a ventilation/gas-work grant. */
    public record AlarmLoad(String id, int apcComponent, boolean known, double demandWatts) {
        public AlarmLoad {
            if (id == null || id.isBlank() || !Double.isFinite(demandWatts)
                    || demandWatts <= 0 || demandWatts > VENT_MAX_DEMAND_WATTS)
                throw new IllegalArgumentException("Invalid air alarm demand");
        }
    }
    /** Ordinary continuously metered door receiver; this is not a ventilation or alarm load. */
    public record AirlockLoad(String id, int apcComponent, boolean known, double demandWatts) {
        public AirlockLoad { if (id == null || id.isBlank() || !Double.isFinite(demandWatts) || demandWatts <= 0 || demandWatts > VENT_MAX_DEMAND_WATTS) throw new IllegalArgumentException("Invalid airlock demand"); }
    }
    public record DisposalLoad(String id, int apcComponent, boolean known, double demandWatts) {
        public DisposalLoad { if (id == null || id.isBlank() || !Double.isFinite(demandWatts) || demandWatts <= 0 || demandWatts > VENT_MAX_DEMAND_WATTS) throw new IllegalArgumentException("Invalid disposal demand"); }
    }
    /** Resolved powered ChemMaster APC receiver; meters actual component allocation. */
    public record ChemMasterLoad(String id, int apcComponent, boolean known, double demandWatts) {
        public ChemMasterLoad { if (id == null || id.isBlank() || !Double.isFinite(demandWatts) || demandWatts <= 0 || demandWatts > VENT_MAX_DEMAND_WATTS) throw new IllegalArgumentException("Invalid ChemMaster demand"); }
    }
    /** Generic APC receiver; deliberately separate from specialized device accounting. */
    public record ApcConsumer(String id, int apcComponent, boolean known, double demandWatts) {
        public ApcConsumer { if (id == null || id.isBlank() || !Double.isFinite(demandWatts) || demandWatts < 0) throw new IllegalArgumentException("Invalid APC consumer demand"); }
    }
    public record ProcessingLoad(String id, int apcComponent, boolean known, double demandWatts) {
        public ProcessingLoad { if (id == null || id.isBlank() || !Double.isFinite(demandWatts) || demandWatts <= 0) throw new IllegalArgumentException("Invalid processing demand"); }
    }
    public record Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                        List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms,
                        List<AirlockLoad> airlocks, List<DisposalLoad> disposal, List<ChemMasterLoad> chemMasters,
                        List<MvConsumer> mvConsumers, List<ApcConsumer> apcConsumers,
                        List<ProcessingLoad> processing, long elapsedTicks) {
        public Input {
            sources = List.copyOf(sources); substations = List.copyOf(substations); apcs = List.copyOf(apcs);
            lamps = List.copyOf(lamps); vents = List.copyOf(vents); alarms = List.copyOf(alarms);
            airlocks = List.copyOf(airlocks); disposal = List.copyOf(disposal); chemMasters = List.copyOf(chemMasters);
            mvConsumers = List.copyOf(mvConsumers); apcConsumers = List.copyOf(apcConsumers); processing = List.copyOf(processing);
            Set<String> ids = new HashSet<>();
            for (Source value : sources) unique(ids, value.id());
            for (Substation value : substations) unique(ids, value.id());
            for (Apc value : apcs) unique(ids, value.id());
            for (Lamp value : lamps) unique(ids, value.id());
            for (VentLoad value : vents) unique(ids, value.id());
            for (AlarmLoad value : alarms) unique(ids, value.id());
            for (AirlockLoad value : airlocks) unique(ids, value.id());
            for (DisposalLoad value : disposal) unique(ids, value.id());
            for (ChemMasterLoad value : chemMasters) unique(ids, value.id());
            for (MvConsumer value : mvConsumers) unique(ids, value.id());
            for (ApcConsumer value : apcConsumers) unique(ids, value.id());
            for (ProcessingLoad value : processing) unique(ids, value.id());
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms,
                     List<AirlockLoad> airlocks, List<DisposalLoad> disposal, List<ChemMasterLoad> chemMasters,
                     List<MvConsumer> mvConsumers, List<ApcConsumer> apcConsumers, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, alarms, airlocks, disposal, chemMasters, mvConsumers, apcConsumers, List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms,
                     List<AirlockLoad> airlocks, List<DisposalLoad> disposal, List<ChemMasterLoad> chemMasters,
                     List<MvConsumer> mvConsumers, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, alarms, airlocks, disposal, chemMasters, mvConsumers, List.of(), List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms,
                     List<AirlockLoad> airlocks, List<DisposalLoad> disposal, List<ChemMasterLoad> chemMasters, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, alarms, airlocks, disposal, chemMasters, List.of(), List.of(), List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, alarms, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs, List<Lamp> lamps, long elapsedTicks) {
            this(sources, substations, apcs, lamps, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), elapsedTicks);
        }
    }
    public record Result(Map<String, Double> substationInputWatts, Map<String, Double> apcInputWatts,
                         Map<String, Double> lampWatts, Map<String, Double> apcOutputWatts,
                         Map<String, Double> batteryEnergyJoules, Map<String, Double> ventWatts,
                         Map<String, Double> alarmWatts, Map<String, Double> airlockWatts,
                         Map<String, Double> disposalWatts, Map<String, Double> chemMasterWatts,
                         Map<String, Double> mvConsumerWatts, Map<String, Double> apcConsumerWatts,
                          Map<String, Double> processingWatts, Map<String, Double> hvSourceWatts) {
        public Result {
            substationInputWatts = Map.copyOf(substationInputWatts); apcInputWatts = Map.copyOf(apcInputWatts);
            lampWatts = Map.copyOf(lampWatts); apcOutputWatts = Map.copyOf(apcOutputWatts);
            batteryEnergyJoules = Map.copyOf(batteryEnergyJoules); ventWatts = Map.copyOf(ventWatts);
            alarmWatts = Map.copyOf(alarmWatts); airlockWatts = Map.copyOf(airlockWatts);
            disposalWatts = Map.copyOf(disposalWatts); chemMasterWatts = Map.copyOf(chemMasterWatts);
            mvConsumerWatts = Map.copyOf(mvConsumerWatts); apcConsumerWatts = Map.copyOf(apcConsumerWatts);
            processingWatts = Map.copyOf(processingWatts);
            hvSourceWatts = Map.copyOf(hvSourceWatts);
        }
    }

    private static void unique(Set<String> ids, String id) {
        if (id == null || id.isBlank() || !ids.add(id))
            throw new IllegalArgumentException("Missing or duplicate power allocation id: " + id);
    }

    public static Result solve(Input input) {
        Map<String, Double> hvInputs = new HashMap<>();
        Map<String, Double> hvSourceWatts = new HashMap<>();
        for (Source source : input.sources()) hvSourceWatts.put(source.id(), 0.0);
        Map<Integer, List<Source>> sourcesByComponent = group(input.sources(), Source::known, Source::component);
        Map<Integer, List<Substation>> subsByHv = group(input.substations(), Substation::hvKnown, Substation::hvComponent);
        for (var entry : subsByHv.entrySet()) {
            List<Substation> subs = entry.getValue();
            List<PowerAllocation.Source> offers = sourcesByComponent.getOrDefault(entry.getKey(), List.of()).stream()
                    .map(s -> new PowerAllocation.Source(s.id(), s.ratedMaximumWatts(), s.watts())).toList();
            var loads = subs.stream().map(s -> new PowerAllocation.Load(s.id(), SUBSTATION_LIMIT)).toList();
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.HV, true,
                    offers, loads, null), input.elapsedTicks());
            allocation.deliveredWatts().forEach((id, watts) -> hvInputs.put(id, watts));
            double sourceTotal = sourcesByComponent.getOrDefault(entry.getKey(), List.of()).stream()
                    .mapToDouble(source -> Math.min(source.watts(), source.ratedMaximumWatts())).sum();
            if (sourceTotal > 0 && allocation.sourceUsedWatts() > 0) {
                for (Source source : sourcesByComponent.getOrDefault(entry.getKey(), List.of())) {
                    double offer = Math.min(source.watts(), source.ratedMaximumWatts());
                    hvSourceWatts.put(source.id(), allocation.sourceUsedWatts() * offer / sourceTotal);
                }
            }
        }

        Map<String, Double> apcInputs = new HashMap<>();
        Map<Integer, List<Substation>> subsByMv = group(input.substations(), Substation::mvKnown, Substation::mvComponent);
        Map<Integer, List<Apc>> apcsByMv = group(input.apcs(), Apc::mvKnown, Apc::mvComponent);
        Map<String, Double> apcRequests = apcInputRequests(input.apcs(), input, input.elapsedTicks());
        Map<Integer, List<MvConsumer>> mvConsumersByComponent = group(input.mvConsumers(), MvConsumer::known, MvConsumer::component);
        Map<String, Double> mvConsumerWatts = new HashMap<>();
        for (MvConsumer consumer : input.mvConsumers()) mvConsumerWatts.put(consumer.id(), 0.0);
        Set<Integer> mvComponents = new HashSet<>(apcsByMv.keySet());
        mvComponents.addAll(mvConsumersByComponent.keySet());
        for (int mvComponent : mvComponents) {
            List<Substation> subs = subsByMv.getOrDefault(mvComponent, List.of());
            List<PowerAllocation.Source> offers = subs.stream().map(sub -> new PowerAllocation.Source(sub.id(),
                    SUBSTATION_LIMIT * SUBSTATION_EFFICIENCY,
                    Math.min(SUBSTATION_LIMIT, hvInputs.getOrDefault(sub.id(), 0.0)) * SUBSTATION_EFFICIENCY)).toList();
            List<Apc> loads = apcsByMv.getOrDefault(mvComponent, List.of());
            List<MvConsumer> consumers = mvConsumersByComponent.getOrDefault(mvComponent, List.of());
            List<PowerAllocation.Load> mvDemands = new ArrayList<>();
            // Request only real downstream demand plus charge headroom. Rated APC input is
            // a ceiling, not a standing load that can crowd ordinary MV receivers out.
            loads.forEach(apc -> mvDemands.add(new PowerAllocation.Load(apc.id(), apcRequests.getOrDefault(apc.id(), 0.0))));
            consumers.forEach(consumer -> mvDemands.add(new PowerAllocation.Load(consumer.id(), consumer.demandWatts())));
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.MV, true,
                    offers, mvDemands, null),
                    input.elapsedTicks());
            loads.forEach(apc -> apcInputs.put(apc.id(), allocation.deliveredWatts().getOrDefault(apc.id(), 0.0)));
            consumers.forEach(consumer -> mvConsumerWatts.put(consumer.id(), allocation.deliveredWatts().getOrDefault(consumer.id(), 0.0)));
        }

        Map<Integer, List<Apc>> apcsByApc = group(input.apcs(), Apc::apcKnown, Apc::apcComponent);
        Map<Integer, List<Lamp>> lampsByApc = group(input.lamps(), Lamp::known, Lamp::component);
        Map<Integer, List<VentLoad>> ventsByApc = group(input.vents(), VentLoad::known, VentLoad::apcComponent);
        Map<Integer, List<AlarmLoad>> alarmsByApc = group(input.alarms(), AlarmLoad::known, AlarmLoad::apcComponent);
        Map<Integer, List<AirlockLoad>> airlocksByApc = group(input.airlocks(), AirlockLoad::known, AirlockLoad::apcComponent);
        Map<Integer, List<DisposalLoad>> disposalByApc = group(input.disposal(), DisposalLoad::known, DisposalLoad::apcComponent);
        Map<Integer, List<ChemMasterLoad>> chemMastersByApc = group(input.chemMasters(), ChemMasterLoad::known, ChemMasterLoad::apcComponent);
        Map<Integer, List<ApcConsumer>> genericByApc = group(input.apcConsumers(), ApcConsumer::known, ApcConsumer::apcComponent);
        Map<Integer, List<ProcessingLoad>> processingByApc = group(input.processing(), ProcessingLoad::known, ProcessingLoad::apcComponent);
        Map<String, Double> lampWatts = new HashMap<>();
        Map<String, Double> ventWatts = new HashMap<>();
        Map<String, Double> alarmWatts = new HashMap<>();
        Map<String, Double> airlockWatts = new HashMap<>();
        Map<String, Double> disposalWatts = new HashMap<>();
        Map<String, Double> chemMasterWatts = new HashMap<>();
        Map<String, Double> genericWatts = new HashMap<>();
        Map<String, Double> processingWatts = new HashMap<>();
        Map<String, Double> apcOutputWatts = new HashMap<>();
        Map<String, Double> energies = new HashMap<>();
        for (Lamp lamp : input.lamps()) lampWatts.put(lamp.id(), 0.0);
        for (VentLoad vent : input.vents()) ventWatts.put(vent.id(), 0.0);
        for (AlarmLoad alarm : input.alarms()) alarmWatts.put(alarm.id(), 0.0);
        for (AirlockLoad load : input.airlocks()) airlockWatts.put(load.id(), 0.0);
        for (DisposalLoad unit : input.disposal()) disposalWatts.put(unit.id(), 0.0);
        for (ChemMasterLoad load : input.chemMasters()) chemMasterWatts.put(load.id(), 0.0);
        for (ApcConsumer load : input.apcConsumers()) genericWatts.put(load.id(), 0.0);
        for (ProcessingLoad load : input.processing()) processingWatts.put(load.id(), 0.0);
        for (Apc apc : input.apcs()) {
            energies.put(apc.id(), validEnergy(apc.energyJoules()));
            apcOutputWatts.put(apc.id(), 0.0);
        }
        for (var entry : apcsByApc.entrySet()) {
            List<Apc> attachedApcs = entry.getValue();
            List<Apc> active = attachedApcs.stream().filter(Apc::breakerClosed).toList();
            // The breaker gates APC-tier output and discharge, but open batteries may
            // still charge from their own known MV input. Unknown/unloaded ports fail closed.
            List<Apc> openChargeable = attachedApcs.stream().filter(apc -> !apc.breakerClosed()
                    && apc.mvKnown()
                    && apcInputs.getOrDefault(apc.id(), 0.0) > 0).toList();
            List<Lamp> loads = lampsByApc.getOrDefault(entry.getKey(), List.of());
            List<VentLoad> ventLoads = ventsByApc.getOrDefault(entry.getKey(), List.of());
            List<AlarmLoad> alarmLoads = alarmsByApc.getOrDefault(entry.getKey(), List.of());
            List<AirlockLoad> airlockLoads = airlocksByApc.getOrDefault(entry.getKey(), List.of());
            List<DisposalLoad> disposalLoads = disposalByApc.getOrDefault(entry.getKey(), List.of());
            List<ChemMasterLoad> chemMasterLoads = chemMastersByApc.getOrDefault(entry.getKey(), List.of());
            List<ApcConsumer> genericLoads = genericByApc.getOrDefault(entry.getKey(), List.of());
            List<ProcessingLoad> processingLoads = processingByApc.getOrDefault(entry.getKey(), List.of());
            double activeStored = active.stream().mapToDouble(apc -> validEnergy(apc.energyJoules())).sum();
            double seconds = Math.max(0, Math.min((double) input.elapsedTicks(),
                    PowerAllocation.MAX_VALUE)) / PowerAllocation.TICKS_PER_SECOND;
            double batteryDischargeLimit = active.stream().mapToDouble(apc -> seconds <= 0 ? 0
                    : Math.min(APC_MAX_SUPPLY, validEnergy(apc.energyJoules()) / seconds)).sum();
            // The shared network decides what the lamps receive. Charging is deliberately
            // omitted here: its source must remain attributable to the APC that received it.
            PowerAllocation.Storage battery = active.isEmpty() ? null : new PowerAllocation.Storage(
                    activeStored, activeStored, 0, batteryDischargeLimit, 1, 1);
            List<PowerAllocation.Source> offers = active.stream().map(apc -> new PowerAllocation.Source(apc.id(),
                    APC_INPUT_LIMIT, Math.min(APC_INPUT_LIMIT, apcInputs.getOrDefault(apc.id(), 0.0)))).toList();
            List<PowerAllocation.Load> demands = new ArrayList<>();
            loads.forEach(lamp -> demands.add(new PowerAllocation.Load(lamp.id(), lamp.kind().lampDemandWatts())));
            if (!active.isEmpty()) ventLoads.forEach(vent -> demands.add(new PowerAllocation.Load(vent.id(), vent.demandWatts())));
            if (!active.isEmpty()) alarmLoads.forEach(alarm -> demands.add(new PowerAllocation.Load(alarm.id(), alarm.demandWatts())));
            if (!active.isEmpty()) airlockLoads.forEach(load -> demands.add(new PowerAllocation.Load(load.id(), load.demandWatts())));
            if (!active.isEmpty()) disposalLoads.forEach(unit -> demands.add(new PowerAllocation.Load(unit.id(), unit.demandWatts())));
            if (!active.isEmpty()) chemMasterLoads.forEach(load -> demands.add(new PowerAllocation.Load(load.id(), load.demandWatts())));
            if (!active.isEmpty()) genericLoads.forEach(load -> demands.add(new PowerAllocation.Load(load.id(), load.demandWatts())));
            if (!active.isEmpty()) processingLoads.forEach(load -> demands.add(new PowerAllocation.Load(load.id(), load.demandWatts())));
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.APC, true,
                     offers, demands,
                     battery), input.elapsedTicks());
            for (Lamp lamp : loads) lampWatts.put(lamp.id(), allocation.deliveredWatts().getOrDefault(lamp.id(), 0.0));
            for (VentLoad vent : ventLoads) ventWatts.put(vent.id(), allocation.deliveredWatts().getOrDefault(vent.id(), 0.0));
            for (AlarmLoad alarm : alarmLoads) alarmWatts.put(alarm.id(), allocation.deliveredWatts().getOrDefault(alarm.id(), 0.0));
            for (AirlockLoad load : airlockLoads) airlockWatts.put(load.id(), allocation.deliveredWatts().getOrDefault(load.id(), 0.0));
            for (DisposalLoad unit : disposalLoads) disposalWatts.put(unit.id(), allocation.deliveredWatts().getOrDefault(unit.id(), 0.0));
            for (ChemMasterLoad load : chemMasterLoads) chemMasterWatts.put(load.id(), allocation.deliveredWatts().getOrDefault(load.id(), 0.0));
            for (ApcConsumer load : genericLoads) genericWatts.put(load.id(), allocation.deliveredWatts().getOrDefault(load.id(), 0.0));
            for (ProcessingLoad load : processingLoads) processingWatts.put(load.id(), allocation.deliveredWatts().getOrDefault(load.id(), 0.0));
            Map<String, Double> sourceUsed = allocateSourceUse(active, apcInputs,
                    allocation.sourceUsedWatts());
            Map<String, Double> batteryUsed = allocateBatteryUse(active,
                    allocation.batteryOutputWatts(), input.elapsedTicks());
            for (Apc apc : active) {
                double inputUsed = sourceUsed.getOrDefault(apc.id(), 0.0);
                double batteryOutput = batteryUsed.getOrDefault(apc.id(), 0.0);
                apcOutputWatts.merge(apc.id(), inputUsed + batteryOutput, Double::sum);
                double energy = validEnergy(apc.energyJoules());
                energy = Math.max(0, energy - batteryOutput * seconds);
                double ownSurplus = Math.max(0, apcInputs.getOrDefault(apc.id(), 0.0) - inputUsed);
                energy = chargeBattery(energy, ownSurplus, seconds);
                energies.put(apc.id(), energy);
            }

            // Open-breaker APC input is isolated from lamp delivery and discharge.
            // It can only charge its own known battery from external MV power.
            for (Apc apc : openChargeable) {
                double energy = validEnergy(apc.energyJoules());
                double ownInput = Math.min(APC_INPUT_LIMIT, apcInputs.getOrDefault(apc.id(), 0.0));
                energies.put(apc.id(), chargeBattery(energy, ownInput, seconds));
            }
        }
        for (Apc apc : input.apcs()) if (!apc.apcKnown() && apc.mvKnown()) {
            double energy = validEnergy(apc.energyJoules());
            energies.put(apc.id(), chargeBattery(energy, apcInputs.getOrDefault(apc.id(), 0.0), secondsFor(input.elapsedTicks())));
        }
        return new Result(hvInputs, apcInputs, lampWatts, apcOutputWatts, energies, ventWatts, alarmWatts,
                airlockWatts, disposalWatts, chemMasterWatts, mvConsumerWatts, genericWatts, processingWatts, hvSourceWatts);
    }

    private static double secondsFor(long elapsedTicks) {
        return Math.max(0, Math.min((double) elapsedTicks, PowerAllocation.MAX_VALUE))
                / PowerAllocation.TICKS_PER_SECOND;
    }

    private static double chargeBattery(double energy, double inputWatts, double seconds) {
        double charge = seconds <= 0 ? 0 : Math.min(Math.min(APC_INPUT_LIMIT, inputWatts),
                Math.min(BATTERY_CHARGE_RATE, (BATTERY_CAPACITY - energy) / seconds));
        return Math.min(BATTERY_CAPACITY, energy + charge * seconds);
    }

    private static Map<String, Double> apcInputRequests(List<Apc> apcs, Input input, long elapsedTicks) {
        Map<Integer, Double> demandByApc = new HashMap<>();
        for (Lamp load : input.lamps()) if (load.known()) demandByApc.merge(load.component(), load.kind().lampDemandWatts(), Double::sum);
        for (VentLoad load : input.vents()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        for (AlarmLoad load : input.alarms()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        for (AirlockLoad load : input.airlocks()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        for (DisposalLoad load : input.disposal()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        for (ChemMasterLoad load : input.chemMasters()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        for (ApcConsumer load : input.apcConsumers()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        for (ProcessingLoad load : input.processing()) if (load.known()) demandByApc.merge(load.apcComponent(), load.demandWatts(), Double::sum);
        Map<Integer, List<Apc>> grouped = group(apcs, Apc::apcKnown, Apc::apcComponent);
        Map<Integer, List<Apc>> providers = new HashMap<>();
        for (Apc apc : apcs) if (apc.apcKnown() && apc.breakerClosed() && apc.mvKnown())
            providers.computeIfAbsent(apc.apcComponent(), ignored -> new ArrayList<>()).add(apc);
        double seconds = secondsFor(elapsedTicks);
        Map<Integer, Double> sharedDemand = new HashMap<>();
        for (var entry : grouped.entrySet()) {
            List<Apc> eligible = providers.getOrDefault(entry.getKey(), List.of());
            sharedDemand.put(entry.getKey(), eligible.isEmpty() ? 0
                    : demandByApc.getOrDefault(entry.getKey(), 0.0) / eligible.size());
        }
        Map<String, Double> requests = new HashMap<>();
        for (Apc apc : apcs) {
            double energy = validEnergy(apc.energyJoules());
            double chargeRoom = seconds <= 0 ? 0 : Math.min(BATTERY_CHARGE_RATE, (BATTERY_CAPACITY - energy) / seconds);
            boolean eligibleProvider = apc.apcKnown() && apc.breakerClosed() && apc.mvKnown();
            double request = (eligibleProvider ? sharedDemand.getOrDefault(apc.apcComponent(), 0.0) : 0)
                    + (apc.mvKnown() ? chargeRoom : 0);
            requests.put(apc.id(), Math.min(APC_INPUT_LIMIT, request));
        }
        return requests;
    }

    /**
     * Assigns source passthrough in stable APC-id order, then apportions shared battery
     * support by stored energy. Thus each APC meter is a contribution to actual delivery,
     * never the whole shared component total duplicated on every APC.
     */
    private static Map<String, Double> allocateSourceUse(List<Apc> apcs, Map<String, Double> inputs,
                                                          double sourceUsed) {
        List<Apc> ordered = apcs.stream().sorted(Comparator.comparing(Apc::id)).toList();
        double remainingSource = sourceUsed;
        Map<String, Double> used = new HashMap<>();
        for (Apc apc : ordered) {
            double contribution = Math.min(remainingSource, inputs.getOrDefault(apc.id(), 0.0));
            used.put(apc.id(), contribution);
            remainingSource -= contribution;
        }
        return used;
    }

    private static Map<String, Double> allocateBatteryUse(List<Apc> apcs, double batteryOutput,
                                                           long elapsedTicks) {
        List<Apc> ordered = apcs.stream().sorted(Comparator.comparing(Apc::id)).toList();
        double seconds = Math.max(0, Math.min((double) elapsedTicks, PowerAllocation.MAX_VALUE))
                / PowerAllocation.TICKS_PER_SECOND;
        double remainingBattery = batteryOutput;
        Map<String, Double> batteryContributions = new HashMap<>();
        double stored = ordered.stream().mapToDouble(apc -> validEnergy(apc.energyJoules())).sum();
        if (stored > 0) {
            for (Apc apc : ordered) {
                double proportional = batteryOutput * validEnergy(apc.energyJoules()) / stored;
                double energyLimit = seconds <= 0 ? 0 : validEnergy(apc.energyJoules()) / seconds;
                double contribution = Math.min(remainingBattery,
                        Math.min(Math.min(APC_MAX_SUPPLY, energyLimit), proportional));
                batteryContributions.put(apc.id(), contribution);
                remainingBattery -= contribution;
            }
            // Redistribute only when a battery reached its per-device discharge bound.
            for (Apc apc : ordered) {
                double prior = batteryContributions.getOrDefault(apc.id(), 0.0);
                double energyLimit = seconds <= 0 ? 0 : validEnergy(apc.energyJoules()) / seconds;
                double headroom = Math.max(0, Math.min(APC_MAX_SUPPLY, energyLimit) - prior);
                double extra = Math.min(remainingBattery, headroom);
                batteryContributions.put(apc.id(), prior + extra);
                remainingBattery -= extra;
            }
        }
        return batteryContributions;
    }

    private static <T> Map<Integer, List<T>> group(List<T> values, java.util.function.Predicate<T> known,
                                                    java.util.function.ToIntFunction<T> component) {
        Map<Integer, List<T>> groups = new HashMap<>();
        for (T value : values) if (known.test(value)) groups.computeIfAbsent(component.applyAsInt(value), ignored -> new ArrayList<>()).add(value);
        return groups;
    }

    private static double validEnergy(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(value, BATTERY_CAPACITY)) : 0;
    }
}

