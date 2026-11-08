package com.juicyslew.moonstation14.ms14.atmos.teg;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import java.util.Objects;

/** Pure thermodynamic step for the SS14 thermo-electric generator. */
public final class TegThermodynamics {
    public static final double DEFAULT_EFFICIENCY = 0.65;
    public static final double DEFAULT_POWER_FACTOR = 1.0;

    private TegThermodynamics() { }

    /**
     * Applies upstream's heat-capacity equalization bound, Carnot-limited configured efficiency,
     * and low-delta-temperature reduction. Species and volumes are preserved independently.
     * The returned gas-energy changes report the actual float-backed changes in the resulting
     * mixtures; transfer and generated energy remain SS14's requested/theoretical quantities.
     */
    public static Result generate(GasMixture first, GasMixture second,
                                  double configuredEfficiency, double powerFactor) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!Double.isFinite(configuredEfficiency) || configuredEfficiency < 0 || configuredEfficiency > 1)
            throw new IllegalArgumentException("configuredEfficiency must be between zero and one");
        if (!Double.isFinite(powerFactor) || powerFactor < 0)
            throw new IllegalArgumentException("powerFactor must be finite and nonnegative");

        double cpA = first.heatCapacity();
        double cpB = second.heatCapacity();
        double ta = first.temperatureKelvin();
        double tb = second.temperatureKelvin();
        // SS14 gates processing on both mixtures having pressure; empty/zero-Cp gas cannot exchange heat.
        if (first.pressureKpa() <= 0 || second.pressureKpa() <= 0 || cpA <= 0 || cpB <= 0) {
            return new Result(first, second, 0, 0, 0, 0, 0, 0);
        }

        boolean hotA = ta > tb;
        double hotTemperature = hotA ? ta : tb;
        double coldTemperature = hotA ? tb : ta;
        double finalTemperature = (ta * cpA + tb * cpB) / (cpA + cpB);
        // Deliberately mirrors upstream's cA multiplier even when stream A is the cold stream.
        double maximumWork = Math.abs(ta - finalTemperature) * cpA;
        double carnot = 1.0 - coldTemperature / hotTemperature;
        double effectiveEfficiency = Math.min(configuredEfficiency, carnot);
        effectiveEfficiency *= Math.tanh((hotTemperature - coldTemperature) / 700.0);
        double transfer = maximumWork * effectiveEfficiency;
        double hotRemoved = transfer;
        double coldAdded = transfer * (1.0 - configuredEfficiency);
        double beforeA = first.thermalEnergy();
        double beforeB = second.thermalEnergy();
        double requestedA = hotA ? beforeA - hotRemoved : beforeA + coldAdded;
        double requestedB = hotA ? beforeB + coldAdded : beforeB - hotRemoved;
        GasMixture resultA = withRepresentableEnergy(first, requestedA);
        GasMixture resultB = withRepresentableEnergy(second, requestedB);
        double actualHotRemoved = hotA ? beforeA - resultA.thermalEnergy() : beforeB - resultB.thermalEnergy();
        double actualColdAdded = hotA ? resultB.thermalEnergy() - beforeB : resultA.thermalEnergy() - beforeA;
        return new Result(resultA, resultB, actualHotRemoved, actualColdAdded, transfer,
                transfer * powerFactor, effectiveEfficiency, maximumWork);
    }

    private static GasMixture withRepresentableEnergy(GasMixture mixture, double energy) {
        double capacity = mixture.heatCapacity();
        double minEnergy = capacity * GasMixture.MIN_TEMPERATURE_KELVIN;
        double maxEnergy = capacity * GasMixture.MAX_TEMPERATURE_KELVIN;
        if (!Double.isFinite(energy) || energy < minEnergy || energy > maxEnergy)
            throw new IllegalArgumentException("TEG thermal step exceeds representable mixture energy");
        return mixture.withThermalEnergy(energy);
    }

    public record Result(GasMixture first, GasMixture second, double hotThermalEnergyRemovedJoules,
                         double coldThermalEnergyAddedJoules, double transferJoules,
                          double generatedEnergyJoules, double effectiveEfficiency,
                          double maximumWorkJoules) { }
}
