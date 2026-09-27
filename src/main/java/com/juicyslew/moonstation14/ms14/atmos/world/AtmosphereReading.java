package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

import java.util.Objects;

/** Immutable presentation snapshot; provisional readings are not authoritative physical state. */
public record AtmosphereReading(GasMixture mixture, Status status) {
    public AtmosphereReading {
        mixture = Objects.requireNonNull(mixture, "mixture").withScaledMoles(1.0);
        status = Objects.requireNonNull(status, "status");
    }

    public enum Status {
        FINITE,
        EXTERIOR,
        PROVISIONAL
    }
}
