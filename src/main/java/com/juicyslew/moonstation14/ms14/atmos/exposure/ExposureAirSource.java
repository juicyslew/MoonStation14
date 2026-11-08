package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;

import java.util.Optional;
import java.util.function.Predicate;

/** Narrow exposure seam: samples carry their physical volume because gas mixtures do not. */
public interface ExposureAirSource {
    record Sample(GasMixture mixture, double volumeCubicMeters) {
        public Sample {
            if (mixture == null || !Double.isFinite(volumeCubicMeters) || volumeCubicMeters <= 0)
                throw new IllegalArgumentException("invalid exposure sample");
        }
        public double pressureKpa() { return mixture.pressureKpa(volumeCubicMeters); }
        public double temperatureKelvin() { return mixture.temperatureKelvin(); }
    }

    Optional<Sample> sample();

    /** Validator is called once before any source mutation; rejection leaves finite stores intact. */
    Optional<BreathExchange> exchange(double requestedMoles, GasMixture exhaled,
                                      Predicate<GasMixture> validateInhaled);

    /** Commits signed environmental heat (positive adds heat to the finite air source). */
    default boolean exchangeThermalEnergy(double joules) { return false; }
}

