package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable ideal-gas mixture. Amounts are moles, temperature is kelvin, and energy is joules. */
public final class GasMixture {
    private static final float GAS_CONSTANT = 8.3144626f;
    public static final double MIN_TEMPERATURE_KELVIN = 2.7;
    public static final double MAX_TEMPERATURE_KELVIN = 262144.0;
    public static final double GAS_MIN_MOLES = 5e-8;

    private final float[] speciesMoles;
    private final float temperatureKelvin;
    private final float volumeCubicMeters;

    public GasMixture(Map<GasType, Double> moles, double temperatureKelvin) {
        this(moles, temperatureKelvin, AtmosphereUnits.minecraftCellVolumeCubicMeters());
    }

    public GasMixture(Map<GasType, Double> moles, double temperatureKelvin, double volumeCubicMeters) {
        this.speciesMoles = snapshot(moles);
        this.temperatureKelvin = (float) clampTemperature(temperatureKelvin);
        this.volumeCubicMeters = (float) nonNegativeFinite(volumeCubicMeters, "volumeCubicMeters");
        if (!Double.isFinite(totalMoles()) || !Double.isFinite(heatCapacity())
                || !Double.isFinite(thermalEnergy())) {
            throw new IllegalArgumentException("Mixture aggregate values must be finite");
        }
    }

    private static double clampTemperature(double temperature) {
        if (!Double.isFinite(temperature)) throw new IllegalArgumentException("temperatureKelvin must be finite");
        return Math.max(MIN_TEMPERATURE_KELVIN, Math.min(MAX_TEMPERATURE_KELVIN, temperature));
    }

    private static float[] snapshot(Map<GasType, Double> values) {
        Objects.requireNonNull(values, "moles");
        float[] copy = new float[GasType.values().length];
        for (Map.Entry<GasType, Double> entry : values.entrySet()) {
            GasType type = Objects.requireNonNull(entry.getKey(), "gas type");
            double amount = nonNegativeFinite(Objects.requireNonNull(entry.getValue(), "gas moles"), "gas moles");
            float rounded = (float) amount;
            if (!Float.isFinite(rounded)) throw new IllegalArgumentException("gas moles exceed float range");
            copy[type.ordinal()] = rounded;
        }
        return copy;
    }

