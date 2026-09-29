package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable outcome of exchanging one breath with a finite atmosphere cell. */
public record BreathExchange(GasMixture inhaled, GasMixture roomAfter) {
    public BreathExchange {
        Objects.requireNonNull(inhaled, "inhaled");
        Objects.requireNonNull(roomAfter, "roomAfter");
    }

    /**
     * Computes a closed exchange without touching world state. Inhalation is a proportional
     * sample of the available room gas, limited by both the request and room inventory; all
     * returned exhaled gas is added back to the room at its existing thermal energy.
     */
    public static Optional<BreathExchange> calculate(GasMixture room, double requestedMoles,
                                                      GasMixture exhaled) {
        if (room == null || exhaled == null || !Double.isFinite(requestedMoles) || requestedMoles < 0.0)
            return Optional.empty();
        try {
            if (requestedMoles == 0.0 && exhaled.totalMoles() == 0.0)
                return Optional.of(new BreathExchange(new GasMixture(Map.of(), room.temperatureKelvin()), room));
            double available = room.totalMoles();
            double inhaledMoles = Math.min(requestedMoles, available);
            GasMixture inhaled = available == 0.0
                    ? new GasMixture(Map.of(), room.temperatureKelvin())
                    : room.withScaledMoles(inhaledMoles / available);
            GasMixture remainder = available == 0.0
                    ? room
                    : room.withScaledMoles((available - inhaledMoles) / available);

            EnumMap<GasType, Double> combined = new EnumMap<>(GasType.class);
            remainder.gasMoles().forEach(combined::put);
            exhaled.gasMoles().forEach((type, amount) -> combined.merge(type, amount, Double::sum));
            double energy = remainder.thermalEnergy() + exhaled.thermalEnergy();
            double capacity = combined.entrySet().stream()
                    .mapToDouble(entry -> entry.getValue() * entry.getKey().molarHeatCapacity()).sum();
            double temperature = capacity == 0.0 ? remainder.temperatureKelvin() : energy / capacity;
            GasMixture after = new GasMixture(combined, temperature);
            if (!Double.isFinite(energy) || !Double.isFinite(capacity) || !Double.isFinite(temperature))
                return Optional.empty();
            return Optional.of(new BreathExchange(inhaled, after));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
    }
}
