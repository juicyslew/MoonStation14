package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.power.solver.PowerAllocation;

import java.util.*;

/** Pure component-wide accounting for the bounded live HV → MV → APC power loop. */
public final class PowerLiveLoop {
    private static final double SUBSTATION_LIMIT = 8_000;
    private static final double APC_LIMIT = 2_000;
    private static final double SOURCE_LIMIT = 10_000;
    private static final double LAMP_DEMAND = 100;
    private static final double BATTERY_CAPACITY = 1_000_000;
    private static final double SUBSTATION_EFFICIENCY = .9;
    private static final double BATTERY_RATE = 1_000;

    private PowerLiveLoop() { }

    public record Source(String id, int component, boolean known, double watts) { }
    public record Substation(String id, int hvComponent, boolean hvKnown, int mvComponent,
                             boolean mvKnown) { }
    public record Apc(String id, int mvComponent, boolean mvKnown, int apcComponent,
                      boolean apcKnown, boolean breakerClosed, double energyJoules) { }
    public record Lamp(String id, int component, boolean known) { }
    public record Input(List<Source> sources, List<Substation> substations, List<Apc> apcs,
                        List<Lamp> lamps, long elapsedTicks) {
        public Input {
            sources = List.copyOf(sources); substations = List.copyOf(substations);
            apcs = List.copyOf(apcs); lamps = List.copyOf(lamps);
        }
    }
    public record Result(Map<String, Double> substationInputWatts, Map<String, Double> apcInputWatts,
                         Map<String, Double> lampWatts, Map<String, Double> batteryEnergyJoules) { }

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
                    offers, loads.stream().map(apc -> new PowerAllocation.Load(apc.id(), APC_LIMIT)).toList(), null),
                    input.elapsedTicks());
            allocation.deliveredWatts().forEach(apcInputs::put);
        }

        Map<Integer, List<Apc>> apcsByApc = group(input.apcs(), Apc::apcKnown, Apc::apcComponent);
        Map<Integer, List<Lamp>> lampsByApc = group(input.lamps(), Lamp::known, Lamp::component);
        Map<String, Double> lampWatts = new HashMap<>();
        Map<String, Double> energies = new HashMap<>();
        for (Lamp lamp : input.lamps()) lampWatts.put(lamp.id(), 0.0);
        for (Apc apc : input.apcs()) energies.put(apc.id(), validEnergy(apc.energyJoules()));
        for (var entry : apcsByApc.entrySet()) {
            List<Apc> attachedApcs = entry.getValue();
            List<Apc> active = attachedApcs.stream().filter(Apc::breakerClosed).toList();
            List<Lamp> loads = lampsByApc.getOrDefault(entry.getKey(), List.of());
            double stored = active.stream().mapToDouble(apc -> validEnergy(apc.energyJoules())).sum();
            double capacity = active.size() * BATTERY_CAPACITY;
            PowerAllocation.Storage battery = active.isEmpty() ? null : new PowerAllocation.Storage(stored, capacity,
                    active.size() * BATTERY_RATE, active.size() * BATTERY_RATE, 1, 1);
            List<PowerAllocation.Source> offers = active.stream().map(apc -> new PowerAllocation.Source(apc.id(),
                    APC_LIMIT, Math.min(APC_LIMIT, apcInputs.getOrDefault(apc.id(), 0.0)))).toList();
            var allocation = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.APC, true,
                    offers, loads.stream().map(lamp -> new PowerAllocation.Load(lamp.id(), LAMP_DEMAND)).toList(),
                    battery), input.elapsedTicks());
            allocation.deliveredWatts().forEach(lampWatts::put);
            double delta = allocation.batteryEnergyJoules() - stored;
            distributeEnergy(active, energies, delta);
        }
        return new Result(Map.copyOf(hvInputs), Map.copyOf(apcInputs), Map.copyOf(lampWatts), Map.copyOf(energies));
    }

    private static <T> Map<Integer, List<T>> group(List<T> values, java.util.function.Predicate<T> known,
                                                    java.util.function.ToIntFunction<T> component) {
        Map<Integer, List<T>> groups = new HashMap<>();
        for (T value : values) if (known.test(value)) groups.computeIfAbsent(component.applyAsInt(value), ignored -> new ArrayList<>()).add(value);
        return groups;
    }

    private static void distributeEnergy(List<Apc> apcs, Map<String, Double> out, double delta) {
        if (apcs.isEmpty() || delta == 0) return;
        double available = apcs.stream().mapToDouble(a -> delta < 0 ? validEnergy(a.energyJoules())
                : BATTERY_CAPACITY - validEnergy(a.energyJoules())).sum();
        double remaining = Math.abs(delta);
        for (int i = 0; i < apcs.size(); i++) {
            Apc apc = apcs.get(i);
            double room = delta < 0 ? validEnergy(apc.energyJoules())
                    : BATTERY_CAPACITY - validEnergy(apc.energyJoules());
            double amount = available <= 0 ? 0 : i == apcs.size() - 1 ? Math.min(room, remaining)
                    : Math.min(room, Math.abs(delta) * room / available);
            out.put(apc.id(), validEnergy(apc.energyJoules()) + Math.copySign(amount, delta));
            remaining -= amount;
        }
    }

    private static double validEnergy(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(value, BATTERY_CAPACITY)) : 0;
    }
}
