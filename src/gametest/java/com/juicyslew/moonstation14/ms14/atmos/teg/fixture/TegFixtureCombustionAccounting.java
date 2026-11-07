package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Validates committed SS14 combustion events; this observes chemistry and never changes it. */
final class TegFixtureCombustionAccounting {
    static final double EVENT_TOLERANCE = 1e-9;
    record Snapshot(Map<GasType,Double> netSpeciesDelta, double plasmaConsumed, double carbonDioxideProduced, double tritiumProduced,
                    double tritiumConsumed, double waterProduced, double oxygenConsumed,
                    double chemicalHeatJoules, int eventCount) { }

    private final Map<GasType, Double> delta = new EnumMap<>(GasType.class);
    private double heat, plasmaConsumed, tritiumProduced, tritiumConsumed, waterProduced, oxygenConsumed, carbonDioxideProduced;
    private int events;

    void observe(List<GasReactionEvaluator.Event> observed) {
        if (observed == null) return;
        for (var event : observed) {
            var d = event.speciesDelta();
            for (var entry : d.entrySet()) require(Double.isFinite(entry.getValue()), "non-finite event species delta");
            if (event.effect() == GasReactionData.EffectType.PLASMA_FIRE) {
                require(only(d, GasType.PLASMA, GasType.OXYGEN, GasType.CARBON_DIOXIDE, GasType.TRITIUM), "unsupported plasma-fire species");
                double consumed = -d.getOrDefault(GasType.PLASMA, 0d);
                double co2 = d.getOrDefault(GasType.CARBON_DIOXIDE, 0d);
                double tri = d.getOrDefault(GasType.TRITIUM, 0d);
                double oxygen = -d.getOrDefault(GasType.OXYGEN, 0d);
                require(consumed > 0 && close(consumed, co2 + tri) && oxygen >= .4 * consumed - EVENT_TOLERANCE
                        && oxygen <= 1.4 * consumed + EVENT_TOLERANCE, "invalid plasma-fire stoichiometry");
                require(close(event.energyDeltaJoules(), 160000 * consumed), "invalid plasma-fire heat");
                plasmaConsumed += consumed;
                carbonDioxideProduced += co2; tritiumProduced += tri; oxygenConsumed += oxygen;
            } else if (event.effect() == GasReactionData.EffectType.TRITIUM_FIRE) {
                require(only(d, GasType.TRITIUM, GasType.OXYGEN, GasType.WATER_VAPOR), "unsupported tritium-fire species");
                double consumed = -d.getOrDefault(GasType.TRITIUM, 0d);
                require(consumed > 0 && close(-d.getOrDefault(GasType.OXYGEN, 0d), .5 * consumed)
                        && close(d.getOrDefault(GasType.WATER_VAPOR, 0d), consumed), "invalid tritium-fire stoichiometry");
                require(close(event.energyDeltaJoules(), 284000 * consumed)
                        || close(event.energyDeltaJoules(), 2840000 * consumed), "invalid tritium-fire heat");
                tritiumConsumed += consumed; waterProduced += d.getOrDefault(GasType.WATER_VAPOR, 0d);
                oxygenConsumed += -d.getOrDefault(GasType.OXYGEN, 0d);
            } else throw new IllegalArgumentException("unsupported committed chemical effect: " + event.effect());
            for (var entry : d.entrySet()) delta.merge(entry.getKey(), entry.getValue(), Double::sum);
            heat += event.energyDeltaJoules(); events++;
        }
    }

