package com.juicyslew.moonstation14.ms14.atmos.teg;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;
import java.util.Objects;
import java.util.Set;

/** Pure SS14 TEG circulator inlet-to-outlet extraction plan. */
public final class TegCirculationPolicy {
    private TegCirculationPolicy() { }

    public static Result plan(GasMixture inlet, GasMixture outlet,
                              double inletVolumeM3, double outletVolumeM3) {
        Objects.requireNonNull(inlet, "inlet");
        Objects.requireNonNull(outlet, "outlet");
        positiveFinite(inletVolumeM3, "inletVolumeM3");
        positiveFinite(outletVolumeM3, "outletVolumeM3");
        double n1 = inlet.totalMoles(), n2 = outlet.totalMoles();
        double p1 = inlet.pressureKpa(inletVolumeM3), p2 = outlet.pressureKpa(outletVolumeM3);
        double t1 = inlet.temperatureKelvin(), t2 = outlet.temperatureKelvin();
        double delta = p1 - p2;
        double denominator = t1 * outletVolumeM3 + t2 * inletVolumeM3;
        double requested = 0;
        if (delta > 0 && p1 > 0 && denominator > 0) {
            requested = n1 - (n1 + n2) * t2 * inletVolumeM3 / denominator;
            // Upstream removal cannot remove a negative amount; guard roundoff at equilibrium.
            requested = Math.max(0, requested);
        }
        GasMixture packet = GasMixture.vacuum();
        GasMixture remaining = inlet;
        if (requested > 0) {
            var plan = PipeGasTransfer.plan(inlet, GasMixture.vacuum(), inletVolumeM3,
                    outletVolumeM3, requested, Double.MAX_VALUE, Set.of(GasType.values()))
                    .orElse(null);
            if (plan != null) {
                packet = plan.packet();
                remaining = plan.sourceAfter();
            }
        }
        return new Result(remaining, packet, delta, packet.totalMoles());
    }

    private static void positiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive and finite");
    }

    public record Result(GasMixture inletAfter, GasMixture packet, double pressureDeltaKpa,
                         double transferredMoles) {
        public Result {
            Objects.requireNonNull(inletAfter, "inletAfter");
            Objects.requireNonNull(packet, "packet");
            if (!Double.isFinite(pressureDeltaKpa) || !Double.isFinite(transferredMoles) || transferredMoles < 0)
                throw new IllegalArgumentException("invalid circulation result");
        }
    }
}
