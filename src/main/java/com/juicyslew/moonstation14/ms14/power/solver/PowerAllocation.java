package com.juicyslew.moonstation14.ms14.power.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure, deterministic power accounting. Power is watts; stored energy is joules. */
public final class PowerAllocation {
    public static final int TICKS_PER_SECOND = 20;
    /** Bounds protect arithmetic and keep corrupt external inputs from overflowing accounting. */
    public static final double MAX_VALUE = 1.0e12;

    private PowerAllocation() { }

    public enum Tier { HV, MV, APC }

    /** Output is clipped to the source's declared output limit. IDs establish stable accounting order. */
    public record Source(String id, double outputLimitWatts, double outputWatts) {
        public Source { if (id == null || id.isBlank()) throw new IllegalArgumentException("source id"); }
    }

    public record Load(String id, double demandWatts) {
        public Load { if (id == null || id.isBlank()) throw new IllegalArgumentException("load id"); }
    }

    /** Charge/discharge efficiencies are fractions in (0, 1]; one-way energy losses are explicit. */
    public record Storage(double energyJoules, double capacityJoules,
                          double maxChargeWatts, double maxDischargeWatts,
                          double chargeEfficiency, double dischargeEfficiency) { }

    public record Network(Tier tier, boolean graphKnown, List<Source> sources, List<Load> loads,
                          Storage storage) {
        public Network {
            if (tier == null) throw new NullPointerException("tier");
            sources = sources == null ? List.of() : List.copyOf(sources);
            loads = loads == null ? List.of() : List.copyOf(loads);
        }
    }

    public record Result(Tier tier, boolean graphKnown, Map<String, Double> deliveredWatts,
                         double sourceAvailableWatts, double sourceUsedWatts,
                         double batteryOutputWatts, double batteryInputWatts,
                         double batteryEnergyJoules, double curtailedWatts,
                         double unmetLoadWatts, double elapsedSeconds) { }

    /**
     * Evaluates one already-resolved typed network for elapsed ticks. Unknown/incomplete graphs
     * fail closed: no generation, load delivery, storage movement, or energized claim is made.
     */
    public static Result solve(Network network, long elapsedTicks) {
        if (network == null) throw new NullPointerException("network");
        double seconds = safe(elapsedTicks) / TICKS_PER_SECOND;
        double stored = network.storage() == null ? 0 : clamp(network.storage().energyJoules(), 0,
                clamp(network.storage().capacityJoules(), 0, MAX_VALUE));
        Map<String, Double> deliveries = new LinkedHashMap<>();
        network.loads().stream().sorted(Comparator.comparing(Load::id))
                .forEach(load -> deliveries.put(load.id(), 0.0));
        if (!network.graphKnown()) {
            return new Result(network.tier(), false, Map.copyOf(deliveries), 0, 0, 0, 0,
                    stored, 0, sumLoads(network.loads()), seconds);
        }

        double source = 0;
        for (Source item : network.sources().stream().sorted(Comparator.comparing(Source::id)).toList()) {
            source = add(source, Math.min(nonNegative(item.outputWatts()), nonNegative(item.outputLimitWatts())));
        }
        double demand = sumLoads(network.loads());
        Storage battery = network.storage();
        double dischargeWatts = 0;
        double dischargeEfficiency = battery == null ? 1 : efficiency(battery.dischargeEfficiency());
        if (source < demand && battery != null && seconds > 0) {
            double energyLimitedOutput = stored * dischargeEfficiency / seconds;
            dischargeWatts = Math.min(demand - source, Math.min(nonNegative(battery.maxDischargeWatts()), energyLimitedOutput));
        }

        double available = add(source, dischargeWatts);
        double served = Math.min(available, demand);
        allocate(network.loads(), served, demand, deliveries);
        double sourceUsed = Math.min(source, served);
        double batteryUsed = Math.max(0, served - sourceUsed);
        double newEnergy = stored;
        if (battery != null && seconds > 0) {
            newEnergy = Math.max(0, stored - batteryUsed * seconds / dischargeEfficiency);
        }

        double surplus = Math.max(0, source - sourceUsed);
        double chargeWatts = 0;
        if (battery != null && seconds > 0 && surplus > 0) {
            double chargeEfficiency = efficiency(battery.chargeEfficiency());
            double roomLimitedInput = Math.max(0, clamp(battery.capacityJoules(), 0, MAX_VALUE) - newEnergy)
                    / (seconds * chargeEfficiency);
            chargeWatts = Math.min(surplus, Math.min(nonNegative(battery.maxChargeWatts()), roomLimitedInput));
            newEnergy = Math.min(clamp(battery.capacityJoules(), 0, MAX_VALUE),
                    newEnergy + chargeWatts * seconds * chargeEfficiency);
        }
        double curtailed = Math.max(0, source - sourceUsed - chargeWatts);
        return new Result(network.tier(), true, Map.copyOf(deliveries), source, sourceUsed,
                batteryUsed, chargeWatts, newEnergy, curtailed, Math.max(0, demand - served), seconds);
    }

