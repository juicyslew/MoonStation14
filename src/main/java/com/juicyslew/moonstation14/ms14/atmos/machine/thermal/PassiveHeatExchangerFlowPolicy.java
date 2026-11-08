package com.juicyslew.moonstation14.ms14.atmos.machine.thermal;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;

import java.util.List;
import java.util.Optional;

/** Signed, bidirectional SS14 heat-exchanger pressure-flow step. */
public final class PassiveHeatExchangerFlowPolicy {
    private static final double R = 8.3144626;
    private PassiveHeatExchangerFlowPolicy() { }
    public record Plan(GasMixture inletAfter, GasMixture outletAfter, GasMixture packet,
                       double movedMoles, boolean receivingInlet) { }

    public static Optional<Plan> plan(GasMixture inlet, GasMixture outlet, double conductivity,
                                      double dtSeconds) {
        if (inlet == null || outlet == null || !Double.isFinite(conductivity) || conductivity < 0
                || !Double.isFinite(dtSeconds) || dtSeconds < 0)
            throw new IllegalArgumentException("invalid exchanger flow input");
        double vi = inlet.volumeCubicMeters(), vo = outlet.volumeCubicMeters();
        if (vi <= 0 || vo <= 0 || dtSeconds == 0 || conductivity == 0)
            return Optional.empty();
        double dpdn = R * (outlet.temperatureKelvin() / vo + inlet.temperatureKelvin() / vi) / 1000.0;
        if (!Double.isFinite(dpdn) || dpdn <= 0) return Optional.empty();
        double pressureDelta = inlet.pressureKpa() - outlet.pressureKpa();
        if (pressureDelta == 0) return Optional.empty();
        double moles = pressureDelta * (1 - Math.exp(-conductivity * dpdn * dtSeconds)) / dpdn;
        GasMixture source = moles > 0 ? inlet : outlet;
        GasMixture destination = moles > 0 ? outlet : inlet;
        if (source.totalMoles() <= 0) return Optional.empty();
        double amount = Math.min(Math.abs(moles), source.totalMoles());
        if (amount < GasMixture.GAS_MIN_MOLES) return Optional.empty();
        double ratio = amount / source.totalMoles();
        GasMixture.Removal removal = source.splitRatio(ratio);
        if (removal.removed().totalMoles() < GasMixture.GAS_MIN_MOLES) return Optional.empty();
        GasMixture mergedContents = PipeGasLedger.merge(List.of(destination, removal.removed()));
        GasMixture merged = new GasMixture(mergedContents.gasMoles(), mergedContents.temperatureKelvin(),
                destination.volumeCubicMeters());
        return moles > 0
                ? Optional.of(new Plan(removal.remaining(), merged, removal.removed(), removal.removed().totalMoles(), false))
                : Optional.of(new Plan(merged, removal.remaining(), removal.removed(), removal.removed().totalMoles(), true));
    }
}
