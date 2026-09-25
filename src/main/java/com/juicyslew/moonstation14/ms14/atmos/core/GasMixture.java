package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable ideal-gas mixture. Amounts are moles, temperature is kelvin, and energy is joules. */
public final class GasMixture {
    private static final double GAS_CONSTANT = 8.31446261815324;
    private static final double VACUUM_TEMPERATURE_KELVIN = 2.7;

    private final Map<GasType, Double> moles;
    private final double temperatureKelvin;

    public GasMixture(Map<GasType, Double> moles, double temperatureKelvin) {
        this.moles = snapshot(moles);
        this.temperatureKelvin = nonNegativeFinite(temperatureKelvin, "temperatureKelvin");
        if (!Double.isFinite(totalMoles()) || !Double.isFinite(heatCapacity())
                || !Double.isFinite(thermalEnergy())) {
            throw new IllegalArgumentException("Mixture aggregate values must be finite");
        }
    }

    private static Map<GasType, Double> snapshot(Map<GasType, Double> values) {
        Objects.requireNonNull(values, "moles");
        EnumMap<GasType, Double> copy = new EnumMap<>(GasType.class);
        for (Map.Entry<GasType, Double> entry : values.entrySet()) {
            GasType type = Objects.requireNonNull(entry.getKey(), "gas type");
            double amount = nonNegativeFinite(Objects.requireNonNull(entry.getValue(), "gas moles"), "gas moles");
            if (amount > 0.0) {
                copy.put(type, amount);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static double nonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
        return value;
    }

    public static GasMixture vacuum() {
        return new GasMixture(Map.of(), VACUUM_TEMPERATURE_KELVIN);
    }

    public static GasMixture breathableAir() {
        // Ideal-gas amounts at 1 m3, 293.15 K and 101.325 kPa.
        double total = 101325.0 / (GAS_CONSTANT * 293.15);
        return new GasMixture(Map.of(GasType.OXYGEN, total * 0.21, GasType.NITROGEN, total * 0.79), 293.15);
    }

    public double temperatureKelvin() {
        return temperatureKelvin;
    }

    public double moles(GasType type) {
        return moles.getOrDefault(Objects.requireNonNull(type, "type"), 0.0);
    }

    public Map<GasType, Double> gasMoles() {
        return moles;
    }

    public double totalMoles() {
        return moles.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double heatCapacity() {
        return moles.entrySet().stream().mapToDouble(entry -> entry.getValue() * entry.getKey().molarHeatCapacity()).sum();
    }

    public double thermalEnergy() {
        return heatCapacity() * temperatureKelvin;
    }

    /** Returns a detached mixture with every species scaled by the same fraction. */
    public GasMixture withScaledMoles(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be finite and between zero and one");
        }
        EnumMap<GasType, Double> scaled = new EnumMap<>(GasType.class);
        for (Map.Entry<GasType, Double> entry : moles.entrySet()) {
            double amount = entry.getValue() * fraction;
            if (amount > 0.0) scaled.put(entry.getKey(), amount);
        }
        return new GasMixture(scaled, temperatureKelvin);
    }

    public double pressureKpa(double volumeCubicMeters) {
        nonNegativeFinite(volumeCubicMeters, "volumeCubicMeters");
        if (volumeCubicMeters == 0.0) {
            throw new IllegalArgumentException("volumeCubicMeters must be greater than zero");
        }
        return totalMoles() * GAS_CONSTANT * temperatureKelvin / volumeCubicMeters / 1000.0;
    }

    /** Changes composition while adding/removing gas at the mixture's current temperature. */
    public GasMixture withGasDelta(GasType type, double deltaMoles) {
        return withGasDelta(type, deltaMoles, temperatureKelvin);
    }

    /**
     * Changes gas amount while carrying the added gas at the specified temperature. Removed gas
     * always carries energy at the mixture's current temperature, so a removal must specify that
     * same temperature. The resulting temperature follows energy conservation.
     */
    public GasMixture withGasDelta(GasType type, double deltaMoles, double gasTemperatureKelvin) {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(deltaMoles)) {
            throw new IllegalArgumentException("deltaMoles must be finite");
        }
        nonNegativeFinite(gasTemperatureKelvin, "gasTemperatureKelvin");
        double newAmount = moles(type) + deltaMoles;
        if (newAmount < 0.0) {
            throw new IllegalArgumentException("Gas delta would make inventory negative");
        }
        if (!Double.isFinite(newAmount)) {
            throw new IllegalArgumentException("Gas delta would result in non-finite inventory");
        }
        if (deltaMoles < 0.0 && gasTemperatureKelvin != temperatureKelvin) {
            throw new IllegalArgumentException("Removed gas must be at the mixture's current temperature");
        }
        double energy = thermalEnergy() + deltaMoles * type.molarHeatCapacity() * gasTemperatureKelvin;
        if (energy < 0.0 && energy > -1e-10) {
            energy = 0.0;
        }
        if (!Double.isFinite(energy) || energy < 0.0) {
            throw new IllegalArgumentException("Gas change would result in invalid thermal energy");
        }
        EnumMap<GasType, Double> changed = new EnumMap<>(GasType.class);
        changed.putAll(moles);
        if (newAmount == 0.0) changed.remove(type); else changed.put(type, newAmount);
        double capacity = changed.entrySet().stream().mapToDouble(entry -> entry.getValue() * entry.getKey().molarHeatCapacity()).sum();
        if (capacity == 0.0) {
            if (energy != 0.0) {
                throw new IllegalArgumentException("Gas change would leave thermal energy without heat capacity");
            }
            return new GasMixture(changed, temperatureKelvin);
        }
        if (!Double.isFinite(capacity)) {
            throw new IllegalArgumentException("Gas change would result in non-finite heat capacity");
        }
        return new GasMixture(changed, energy / capacity);
    }

    public GasMixture withThermalEnergy(double energyJoules) {
        nonNegativeFinite(energyJoules, "energyJoules");
        double capacity = heatCapacity();
        if (capacity == 0.0) {
            if (energyJoules != 0.0) {
                throw new IllegalStateException("Cannot assign thermal energy to a mixture with zero heat capacity");
            }
            return this;
        }
        return new GasMixture(moles, energyJoules / capacity);
    }

    public GasMixture withEnergyDelta(double deltaJoules) {
        if (!Double.isFinite(deltaJoules)) {
            throw new IllegalArgumentException("deltaJoules must be finite");
        }
        double energy = thermalEnergy() + deltaJoules;
        if (energy < 0.0) {
            throw new IllegalArgumentException("Energy delta would make thermal energy negative");
        }
        return withThermalEnergy(energy);
    }
}
