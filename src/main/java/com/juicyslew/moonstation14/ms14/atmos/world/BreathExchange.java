package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

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
            double ratio = available == 0.0 ? 0.0 : Math.min(1.0, requestedMoles / available);
            GasMixture.Removal split = room.splitRatio(ratio);
            GasMixture inhaled = split.removed();
            GasMixture remainder = split.remaining();
            // An empty former lung is not merged: upstream breathing has no exhalation on
            // its initial fresh-lung pass, and merging an empty giver changes thermal gates.
            GasMixture after = exhaled.totalMoles() == 0.0 ? remainder : remainder.mergedWith(exhaled);
            return Optional.of(new BreathExchange(inhaled, after));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
    }
}
