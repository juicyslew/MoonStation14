package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureEqualization;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineCadence;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureEqualization;

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

    /**
     * Local bound for a connected solver component of at most {@code componentCells}.
     * This overload is only for a complete, known finite component, not a sampled subset.
     * Each successful route is a BFS predecessor path with no repeated nodes, hence at most
     * cells-1 edge transfers; routes can revisit cells on later iterations, so retain the
     * solver's full route limit rather than assuming one route per recipient.
     */
    public static double operations(long completedAtmosphereCycles, int fixtureFacePairs, int paidIntervals,
                                    int componentCells) {
        if (fixtureFacePairs < 0 || paidIntervals < 0 || componentCells <= 0)
            throw new IllegalArgumentException("Invalid local atmosphere precision bound");
        long routeBound = (long) MonstermosPressureEqualization.DEFAULTS.routes()
                * Math.max(0, componentCells - 1);
        long perCycle = Math.min(MonstermosEqualization.MAX_TRANSFER_OPERATIONS,
                Math.min((long) Math.max(0, fixtureFacePairs) * MonstermosPressureEqualization.DEFAULTS.routes(),
                        routeBound));
        return 1.0 + Math.max(0, completedAtmosphereCycles) * perCycle
                + Math.max(0, paidIntervals);
    }

    /**
     * Source transfer opportunities plus a conservative pipe merge/split conversion bound.
     * PipeGasLedger.merge accumulates each species and energy over the component; split converts
     * each source species to float and performs one float share per node. Charge two scalar
     * opportunities per species/node plus energy/volume arithmetic per node (rather than counting
     * topology face pairs). One additional funded event covers either phase boundary.
     */
    public static double aggregateOperations(long cycles, int sourceCells, int pipeNodes,
                                             long elapsedTicks, double intervalSeconds) {
        if (sourceCells <= 0 || pipeNodes <= 0 || elapsedTicks < 0 || !Double.isFinite(intervalSeconds)
                || intervalSeconds <= 0) throw new IllegalArgumentException("Invalid aggregate precision bound");
        long funded = elapsedTicks / AtmosMachineCadence.intervalTicks(intervalSeconds) + 1;
        long sourcePerCycle = (long) MonstermosPressureEqualization.DEFAULTS.routes() * (sourceCells - 1);
        long pipePerEvent = (long) pipeNodes * (2L * GasType.values().length + 2);
        // FiniteVentilationTransfer computes both reference and transaction plans. Each
        // PipeGasTransfer.create constructs packet, source remainder and merged destination
        // (up to six mixture constructions across those planner paths); PipeGasTransaction then
        // commits/canonicalizes source and destination through each endpoint's writeRaw path.
        // Charge eight mixture opportunities per species/energy+temperature field. Planner-only
        // calculations do not themselves mutate inventory, so this is conservative without
        // treating intermediate values as additional physical conservation error.
        long transferPerEvent = 8L * (GasType.values().length + 2);
        return 1.0 + Math.max(0, cycles) * sourcePerCycle + funded * (pipePerEvent + transferPerEvent);
    }

    /** Both before/after inventories use this same aggregate bound for conservation and signal. */
    public static boolean conservedAndSignalExceedsBudget(Inventory before, Inventory after,
                                                           GasType signal, double signalDelta,
                                                           double operations) {
        for (GasType gas : GasType.values()) {
            if (Math.abs(before.moles(gas) - after.moles(gas)) > before.moleBudget(gas, after, operations)) return false;
        }
        if (Math.abs(before.energy() - after.energy()) > before.energyBudget(after, operations)) return false;
        return before.moleBudget(signal, after, operations) < signalDelta;
    }
}
