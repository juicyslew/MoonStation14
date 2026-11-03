package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import java.util.Map;

/** Pure regulated draw calculation; callers decide when an authoritative snapshot is committed. */
public final class GasTankDrawHelper {
    private GasTankDrawHelper() { }
    public record Draw(GasTankContents remaining, GasMixture inhaled) { }
    public static Draw drawOutput(GasTankContents contents, GasTankData profile, double breathVolumeLiters) {
        return drawOutput(contents, profile, breathVolumeLiters, Double.POSITIVE_INFINITY);
    }
    /** Draw capped by the caller's remaining finite receiver capacity. */
    public static Draw drawOutput(GasTankContents contents, GasTankData profile, double breathVolumeLiters, double maxMoles) {
        if (contents == null || profile == null || !Double.isFinite(breathVolumeLiters) || breathVolumeLiters < 0)
            throw new IllegalArgumentException("Invalid tank draw request");
        if (Double.isNaN(maxMoles) || maxMoles < 0) throw new IllegalArgumentException("Invalid draw cap");
        GasMixture tank = contents.mixture();
        double volume = breathVolumeLiters / 1000.0;
        double requested = Math.min(maxMoles, GasMixture.molesForPressureDelta(profile.outletPressureKpa(), volume, tank.temperatureKelvin()));
        // SS14 RemoveAirOutput returns the entire available inventory when it is smaller than
        // the requested output. Preserve that final packet exactly: splitRatio applies the
        // atmosphere's GAS_MIN_MOLES cleanup to both sides and can otherwise erase a tiny but
        // real finite tank charge instead of delivering it to the validated lung candidate.
        if (tank.totalMoles() > 0 && tank.totalMoles() <= requested) {
            GasMixture packet = new GasMixture(tank.gasMoles(), tank.temperatureKelvin(), volume);
            return new Draw(contents.withMixture(new GasMixture(Map.of(), tank.temperatureKelvin(),
                    tank.volumeCubicMeters())), packet);
        }
        double fraction = tank.totalMoles() == 0 ? 0 : Math.min(1, requested / tank.totalMoles());
        GasMixture.Removal split = tank.splitRatio(fraction);
        GasMixture inhaled = new GasMixture(split.removed().gasMoles(), tank.temperatureKelvin(), volume);
        return new Draw(contents.withMixture(split.remaining()), inhaled);
    }
}
