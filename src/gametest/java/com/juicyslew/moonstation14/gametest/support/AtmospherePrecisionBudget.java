package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureEqualization;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Float-storage rounding budget scaled by measured atmosphere operations and sampled cells. */
public final class AtmospherePrecisionBudget {
    private AtmospherePrecisionBudget() { }

    public record Inventory(Map<GasType, Double> moles, Map<GasType, Double> moleUlpSums,
                            double energy, double energyUlpSum) {
        public static Inventory capture(List<GasMixture> cells) {
            Map<GasType, Double> moles = new EnumMap<>(GasType.class);
            Map<GasType, Double> ulps = new EnumMap<>(GasType.class);
            for (GasType gas : GasType.values()) { moles.put(gas, 0.0); ulps.put(gas, 0.0); }
            double energy = 0, energyUlps = 0;
            for (GasMixture mixture : cells) {
                for (GasType gas : GasType.values()) {
                    double amount = mixture.moles(gas);
                    moles.merge(gas, amount, Double::sum);
                    ulps.merge(gas, (double) Math.ulp((float) amount), Double::sum);
                }
                energy += mixture.thermalEnergy();
                energyUlps += Math.ulp((float) mixture.thermalEnergy());
            }
            return new Inventory(Map.copyOf(moles), Map.copyOf(ulps), energy, energyUlps);
        }
        public double moles(GasType gas) { return moles.getOrDefault(gas, 0.0); }
        public double totalMoles() { return moles.values().stream().mapToDouble(Double::doubleValue).sum(); }
        public double moleBudget(GasType gas, Inventory after, double operations) {
            return operations * (moleUlpSums.get(gas) + after.moleUlpSums.get(gas)
                    + Math.ulp((float) moles(gas)) + Math.ulp((float) after.moles(gas)));
        }
        public double energyBudget(Inventory after, double operations) {
            return operations * (energyUlpSum + after.energyUlpSum
                    + Math.ulp((float) energy) + Math.ulp((float) after.energy));
        }
    }

    /**
     * Local rounding opportunities: each completed atmosphere cycle is charged only against
     * undirected face pairs in the measured fixture cell set, capped by the pressure solver's
     * configured route and transfer-operation limits; each explicit paid ventilation transaction
     * contributes one additional mixture conversion. No world-wide transfer counters are used.
     * AtmosphereService.TICK_CADENCE is two server ticks.
     */
    public static double operations(long completedAtmosphereCycles, int fixtureFacePairs, int paidIntervals) {
        long perCycle = Math.min(MonstermosEqualization.MAX_TRANSFER_OPERATIONS,
                (long) Math.max(0, fixtureFacePairs) * MonstermosPressureEqualization.DEFAULTS.routes());
        return 1.0 + Math.max(0, completedAtmosphereCycles) * perCycle
                + Math.max(0, paidIntervals);
    }
}
