package com.juicyslew.moonstation14.ms14.atmos.machine.flow;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.EnumSet;

/** Pure volume-pump tick policy. World availability and enabled/power gates belong to the caller. */
public final class VolumePumpFlowPolicy {
    public record Plan(GasMixture inletAfter, GasMixture outletAfter,
                       Optional<GasMixture> environmentAfter, Map<GasType, Double> movedByGas,
                       double energyMoved) {
        public Plan { environmentAfter = environmentAfter == null ? Optional.empty() : environmentAfter; movedByGas = Map.copyOf(movedByGas); }
    }
    private VolumePumpFlowPolicy() { }

    public static Optional<Plan> plan(GasMixture inlet, GasMixture outlet, Optional<GasMixture> environment,
            double inletVolumeM3, double outletVolumeM3, double rateM3PerSecond,
            double pumpSpeedup, double deltaSeconds, double lowerPressureKpa,
            double higherPressureKpa, boolean overclocked, double overclockThresholdKpa,
            double leakRatio) {
        if (inlet == null || outlet == null) throw new IllegalArgumentException("mixtures are required");
        environment = environment == null ? Optional.empty() : environment;
        finitePositive(inletVolumeM3, "inletVolumeM3"); finitePositive(outletVolumeM3, "outletVolumeM3");
        finiteNonnegative(rateM3PerSecond, "rateM3PerSecond"); finiteNonnegative(pumpSpeedup, "pumpSpeedup");
        finiteNonnegative(deltaSeconds, "deltaSeconds"); finiteNonnegative(lowerPressureKpa, "lowerPressureKpa");
        finiteNonnegative(higherPressureKpa, "higherPressureKpa"); finiteNonnegative(overclockThresholdKpa, "overclockThresholdKpa");
        if (!Double.isFinite(leakRatio) || leakRatio < 0 || leakRatio > 1) throw new IllegalArgumentException("leakRatio must be in [0,1]");
        double inputPressure = inlet.pressureKpa(inletVolumeM3), outputPressure = outlet.pressureKpa(outletVolumeM3);
        if (inputPressure <= lowerPressureKpa || (outputPressure >= higherPressureKpa && !overclocked)
                || (overclocked && outputPressure - inputPressure >= overclockThresholdKpa)
                || inlet.totalMoles() == 0 || rateM3PerSecond == 0 || pumpSpeedup == 0 || deltaSeconds == 0)
            return Optional.empty();

        double movedVolume = Math.min(inletVolumeM3, rateM3PerSecond * pumpSpeedup * deltaSeconds);
        if (!Double.isFinite(movedVolume) || movedVolume <= 0) return Optional.empty();
        double requestedMoles = inlet.totalMoles() * movedVolume / inletVolumeM3;
        double maxPressure = higherPressureKpa;
        double leak = overclocked && environment.isPresent() ? leakRatio : 0;
        // Extract once against an unconstrained temporary receiver, then enforce the real outlet cap
        // through the shared planner after removing any overclock leak.
        GasMixture empty = new GasMixture(Map.of(), inlet.temperatureKelvin(), inletVolumeM3);
        Optional<PipeGasTransfer.Plan> extraction = PipeGasTransfer.plan(inlet, empty, inletVolumeM3,
                inletVolumeM3, requestedMoles, Double.MAX_VALUE, EnumSet.allOf(GasType.class));
        if (extraction.isEmpty()) return Optional.empty();
        GasMixture packet = extraction.get().packet();
        GasMixture leaked = empty;
        if (leak > 0 && packet.totalMoles() > 0) {
            GasMixture emptyLeakDest = new GasMixture(Map.of(), packet.temperatureKelvin(), inletVolumeM3);
            var leakPlan = PipeGasTransfer.plan(packet, emptyLeakDest, inletVolumeM3, inletVolumeM3,
                    packet.totalMoles() * leak, Double.MAX_VALUE, EnumSet.allOf(GasType.class));
            if (leakPlan.isPresent()) { packet = leakPlan.get().sourceAfter(); leaked = leakPlan.get().packet(); }
        }
        var outputPlan = PipeGasTransfer.plan(packet, outlet, inletVolumeM3, outletVolumeM3,
                packet.totalMoles(), maxPressure, EnumSet.allOf(GasType.class));
        if (outputPlan.isEmpty()) return Optional.empty();
        GasMixture inletAfter = PipeGasLedger.merge(List.of(extraction.get().sourceAfter(), outputPlan.get().sourceAfter()), inletVolumeM3);
        Optional<GasMixture> environmentAfter = environment;
        if (leak > 0 && leaked.totalMoles() > 0) {
            var envPlan = PipeGasTransfer.plan(leaked, environment.orElseThrow(), inletVolumeM3,
                    environment.orElseThrow().volumeCubicMeters(), leaked.totalMoles(), Double.MAX_VALUE, EnumSet.allOf(GasType.class));
            if (envPlan.isEmpty()) return Optional.empty();
            environmentAfter = Optional.of(envPlan.get().destinationAfter());
        }
        java.util.EnumMap<GasType, Double> moved = new java.util.EnumMap<>(GasType.class);
        moved.putAll(outputPlan.get().perGasMoved());
        if (leak > 0) leaked.gasMoles().forEach((gas, amount) -> moved.merge(gas, amount, Double::sum));
        double energy = outputPlan.get().energyMoved() + (leak > 0 ? leaked.thermalEnergy() : 0);
        return Optional.of(new Plan(inletAfter, outputPlan.get().destinationAfter(), environmentAfter, moved, energy));
    }

    private static void finitePositive(double n, String name) { if (!Double.isFinite(n) || n <= 0) throw new IllegalArgumentException(name + " must be positive and finite"); }
    private static void finiteNonnegative(double n, String name) { if (!Double.isFinite(n) || n < 0) throw new IllegalArgumentException(name + " must be finite and nonnegative"); }
}