    private static double nonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
        return value;
    }

    public static GasMixture vacuum() {
        return new GasMixture(Map.of(), MIN_TEMPERATURE_KELVIN);
    }

    public static GasMixture breathableAir() {
        // Ideal-gas amounts at 1 m3, 293.15 K and 101.325 kPa.
        double total = 101325.0 / (GAS_CONSTANT * 293.15);
        return new GasMixture(Map.of(GasType.OXYGEN, total * 0.21, GasType.NITROGEN, total * 0.79), 293.15);
    }

    public double temperatureKelvin() {
        return temperatureKelvin;
    }

    public double volumeCubicMeters() { return volumeCubicMeters; }

    public double moles(GasType type) {
        return speciesMoles[Objects.requireNonNull(type, "type").ordinal()];
    }

    public Map<GasType, Double> gasMoles() {
        EnumMap<GasType, Double> result = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) if (speciesMoles[type.ordinal()] > 0)
            result.put(type, (double) speciesMoles[type.ordinal()]);
        return Collections.unmodifiableMap(result);
    }

    public double totalMoles() {
        float total = 0;
        for (float amount : speciesMoles) total += amount;
        return total;
    }

    public double heatCapacity() {
        float capacity = 0;
        for (GasType type : GasType.values()) capacity += speciesMoles[type.ordinal()] * (float) type.molarHeatCapacity();
        return capacity;
    }

    public double thermalEnergy() {
        return (float) heatCapacity() * temperatureKelvin;
    }

    /** Returns a detached mixture with every species scaled by the same fraction. */
    public GasMixture withScaledMoles(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be finite and between zero and one");
        }
        EnumMap<GasType, Double> scaled = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) if (moles(type) > 0) scaled.put(type, (double) ((float) moles(type) * (float) fraction));
        return new GasMixture(scaled, temperatureKelvin, volumeCubicMeters);
    }

    /** Upstream default-scaled capacity, including its 0.0003 J/K floor for ordinary mixtures. */
    public double normalizedHeatCapacity() {
        return Math.max((float) heatCapacity() / (float) AtmosphereUnits.UPSTREAM_HEAT_SCALE_DEFAULT, 0.0003f);
    }

    /** Effective raw-unit equivalent of upstream's normalized capacity, including the floor. */
    public double effectiveHeatCapacity() {
        return (float) normalizedHeatCapacity() * (float) AtmosphereUnits.UPSTREAM_HEAT_SCALE_DEFAULT;
    }

    /** Thermal energy used by default-scaled SS14 simulation kernels. {@link #thermalEnergy()} stays physical raw Cp × K. */
    public double simulationThermalEnergy() {
        return (float) normalizedHeatCapacity() * temperatureKelvin;
    }

    /** Upstream immutable-space normalized Cp translated to this 1,000 L cell. */
    public static double spaceNormalizedHeatCapacity() {
        return AtmosphereUnits.referenceTileQuantity(7000.0);
    }

    public record Removal(GasMixture remaining, GasMixture removed) { }

    /** SS14 RemoveRatio semantics, cleaning tiny species in both source remainder and packet. */
    public Removal splitRatio(double ratio) {
        if (!Double.isFinite(ratio)) throw new IllegalArgumentException("ratio must be finite");
        if (ratio <= 0) return new Removal(this, new GasMixture(Map.of(), temperatureKelvin, volumeCubicMeters));
        ratio = Math.min(1, ratio);
        EnumMap<GasType, Double> removed = new EnumMap<>(GasType.class);
        EnumMap<GasType, Double> remaining = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            float original = (float) moles(type);
            float packet = original * (float) ratio;
            float residual = original - packet;
            if (packet >= GAS_MIN_MOLES) removed.put(type, (double) packet);
            if (residual >= GAS_MIN_MOLES) remaining.put(type, (double) residual);
        }
        return new Removal(new GasMixture(remaining, temperatureKelvin, volumeCubicMeters),
                new GasMixture(removed, temperatureKelvin, volumeCubicMeters));
    }

    /** Compatibility convenience returning only the packet; use splitRatio when source cleanup matters. */
    public GasMixture removeRatio(double ratio) { return splitRatio(ratio).removed(); }

    /** SS14 Merge temperature and heat-capacity gates; composition is summed after temperature selection. */
    public GasMixture mergedWith(GasMixture giver) {
        Objects.requireNonNull(giver, "giver");
        return mergedWith(giver, giver.normalizedHeatCapacity());
    }

    /**
     * Merge using an adapter-supplied giver capacity, for example immutable space's virtual heat
     * capacity. The supplied capacity affects temperature weighting only; actual species remain
     * unchanged and the virtual capacity is not materialized in the resulting mixture.
     */
    public GasMixture mergedWith(GasMixture giver, double giverNormalizedCapacity) {
        Objects.requireNonNull(giver, "giver");
        if (!Double.isFinite(giverNormalizedCapacity) || giverNormalizedCapacity < 0.0003) {
            throw new IllegalArgumentException("giverNormalizedCapacity must be finite and at least the upstream capacity floor");
        }
        // Upstream capacity is raw molar Cp divided by default AtmosHeatScale (8).
        float receiverCapacity = (float) normalizedHeatCapacity();
        float giverCapacity = (float) giverNormalizedCapacity;
        float combined = receiverCapacity + giverCapacity;
        double temperature = temperatureKelvin;
        if (Math.abs(temperatureKelvin - giver.temperatureKelvin) > 0.01 && combined > 0.0003f)
            temperature = (float) (((float) temperatureKelvin * receiverCapacity
                    + (float) giver.temperatureKelvin * giverCapacity) / combined);
        EnumMap<GasType, Double> result = new EnumMap<>(GasType.class);
        for (GasType gas : GasType.values()) {
            float sum = (float) moles(gas) + (float) giver.moles(gas);
            if (sum > 0) result.put(gas, (double) sum);
        }
        return new GasMixture(result, temperature, volumeCubicMeters);
    }

    /** Pressure calculated from this mixture's stored physical volume. */
    public double pressureKpa() { return pressureKpa(volumeCubicMeters); }

    /** Pressure using a caller-supplied physical volume (boundary/adaptor API). */
    public double pressureKpa(double volumeCubicMeters) {
        nonNegativeFinite(volumeCubicMeters, "volumeCubicMeters");
        return volumeCubicMeters == 0 ? 0 : (float) ((float) totalMoles() * GAS_CONSTANT * temperatureKelvin / (float) volumeCubicMeters / 1000f);
    }

    /** Ideal-gas packet size for a pressure delta in kPa and a volume in m³. */
    static double molesForPressureDelta(double pressureDeltaKpa, double volumeCubicMeters, double temperatureKelvin) {
        return (float) pressureDeltaKpa * (float) volumeCubicMeters * 1000f
                / ((float) temperatureKelvin * GAS_CONSTANT);
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
        float specifiedGasTemperature = (float) clampTemperature(gasTemperatureKelvin);
        float newAmount = (float) moles(type) + (float) deltaMoles;
        if (newAmount < 0.0) {
            throw new IllegalArgumentException("Gas delta would make inventory negative");
        }
        if (!Double.isFinite(newAmount)) {
            throw new IllegalArgumentException("Gas delta would result in non-finite inventory");
        }
        if (deltaMoles < 0.0 && specifiedGasTemperature != temperatureKelvin) {
            throw new IllegalArgumentException("Removed gas must be at the mixture's current temperature");
        }
        float energy = (float) thermalEnergy() + (float) deltaMoles * (float) type.molarHeatCapacity() * specifiedGasTemperature;
        if (energy < 0.0 && energy > -1e-10) {
            energy = 0.0f;
        }
        if (!Double.isFinite(energy) || energy < 0.0) {
            throw new IllegalArgumentException("Gas change would result in invalid thermal energy");
        }
        EnumMap<GasType, Double> changed = new EnumMap<>(GasType.class);
        changed.putAll(gasMoles());
        if (newAmount == 0.0f) changed.remove(type); else changed.put(type, (double) newAmount);
        float capacity = 0;
        for (GasType gas : GasType.values()) capacity += changed.getOrDefault(gas, 0.0).floatValue()
                * (float) gas.molarHeatCapacity();
        if (capacity == 0.0) {
            if (energy != 0.0) {
                throw new IllegalArgumentException("Gas change would leave thermal energy without heat capacity");
            }
            return new GasMixture(changed, temperatureKelvin, volumeCubicMeters);
        }
        if (!Double.isFinite(capacity)) {
            throw new IllegalArgumentException("Gas change would result in non-finite heat capacity");
        }
        return new GasMixture(changed, energy / capacity, volumeCubicMeters);
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
        return new GasMixture(gasMoles(), (float) energyJoules / (float) capacity, volumeCubicMeters);
    }

    public GasMixture withEnergyDelta(double deltaJoules) {
        if (!Double.isFinite(deltaJoules)) {
            throw new IllegalArgumentException("deltaJoules must be finite");
        }
        float energy = (float) thermalEnergy() + (float) deltaJoules;
        if (energy < 0.0) {
            throw new IllegalArgumentException("Energy delta would make thermal energy negative");
        }
        return withThermalEnergy(energy);
    }
}

