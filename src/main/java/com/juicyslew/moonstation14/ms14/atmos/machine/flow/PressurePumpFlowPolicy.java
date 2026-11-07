package com.juicyslew.moonstation14.ms14.atmos.machine.flow;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;

import java.util.Optional;
import java.util.Set;

/** Pure pressure-pump transfer policy; enabled/power gating belongs to its caller. */
public final class PressurePumpFlowPolicy {
    private static final double R = 8.31446261815324;
    public static final double DEFAULT_TARGET_PRESSURE_KPA = 101.325;

    private PressurePumpFlowPolicy() { }

    public static Optional<PipeGasTransfer.Plan> plan(GasMixture inlet, GasMixture outlet,
            double inletVolumeM3, double outletVolumeM3, double targetPressureKpa,
            double maximumPressureKpa) {
        if (inlet == null || outlet == null || !Double.isFinite(targetPressureKpa)
                || !Double.isFinite(maximumPressureKpa) || targetPressureKpa < 0
                || maximumPressureKpa < targetPressureKpa) throw new IllegalArgumentException("invalid pump inputs");
        if (inlet.totalMoles() <= 0 || outlet.pressureKpa(outletVolumeM3) >= targetPressureKpa)
            return Optional.empty();
        double requested = (targetPressureKpa - outlet.pressureKpa(outletVolumeM3))
                * 1000.0 * outletVolumeM3 / (R * inlet.temperatureKelvin());
        if (!Double.isFinite(requested) || requested <= 0) return Optional.empty();
        return PipeGasTransfer.plan(inlet, outlet, inletVolumeM3, outletVolumeM3, requested,
                maximumPressureKpa, Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()));
    }

    public static Optional<PipeGasTransfer.Plan> plan(GasMixture inlet, GasMixture outlet) {
        return plan(inlet, outlet, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3,
                Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3, DEFAULT_TARGET_PRESSURE_KPA,
                Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA);
    }
}
