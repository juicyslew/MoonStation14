package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Deterministic conservative exchange step between two equal, one-cubic-meter cells. */
public final class AtmosphereTransfer {
    private AtmosphereTransfer() {}

    public record Result(GasMixture first, GasMixture second) {}

    /**
     * Moves each species down its concentration gradient by the given fraction, carries source
     * enthalpy, then exchanges heat toward equal temperature by the same fraction.
     * Fraction is constrained to [0, 0.5] for a stable pairwise step.
     */
    public static Result step(GasMixture first, GasMixture second, double fraction) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 0.5) {
            throw new IllegalArgumentException("fraction must be finite and in [0, 0.5]");
        }
        EnumMap<GasType, Double> a = new EnumMap<>(GasType.class);
        EnumMap<GasType, Double> b = new EnumMap<>(GasType.class);
        a.putAll(first.gasMoles());
        b.putAll(second.gasMoles());
        double energyA = first.thermalEnergy();
        double energyB = second.thermalEnergy();
        for (GasType type : GasType.values()) {
            double difference = first.moles(type) - second.moles(type);
            double transfer = Math.abs(difference) * fraction;
            if (difference > 0.0) {
                a.put(type, first.moles(type) - transfer);
                b.put(type, second.moles(type) + transfer);
                double enthalpy = transfer * type.molarHeatCapacity() * first.temperatureKelvin();
                energyA -= enthalpy;
                energyB += enthalpy;
            } else if (difference < 0.0) {
                a.put(type, first.moles(type) + transfer);
                b.put(type, second.moles(type) - transfer);
                double enthalpy = transfer * type.molarHeatCapacity() * second.temperatureKelvin();
                energyA += enthalpy;
                energyB -= enthalpy;
            }
        }
        GasMixture movedA = new GasMixture(a, first.temperatureKelvin());
        GasMixture movedB = new GasMixture(b, second.temperatureKelvin());
        // Rebuild after carrying enthalpy; empty cells have no temperature-dependent energy.
        movedA = withEnergy(movedA, energyA, first.temperatureKelvin());
        movedB = withEnergy(movedB, energyB, second.temperatureKelvin());

        double capA = movedA.heatCapacity();
        double capB = movedB.heatCapacity();
        if (capA > 0.0 && capB > 0.0) {
            double heat = fraction * (movedA.temperatureKelvin() - movedB.temperatureKelvin()) * capA * capB / (capA + capB);
            energyA = movedA.thermalEnergy() - heat;
            energyB = movedB.thermalEnergy() + heat;
        } else {
            energyA = movedA.thermalEnergy();
            energyB = movedB.thermalEnergy();
        }
        return new Result(withEnergy(movedA, energyA, movedA.temperatureKelvin()),
                withEnergy(movedB, energyB, movedB.temperatureKelvin()));
    }

    private static GasMixture withEnergy(GasMixture mixture, double energy, double emptyTemperature) {
        if (mixture.heatCapacity() == 0.0) {
            return new GasMixture(Map.of(), emptyTemperature);
        }
        return mixture.withThermalEnergy(Math.max(0.0, energy));
    }
}
