package com.juicyslew.moonstation14.ms14.atmos.exposure;

/** Pure SS14-style regulator: heat is joules, positive warms the body. No gas reservoir is touched. */
public final class ThermalRegulatorMath {
    public static final double MIN_BODY_KELVIN = 2.7;
    public static final double MAX_BODY_KELVIN = 20000.0;

    private ThermalRegulatorMath() { }

    public record Policy(double normalBodyTemperatureKelvin, double metabolismHeatJoulesPerSecond,
                         double radiatedHeatJoulesPerSecond, double implicitHeatRegulationJoulesPerSecond,
                         double sweatHeatRegulationJoulesPerSecond, double shiveringHeatRegulationJoulesPerSecond,
                         double thermalRegulationThresholdKelvin) {
        public Policy {
            if (!Double.isFinite(normalBodyTemperatureKelvin) || normalBodyTemperatureKelvin <= MIN_BODY_KELVIN
                    || normalBodyTemperatureKelvin >= MAX_BODY_KELVIN) throw new IllegalArgumentException("invalid setpoint");
            for (double value : new double[]{metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond,
                    implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond,
                    shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin}) {
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("invalid regulation amount");
            }
        }
    }

    /** Implicit regulation is calculated before metabolism/radiation, active regulation afterward. */
    public static double regulate(double kelvin, double heatCapacity, Policy policy, double seconds,
                                  boolean canSweat, boolean canShiver) {
        if (policy == null || !Double.isFinite(kelvin) || kelvin < MIN_BODY_KELVIN || kelvin > MAX_BODY_KELVIN
                || !Double.isFinite(heatCapacity) || heatCapacity <= 0
                || !Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("invalid body state");
        double target = (policy.normalBodyTemperatureKelvin() - kelvin) * heatCapacity;
        double implicit = Math.copySign(Math.min(Math.abs(target), policy.implicitHeatRegulationJoulesPerSecond() * seconds), target);
        kelvin = bound(kelvin + (implicit + (policy.metabolismHeatJoulesPerSecond()
                - policy.radiatedHeatJoulesPerSecond()) * seconds) / heatCapacity);
        target = (policy.normalBodyTemperatureKelvin() - kelvin) * heatCapacity;
        if (Math.abs(target) / heatCapacity >= policy.thermalRegulationThresholdKelvin()) {
            double limit = target < 0 ? (canSweat ? policy.sweatHeatRegulationJoulesPerSecond() : 0)
                    : (canShiver ? policy.shiveringHeatRegulationJoulesPerSecond() : 0);
            kelvin = bound(kelvin + Math.copySign(Math.min(Math.abs(target), limit * seconds), target) / heatCapacity);
        }
        return kelvin;
    }

    private static double bound(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite body temperature");
        return Math.max(MIN_BODY_KELVIN, Math.min(MAX_BODY_KELVIN, value));
    }
}
