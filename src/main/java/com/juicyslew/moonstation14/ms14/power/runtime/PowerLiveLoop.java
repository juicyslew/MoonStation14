package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.solver.PowerAllocation;

import java.util.*;

/** Pure component-wide accounting for the bounded live HV → MV → APC power loop. */
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

    public record Source(String id, int component, boolean known, double watts) { }
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
    public record Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                         List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms,
                          List<AirlockLoad> airlocks, List<DisposalLoad> disposal, List<ChemMasterLoad> chemMasters, long elapsedTicks) {
        public Input {
            sources = List.copyOf(sources); substations = List.copyOf(substations);
            apcs = List.copyOf(apcs); lamps = List.copyOf(lamps); vents = List.copyOf(vents); alarms = List.copyOf(alarms);
            airlocks = List.copyOf(airlocks); disposal = List.copyOf(disposal);
            chemMasters = List.copyOf(chemMasters);
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
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs, List<Lamp> lamps,
                     List<VentLoad> vents, List<AlarmLoad> alarms, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, alarms, List.of(), List.of(), List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, List<AlarmLoad> alarms,
                     List<AirlockLoad> airlocks, List<DisposalLoad> disposal, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, alarms, airlocks, disposal, List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, List<VentLoad> vents, long elapsedTicks) {
            this(sources, substations, apcs, lamps, vents, List.of(), List.of(), List.of(), List.of(), elapsedTicks);
        }
        public Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                     List<Lamp> lamps, long elapsedTicks) {
            this(sources, substations, apcs, lamps, List.of(), List.of(), List.of(), List.of(), List.of(), elapsedTicks);
        }
    }
    public record Result(Map<String, Double> substationInputWatts, Map<String, Double> apcInputWatts,
                          Map<String, Double> lampWatts, Map<String, Double> apcOutputWatts,
                           Map<String, Double> batteryEnergyJoules, Map<String, Double> ventWatts,
                            Map<String, Double> alarmWatts, Map<String, Double> airlockWatts,
                             Map<String, Double> disposalWatts, Map<String, Double> chemMasterWatts) {
        public Result {
            substationInputWatts = Map.copyOf(substationInputWatts); apcInputWatts = Map.copyOf(apcInputWatts);
            lampWatts = Map.copyOf(lampWatts); apcOutputWatts = Map.copyOf(apcOutputWatts);
            batteryEnergyJoules = Map.copyOf(batteryEnergyJoules); ventWatts = Map.copyOf(ventWatts);
            alarmWatts = Map.copyOf(alarmWatts); airlockWatts = Map.copyOf(airlockWatts);
            disposalWatts = Map.copyOf(disposalWatts);
            chemMasterWatts = Map.copyOf(chemMasterWatts);
        }
    }

    private static void unique(Set<String> ids, String id) {
        if (id == null || id.isBlank() || !ids.add(id))
            throw new IllegalArgumentException("Missing or duplicate power allocation id: " + id);
    }

    public static Result solve(Input input) {
        Map<String, Double> hvInputs = new HashMap<>();
        Map<Integer, List<Source>> sourcesByComponent = group(input.sources(), Source::known, Source::component);
        Map<Integer, List<Substation>> subsByHv = group(input.substations(), Substation::hvKnown, Substation::hvComponent);
        for (var entry : subsByHv.entrySet()) {
            List<Substation> subs = entry.getValue();
            List<PowerAllocation.Source> offers = sourcesByComponent.getOrDefault(entry.getKey(), List.of()).stream()
                    .map(s -> new PowerAllocation.Source(s.id(), SOURCE_LIMIT, s.watts())).toList();
            var loads = subs.stream().map(s -> new PowerAllocation.Load(s.id(), SUBSTATION_LIMIT)).toList();
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.HV, true,
                    offers, loads, null), input.elapsedTicks());
            allocation.deliveredWatts().forEach((id, watts) -> hvInputs.put(id, watts));
        }

        Map<String, Double> apcInputs = new HashMap<>();
        Map<Integer, List<Substation>> subsByMv = group(input.substations(), Substation::mvKnown, Substation::mvComponent);
        Map<Integer, List<Apc>> apcsByMv = group(input.apcs(), Apc::mvKnown, Apc::mvComponent);
        for (var entry : apcsByMv.entrySet()) {
            List<Substation> subs = subsByMv.getOrDefault(entry.getKey(), List.of());
            List<PowerAllocation.Source> offers = subs.stream().map(sub -> new PowerAllocation.Source(sub.id(),
                    SUBSTATION_LIMIT * SUBSTATION_EFFICIENCY,
                    Math.min(SUBSTATION_LIMIT, hvInputs.getOrDefault(sub.id(), 0.0)) * SUBSTATION_EFFICIENCY)).toList();
            List<Apc> loads = entry.getValue();
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.MV, true,
                    offers, loads.stream().map(apc -> new PowerAllocation.Load(apc.id(), APC_INPUT_LIMIT)).toList(), null),
                    input.elapsedTicks());
            allocation.deliveredWatts().forEach(apcInputs::put);
        }

        Map<Integer, List<Apc>> apcsByApc = group(input.apcs(), Apc::apcKnown, Apc::apcComponent);
        Map<Integer, List<Lamp>> lampsByApc = group(input.lamps(), Lamp::known, Lamp::component);
        Map<Integer, List<VentLoad>> ventsByApc = group(input.vents(), VentLoad::known, VentLoad::apcComponent);
        Map<Integer, List<AlarmLoad>> alarmsByApc = group(input.alarms(), AlarmLoad::known, AlarmLoad::apcComponent);
        Map<Integer, List<AirlockLoad>> airlocksByApc = group(input.airlocks(), AirlockLoad::known, AirlockLoad::apcComponent);
        Map<Integer, List<DisposalLoad>> disposalByApc = group(input.disposal(), DisposalLoad::known, DisposalLoad::apcComponent);
        Map<Integer, List<ChemMasterLoad>> chemMastersByApc = group(input.chemMasters(), ChemMasterLoad::known, ChemMasterLoad::apcComponent);
        Map<String, Double> lampWatts = new HashMap<>();
        Map<String, Double> ventWatts = new HashMap<>();
        Map<String, Double> alarmWatts = new HashMap<>();
        Map<String, Double> airlockWatts = new HashMap<>();
        Map<String, Double> disposalWatts = new HashMap<>();
        Map<String, Double> chemMasterWatts = new HashMap<>();
        Map<String, Double> apcOutputWatts = new HashMap<>();
        Map<String, Double> energies = new HashMap<>();
        for (Lamp lamp : input.lamps()) lampWatts.put(lamp.id(), 0.0);
        for (VentLoad vent : input.vents()) ventWatts.put(vent.id(), 0.0);
        for (AlarmLoad alarm : input.alarms()) alarmWatts.put(alarm.id(), 0.0);
        for (AirlockLoad load : input.airlocks()) airlockWatts.put(load.id(), 0.0);
        for (DisposalLoad unit : input.disposal()) disposalWatts.put(unit.id(), 0.0);
        for (ChemMasterLoad load : input.chemMasters()) chemMasterWatts.put(load.id(), 0.0);
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
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.APC, true,
                     offers, demands,
                     battery), input.elapsedTicks());
            for (Lamp lamp : loads) lampWatts.put(lamp.id(), allocation.deliveredWatts().getOrDefault(lamp.id(), 0.0));
            for (VentLoad vent : ventLoads) ventWatts.put(vent.id(), allocation.deliveredWatts().getOrDefault(vent.id(), 0.0));
            for (AlarmLoad alarm : alarmLoads) alarmWatts.put(alarm.id(), allocation.deliveredWatts().getOrDefault(alarm.id(), 0.0));
            for (AirlockLoad load : airlockLoads) airlockWatts.put(load.id(), allocation.deliveredWatts().getOrDefault(load.id(), 0.0));
            for (DisposalLoad unit : disposalLoads) disposalWatts.put(unit.id(), allocation.deliveredWatts().getOrDefault(unit.id(), 0.0));
            for (ChemMasterLoad load : chemMasterLoads) chemMasterWatts.put(load.id(), allocation.deliveredWatts().getOrDefault(load.id(), 0.0));
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
                double chargeSeconds = seconds;
                double charge = chargeSeconds <= 0 ? 0 : Math.min(ownSurplus,
                        Math.min(BATTERY_CHARGE_RATE, (BATTERY_CAPACITY - energy) / chargeSeconds));
                energy = Math.min(BATTERY_CAPACITY, energy + charge * chargeSeconds);
                energies.put(apc.id(), energy);
            }

            // Open-breaker APC input is isolated from lamp delivery and discharge.
            // It can only charge its own known battery from external MV power.
            for (Apc apc : openChargeable) {
                double energy = validEnergy(apc.energyJoules());
                double ownInput = Math.min(APC_INPUT_LIMIT, apcInputs.getOrDefault(apc.id(), 0.0));
                double charge = seconds <= 0 ? 0 : Math.min(ownInput,
                        Math.min(BATTERY_CHARGE_RATE, (BATTERY_CAPACITY - energy) / seconds));
                energies.put(apc.id(), Math.min(BATTERY_CAPACITY, energy + charge * seconds));
            }
        }
        return new Result(Map.copyOf(hvInputs), Map.copyOf(apcInputs), Map.copyOf(lampWatts),
                  Map.copyOf(apcOutputWatts), Map.copyOf(energies), Map.copyOf(ventWatts), Map.copyOf(alarmWatts),
                  Map.copyOf(airlockWatts), Map.copyOf(disposalWatts), Map.copyOf(chemMasterWatts));
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
