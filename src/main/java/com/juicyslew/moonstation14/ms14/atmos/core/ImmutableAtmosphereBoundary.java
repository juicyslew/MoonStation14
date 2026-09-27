package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Exchanges a mutable one-cubic-meter cell with an immutable neighboring atmosphere cell. */
public final class ImmutableAtmosphereBoundary {
    private static final double SPACE_TEMPERATURE_KELVIN = 2.7;
    private static final double SPACE_HEAT_CAPACITY = 7000.0;
    private static final double OPEN_HEAT_TRANSFER_COEFFICIENT = 0.4;
    private static final double SPACE_EMPTY_EPSILON_MOLES = 1.0e-6;
    private static final double SPACE_MINIMUM_PRESSURE_DELTA_KPA = 10.0;
    private static final double SPACE_PRESSURE_RELAXATION = 0.5;

    private ImmutableAtmosphereBoundary() {}

    /**
     * Result of a boundary exchange. Positive exported values leave the finite cell; negative
     * values mean the finite cell was replenished by its immutable exterior.
     */
    public record Result(GasMixture finiteAfter, Map<GasType, Double> perGasExported,
                         double energyExportedJoules) {
        public Result {
            Objects.requireNonNull(finiteAfter, "finiteAfter");
            Objects.requireNonNull(perGasExported, "perGasExported");
            EnumMap<GasType, Double> copy = new EnumMap<>(GasType.class);
            for (Map.Entry<GasType, Double> entry : perGasExported.entrySet()) {
                GasType type = Objects.requireNonNull(entry.getKey(), "gas type");
                double amount = Objects.requireNonNull(entry.getValue(), "exported moles");
                if (!Double.isFinite(amount)) {
                    throw new IllegalArgumentException("exported moles must be finite");
                }
                if (amount != 0.0) copy.put(type, amount);
            }
            perGasExported = Collections.unmodifiableMap(copy);
            if (!Double.isFinite(energyExportedJoules)) {
                throw new IllegalArgumentException("energyExportedJoules must be finite");
            }
        }
    }

    /**
     * Exchanges gas and heat with an exterior snapshot. Only the finite cell is mutable and its
     * changes are reported as a signed export ledger.
     */
    public static Result exchange(GasMixture finite, GasMixture exterior, double fraction,
                                  boolean exteriorIsSpace) {
        Objects.requireNonNull(finite, "finite");
        Objects.requireNonNull(exterior, "exterior");
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 0.5) {
            throw new IllegalArgumentException("fraction must be finite and in [0, 0.5]");
        }

        GasMixture finiteAfter;
        if (exteriorIsSpace) {
            double pressureDifferenceKpa = finite.pressureKpa(1.0) - exterior.pressureKpa(1.0);
            double totalMoles = finite.totalMoles();
            if (totalMoles > 0.0 && totalMoles <= SPACE_EMPTY_EPSILON_MOLES) {
                finiteAfter = GasMixture.vacuum();
            } else if (pressureDifferenceKpa > SPACE_MINIMUM_PRESSURE_DELTA_KPA && totalMoles > 0.0) {
                // A one-cubic-meter cell at its source temperature: remove enough mixture to
                // relax half of the pressure difference, without altering its composition or T.
                double removableMoles = pressureDifferenceKpa * 500.0
                        / (8.31446261815324 * finite.temperatureKelvin());
                double removedMoles = Math.min(totalMoles, removableMoles);
                finiteAfter = finite.withScaledMoles((totalMoles - removedMoles) / totalMoles);
            } else if (totalMoles > 0.0) {
                // Low-pressure fallback exchange drains gradually rather than flashing trace
                // inventories to zero; repeated passes converge to the explicit empty epsilon.
                finiteAfter = finite.withScaledMoles(0.5);
            } else {
                finiteAfter = finite;
            }
        } else {
            // An immutable non-vacuum exterior acts as a reservoir; bounded pair relaxation
            // converges without changing the reservoir or creating gas beyond its composition.
            finiteAfter = AtmosphereTransfer.step(finite, exterior, Math.max(fraction, SPACE_PRESSURE_RELAXATION)).first();
        }
        if (exteriorIsSpace) {
            double capacity = finiteAfter.heatCapacity();
            if (capacity > 0.0) {
                double heatToSpace = OPEN_HEAT_TRANSFER_COEFFICIENT
                        * (finiteAfter.temperatureKelvin() - SPACE_TEMPERATURE_KELVIN)
                        * capacity * SPACE_HEAT_CAPACITY / (capacity + SPACE_HEAT_CAPACITY);
                double energy = finiteAfter.thermalEnergy() - heatToSpace;
                // Rounding at extreme values must not make the finite cell's energy negative.
                finiteAfter = finiteAfter.withThermalEnergy(Math.max(0.0, energy));
            }
        }

        EnumMap<GasType, Double> exported = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            double delta = finite.moles(type) - finiteAfter.moles(type);
            if (delta != 0.0) exported.put(type, delta);
        }
        return new Result(finiteAfter, exported, finite.thermalEnergy() - finiteAfter.thermalEnergy());
    }
}
