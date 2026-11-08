package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.Objects;

/** Pure SS14-style one-way pressure release between finite mixtures. */
public final class GasPressureRelease {
    private GasPressureRelease() { }

    public static ReleaseResult release(GasMixture source, GasMixture receiver, double targetPressureKpa) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(receiver, "receiver");
        if (!Double.isFinite(targetPressureKpa) || targetPressureKpa < 0)
            throw new IllegalArgumentException("targetPressureKpa must be finite and nonnegative");

        double sourcePressure = source.pressureKpa();
        double receiverPressure = receiver.pressureKpa();
        if (receiverPressure >= Math.min(targetPressureKpa, sourcePressure - 10)
                || !(source.totalMoles() > 0) || !(source.temperatureKelvin() > 0)) {
            return new ReleaseResult(source, receiver, 0);
        }

        double pressureDelta = Math.min(targetPressureKpa - receiverPressure,
                (sourcePressure - receiverPressure) / 2.0);
        double requestedMoles = GasMixture.molesForPressureDelta(pressureDelta,
                receiver.volumeCubicMeters(), source.temperatureKelvin());
        double transferred = Math.min(source.totalMoles(), Math.max(0, requestedMoles));
        if (!(transferred > 0)) return new ReleaseResult(source, receiver, 0);

        double ratio = transferred / source.totalMoles();
        GasMixture.Removal removal = source.splitRatio(ratio);
        GasMixture afterReceiver = receiver.mergedWith(removal.removed());
        return new ReleaseResult(removal.remaining(), afterReceiver, removal.removed().totalMoles());
    }

    public record ReleaseResult(GasMixture sourceAfter, GasMixture receiverAfter, double transferredMoles) {
        public ReleaseResult {
            Objects.requireNonNull(sourceAfter, "sourceAfter");
            Objects.requireNonNull(receiverAfter, "receiverAfter");
            if (!Double.isFinite(transferredMoles) || transferredMoles < 0)
                throw new IllegalArgumentException("transferredMoles must be finite and nonnegative");
        }
    }
}