    Snapshot snapshot() {
        return new Snapshot(Map.copyOf(delta), plasmaConsumed, carbonDioxideProduced, tritiumProduced,
                tritiumConsumed, waterProduced, oxygenConsumed, heat, events);
    }
    static void verifyLocalClosure(Map<GasType,Double> before, Map<GasType,Double> after,
                                   Map<GasType,Double> injected, Map<GasType,Double> extracted, Snapshot committed) {
        verifyLocalClosure(before, after, injected, extracted, committed, Map.of(), Map.of());
    }
    static void verifyLocalClosure(Map<GasType,Double> before, Map<GasType,Double> after,
                                   Map<GasType,Double> injected, Map<GasType,Double> extracted, Snapshot committed,
                                   Map<GasType,Double> writeBounds) {
        verifyLocalClosure(before,after,injected,extracted,committed,writeBounds,Map.of());
    }
    static void verifyLocalClosure(Map<GasType,Double> before, Map<GasType,Double> after,
                                   Map<GasType,Double> injected, Map<GasType,Double> extracted, Snapshot committed,
                                   Map<GasType,Double> writeBounds, Map<GasType,Double> cleanup) {
        require(writeBounds != null, "missing write observation bounds");
        require(cleanup != null, "missing actual cutoff cleanup receipt");
        for (var entry : writeBounds.entrySet()) {
            require(entry.getKey() != null && entry.getValue() != null && Double.isFinite(entry.getValue()) && entry.getValue() >= 0,
                    "invalid write observation allowance");
        }
        double chemicalEquivalent = 160000 * writeBounds.getOrDefault(GasType.PLASMA,0d)
                + 2840000 * (writeBounds.getOrDefault(GasType.TRITIUM,0d)+writeBounds.getOrDefault(GasType.WATER_VAPOR,0d));
        for (var entry : cleanup.entrySet()) {
            require(entry.getKey()!=null&&entry.getValue()!=null&&Double.isFinite(entry.getValue())&&entry.getValue()>0,
                    "invalid actual cutoff cleanup receipt");
            double coefficient=entry.getKey()==GasType.PLASMA?160000
                    :entry.getKey()==GasType.TRITIUM||entry.getKey()==GasType.WATER_VAPOR?2840000:0;
            chemicalEquivalent+=coefficient*entry.getValue();
        }
        require(Double.isFinite(chemicalEquivalent) && chemicalEquivalent < TegFixtureEnergyBalance.MATERIAL_MARGIN_JOULES,
                "write observation allowance exceeds strict chemical margin");
        for (GasType gas : GasType.values()) {
            double a=value(before,gas), b=value(after,gas), in=value(injected,gas), out=value(extracted,gas);
            double actual=(b-a)-in+out, expected=value(committed.netSpeciesDelta(),gas)-cleanup.getOrDefault(gas,0d);
            double endpoint=localBudget(a,b,in,out,expected), write=writeBounds.getOrDefault(gas,0d), tolerance=endpoint+write;
            require(Math.abs(actual-expected)<=tolerance, "reaction observation coverage incomplete or local material closure failed for "
                    +gas+" localNet="+actual+" committed="+expected+" residual="+(actual-expected)+" endpointAllowance="+endpoint+" writeAllowance="+write);
        }
    }
    private static double value(Map<GasType,Double> values,GasType gas) { double v=values.getOrDefault(gas,0d);require(Double.isFinite(v),"non-finite local stock/packet value: "+gas);return v; }
    private static double localBudget(double... operands) {
        double sum=0;
        for(double value:operands){require(Double.isFinite(value),"non-finite local closure operand");float f=(float)value;
            require(Float.isFinite(f),"local closure operand exceeds float range");sum+=Math.max(8*Math.ulp(f),8*Math.ulp(value));}
        return sum;
    }
    private static boolean only(Map<GasType,Double> values, GasType... allowed) { var set=java.util.EnumSet.noneOf(GasType.class);java.util.Collections.addAll(set,allowed);return values.keySet().stream().allMatch(set::contains); }
    private static boolean close(double a, double b) { return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a-b) <= Math.max(EVENT_TOLERANCE, 1e-7*Math.max(Math.abs(a),Math.abs(b))); }
    private static void require(boolean okay, String message) { if (!okay) throw new IllegalArgumentException(message); }
}