    /** One-way explicit transformer/APC port contract. No graph merge or implicit tier conversion. */
    public record Bridge(Tier inputTier, Tier outputTier, double maxInputWatts,
                         double efficiency, boolean breakerClosed, Kind kind) {
        public enum Kind { HV_TO_MV_SUBSTATION, MV_TO_APC_BATTERY_BACKED_APC }
        public Bridge {
            if (inputTier == null || outputTier == null || kind == null) throw new NullPointerException();
            boolean valid = (kind == Kind.HV_TO_MV_SUBSTATION && inputTier == Tier.HV && outputTier == Tier.MV)
                    || (kind == Kind.MV_TO_APC_BATTERY_BACKED_APC && inputTier == Tier.MV && outputTier == Tier.APC);
            if (!valid) throw new IllegalArgumentException("bridge tier direction does not match bridge kind");
        }

        /** Transfers only when both graph sides are known and the breaker is closed. */
        public Transfer transfer(double offeredInputWatts, boolean inputGraphKnown, boolean outputGraphKnown) {
            if (!breakerClosed || !inputGraphKnown || !outputGraphKnown) return new Transfer(0, 0, 0);
            double input = Math.min(nonNegative(offeredInputWatts), nonNegative(maxInputWatts));
            double output = input * PowerAllocation.efficiency(efficiency);
            return new Transfer(input, output, Math.max(0, input - output));
        }
    }

    public record Transfer(double inputWatts, double outputWatts, double lossWatts) { }

    private static void allocate(List<Load> loads, double served, double demand, Map<String, Double> out) {
        List<Load> ordered = new ArrayList<>(loads);
        ordered.sort(Comparator.comparing(Load::id));
        if (demand <= 0 || served <= 0) return;
        double ratio = Math.min(1, served / demand);
        double remaining = served;
        for (int i = 0; i < ordered.size(); i++) {
            Load load = ordered.get(i);
            double requested = nonNegative(load.demandWatts());
            double allocated = i == ordered.size() - 1 ? Math.min(requested, remaining)
                    : Math.min(requested * ratio, remaining);
            out.merge(load.id(), allocated, Double::sum);
            remaining = Math.max(0, remaining - allocated);
        }
    }

    private static double sumLoads(List<Load> loads) {
        double sum = 0;
        for (Load load : loads) sum = add(sum, nonNegative(load.demandWatts()));
        return sum;
    }

    private static double safe(long value) { return Math.max(0, Math.min((double) value, MAX_VALUE)); }
    private static double nonNegative(double value) {
        if (Double.isNaN(value) || value <= 0) return 0;
        return Math.min(value, MAX_VALUE);
    }
    private static double add(double a, double b) { return Math.min(MAX_VALUE, a + b); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(nonNegative(value), max)); }
    private static double efficiency(double value) {
        return Double.isNaN(value) ? 1.0e-9 : Math.max(1.0e-9, Math.min(nonNegative(value), 1));
    }
}
