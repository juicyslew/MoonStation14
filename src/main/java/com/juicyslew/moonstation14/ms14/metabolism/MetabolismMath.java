package com.juicyslew.moonstation14.ms14.metabolism;

import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure arithmetic for a single ordinary metabolism attempt. */
public final class MetabolismMath {
    private MetabolismMath() {
    }

    public static <T> MetabolismResult<T> metabolize(float currentAvailable, float configuredRate,
                                                     Map<T, Float> ratios) {
        requireFiniteNonnegative(currentAvailable, "currentAvailable");
        requireFinitePositive(configuredRate, "configuredRate");
        Objects.requireNonNull(ratios, "ratios");

        float actualRemoved = Math.min(currentAvailable, configuredRate);
        float scale = actualRemoved / configuredRate;
        Map<T, Float> production = new LinkedHashMap<>();
        for (Map.Entry<T, Float> entry : ratios.entrySet()) {
            T key = Objects.requireNonNull(entry.getKey(), "ratio key");
            float ratio = requireFiniteNonnegative(entry.getValue(), "ratio for " + key);
            float produced = actualRemoved * ratio;
            if (!Float.isFinite(produced)) {
                throw new IllegalArgumentException("production for " + key + " is not finite");
            }
            production.put(key, produced);
        }
        return new MetabolismResult<>(actualRemoved, scale, production);
    }

    public static <T> MetabolismResult<T> calculate(float currentAvailable, float configuredRate,
                                                    Map<T, Float> ratios) {
        return metabolize(currentAvailable, configuredRate, ratios);
    }

    /** Cent-authoritative ordinary metabolism plan. Rate and ratios are external float-shaped prototype values. */
    public static <T> CentMetabolismPlan<T> metabolizeUnits(long availableUnits, float configuredRate,
                                                             Map<T, Float> ratios) {
        ReagentUnits.validateCents(availableUnits, "availableUnits");
        if (!Float.isFinite(configuredRate) || configuredRate <= 0f)
            throw new IllegalArgumentException("configuredRate must be finite and strictly positive");
        long rateUnits = ReagentUnits.fromFloat(configuredRate);
        if (rateUnits == 0)
            throw new IllegalArgumentException("configuredRate is positive but below the minimum 0.01-unit metabolism rate");
        Objects.requireNonNull(ratios, "ratios");
        long removed = Math.min(availableUnits, rateUnits);
        float scale = (float) removed / rateUnits;
        Map<T, Long> production = new LinkedHashMap<>();
        for (var entry : ratios.entrySet()) {
            T key = Objects.requireNonNull(entry.getKey(), "ratio key");
            float ratio = requireFiniteNonnegative(entry.getValue(), "ratio for " + key);
            double product = Math.floor((double) removed * (double) ratio);
            if (!Double.isFinite(product) || product > ReagentUnits.MAX_CENTS)
                throw new IllegalArgumentException("production for " + key + " exceeds maximum reagent quantity");
            production.put(key, (long) product);
        }
        return new CentMetabolismPlan<>(removed, rateUnits, scale, production);
    }

    public record CentMetabolismPlan<T>(long actualRemovedUnits, long rateUnits, float scale,
                                         Map<T, Long> requestedProductionUnits) {
        public CentMetabolismPlan {
            ReagentUnits.validateCents(actualRemovedUnits, "actualRemovedUnits");
            ReagentUnits.validateCents(rateUnits, "rateUnits");
            if (!Float.isFinite(scale) || scale < 0f || scale > 1f) throw new IllegalArgumentException("scale must be in [0, 1]");
            Objects.requireNonNull(requestedProductionUnits, "requestedProductionUnits");
            requestedProductionUnits.forEach((key, value) -> {
                Objects.requireNonNull(key, "production key");
                ReagentUnits.validateCents(Objects.requireNonNull(value, "production cents"), "production cents");
            });
            requestedProductionUnits = Map.copyOf(requestedProductionUnits);
        }

        /** Legacy callback adapter; ordinary sources are bounded below 1000 units. */
        public <TKey> MetabolismResult<TKey> toFloatResult(Map<TKey, Long> productionUnits) {
            Map<TKey, Float> production = new LinkedHashMap<>();
            productionUnits.forEach((key, value) -> production.put(key, ReagentUnits.toFloat(value)));
            return new MetabolismResult<>(ReagentUnits.toFloat(actualRemovedUnits), scale, production);
        }
    }

    private static float requireFiniteNonnegative(Float value, String name) {
        if (value == null || !Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
        return value;
    }

    private static float requireFinitePositive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and strictly positive");
        }
        return value;
    }
}
