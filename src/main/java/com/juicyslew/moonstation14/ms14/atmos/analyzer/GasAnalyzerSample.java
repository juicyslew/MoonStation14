package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Detached, read-only projection of a finite-cell atmosphere observation. */
public record GasAnalyzerSample(double volumeLitres, double pressureKpa, double temperatureKelvin,
                                List<GasAmount> gases) {
    public GasAnalyzerSample {
        if (!Double.isFinite(volumeLitres) || volumeLitres < 0 || !Double.isFinite(pressureKpa) || pressureKpa < 0
                || !Double.isFinite(temperatureKelvin) || temperatureKelvin < 0 || gases == null || gases.size() > 9)
            throw new IllegalArgumentException("Invalid gas analyzer sample");
        gases = List.copyOf(gases);
        if (gases.stream().anyMatch(java.util.Objects::isNull)
                || gases.stream().map(GasAmount::gas).distinct().count() != gases.size())
            throw new IllegalArgumentException("Invalid analyzer gas list");
    }
    public record GasAmount(GasType gas, double moles, double percentage) {
        public GasAmount {
            if (gas == null || !Double.isFinite(moles) || moles <= 0 || !Double.isFinite(percentage) || percentage < 0 || percentage > 100.0001)
                throw new IllegalArgumentException("Invalid analyzer gas amount");
        }
    }

    public static GasAnalyzerSample from(AtmosphereReading reading, GasAnalyzerProfile profile) {
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(profile, "profile");
        GasMixture mix = reading.mixture();
        return fromMixture(mix, mix.volumeCubicMeters(), profile);
    }

    /** Projects a detached pipe node mixture using that node's authoritative finite volume. */
    public static GasAnalyzerSample fromMixture(GasMixture mix, double volumeCubicMeters, GasAnalyzerProfile profile) {
        Objects.requireNonNull(mix, "mixture");
        Objects.requireNonNull(profile, "profile");
        if (!Double.isFinite(volumeCubicMeters) || volumeCubicMeters <= 0)
            throw new IllegalArgumentException("Invalid analyzer node volume");
        double total = mix.totalMoles();
        double pressure = mix.pressureKpa(volumeCubicMeters);
        if (!Double.isFinite(total) || total < 0 || !Double.isFinite(mix.temperatureKelvin())
                || !Double.isFinite(pressure) || pressure < 0)
            throw new IllegalArgumentException("Non-finite analyzer observation");
        List<GasAmount> gases = mix.gasMoles().entrySet().stream()
                .filter(entry -> entry.getValue() > profile.minimumGasMoles())
                .map(entry -> new GasAmount(entry.getKey(), entry.getValue(), total > 0 ? entry.getValue() * 100 / total : 0))
                .sorted(Comparator.comparingDouble(GasAmount::moles).reversed().thenComparing(amount -> amount.gas().id()))
                .toList();
        return new GasAnalyzerSample(volumeCubicMeters * 1000, pressure, mix.temperatureKelvin(), gases);
    }
}
