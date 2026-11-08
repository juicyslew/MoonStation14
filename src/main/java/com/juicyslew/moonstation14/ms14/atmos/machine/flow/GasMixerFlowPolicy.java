package com.juicyslew.moonstation14.ms14.atmos.machine.flow;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure two-inlet mixer policy. Inputs remain distinct until each packet is extracted. */
public final class GasMixerFlowPolicy {
    public record Plan(GasMixture inletOneAfter, GasMixture inletTwoAfter,
                       GasMixture outletAfter, double inletOneMoles, double inletTwoMoles) { }
    private static final double R = 8.31446261815324;
    private GasMixerFlowPolicy() { }

    public static Optional<Plan> plan(GasMixture one, GasMixture two, GasMixture outlet,
            double volumeOneM3, double volumeTwoM3, double outletVolumeM3,
            double ratioOne, double targetPressureKpa, double maximumPressureKpa) {
        if (one == null || two == null || outlet == null || !Double.isFinite(ratioOne)
                || ratioOne < 0 || ratioOne > 1 || !Double.isFinite(targetPressureKpa)
                || targetPressureKpa < 0 || !Double.isFinite(maximumPressureKpa)
                || maximumPressureKpa < targetPressureKpa) throw new IllegalArgumentException("invalid mixer inputs");
        double pressure = outlet.pressureKpa(outletVolumeM3);
        if (pressure >= targetPressureKpa) return Optional.empty();
        double delta = targetPressureKpa - pressure;
        double demandOne = ratioOne == 0 ? 0 : ratioOne * delta * outletVolumeM3 * 1000 / (R * one.temperatureKelvin());
        double demandTwo = ratioOne == 1 ? 0 : (1-ratioOne) * delta * outletVolumeM3 * 1000 / (R * two.temperatureKelvin());
        if ((!Double.isFinite(demandOne)) || (!Double.isFinite(demandTwo))) throw new IllegalArgumentException("invalid inlet temperature");
        double scale = 1;
        if (demandOne > 0) scale = Math.min(scale, one.totalMoles() / demandOne);
        if (demandTwo > 0) scale = Math.min(scale, two.totalMoles() / demandTwo);
        scale = Math.max(0, Math.min(1, scale));
        double m1 = demandOne * scale, m2 = demandTwo * scale;
        Optional<PipeGasTransfer.Plan> p1 = PipeGasTransfer.plan(one, outlet, volumeOneM3, outletVolumeM3, m1, maximumPressureKpa, Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()));
        if (m1 > 0 && p1.isEmpty()) return Optional.empty();
        GasMixture afterOne = p1.map(PipeGasTransfer.Plan::sourceAfter).orElse(one);
        GasMixture interim = p1.map(PipeGasTransfer.Plan::destinationAfter).orElse(outlet);
        Optional<PipeGasTransfer.Plan> p2 = PipeGasTransfer.plan(two, interim, volumeTwoM3, outletVolumeM3, m2, maximumPressureKpa, Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()));
        if (m2 > 0 && p2.isEmpty()) return Optional.empty();
        GasMixture afterTwo = p2.map(PipeGasTransfer.Plan::sourceAfter).orElse(two);
        GasMixture after = p2.map(PipeGasTransfer.Plan::destinationAfter).orElse(interim);
        if (m1 == 0 && m2 == 0) return Optional.empty();
        // Validate combined species and thermal inventory across the three independent ports.
        PipeGasLedger.merge(List.of(afterOne, afterTwo, after));
        return Optional.of(new Plan(afterOne, afterTwo, after, p1.map(p -> p.packet().totalMoles()).orElse(0d),
                p2.map(p -> p.packet().totalMoles()).orElse(0d)));
    }

    public static Optional<Plan> plan(GasMixture one, GasMixture two, GasMixture outlet, double ratioOne) {
        double v = Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3;
        return plan(one, two, outlet, v, v, v, ratioOne, 101.325,
                Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA);
    }
}
