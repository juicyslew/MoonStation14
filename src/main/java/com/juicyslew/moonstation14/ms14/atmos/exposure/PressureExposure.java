package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;

/** Pure typed reducer for Barotrauma; entity resolution and effects belong to BarotraumaSystem. */
public final class PressureExposure {
    private PressureExposure() { }

    /** Pure typed reducer. A non-finite physical pressure or invalid policy fails closed. */
    public static Map<String, Float> damageAt(double pressureKpa, BarotraumaComponent policy) {
        if (policy == null || !Double.isFinite(pressureKpa) || pressureKpa < 0
                 || !Double.isFinite(policy.maxDamage()) || policy.maxDamage() < 0) return Map.of();
        if (pressureKpa <= BarotraumaAtmospherePolicy.LOW_HAZARD_KPA)
            return typed(policy.damage().types(), BarotraumaAtmospherePolicy.LOW_DAMAGE_MULTIPLIER);
        if (pressureKpa < BarotraumaAtmospherePolicy.HIGH_HAZARD_KPA) return Map.of();
        // Limit before multiplication: even a finite pressure may overflow the ratio or product.
        Map<String, Double> high = policy.damage().types();
        double ratio = pressureKpa / BarotraumaAtmospherePolicy.HIGH_HAZARD_KPA - 1;
        double factor = Math.min(ratio * BarotraumaAtmospherePolicy.HIGH_DAMAGE_COEFFICIENT,
                BarotraumaAtmospherePolicy.HIGH_DAMAGE_SCALE_CAP);
        if (Double.isNaN(factor) || factor <= 0) return Map.of();
        return typed(high, factor);
    }

    /** Pre-check current matching damage; an allowed hit is not prorated when it crosses the ceiling. */
    public static Map<String, Float> limitMatchingDamage(Map<String, Float> damage, Map<String, Float> existing,
                                                          BarotraumaComponent policy) {
        if (damage == null || damage.isEmpty() || existing == null || policy == null
                || !Double.isFinite(policy.maxDamage()) || policy.maxDamage() < 0) return Map.of();
        var keys = new HashSet<>(policy.damage().types().keySet());
        double accumulated = 0;
        for (String key : keys) {
            Float value = existing.get(key);
            if (value != null && Float.isFinite(value) && value > 0) accumulated += value;
        }
        return accumulated >= policy.maxDamage() ? Map.of() : damage;
    }

    private static Map<String, Float> typed(Map<String, Double> source, double factor) {
        if (source == null || !Double.isFinite(factor) || factor < 0) return Map.of();
        Map<String, Float> result = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            Double value = entry.getValue();
            if (entry.getKey() == null || value == null || !Double.isFinite(value) || value < 0)
                return Map.of();
            double scaled = value * factor;
            if (!Double.isFinite(scaled) || scaled > Float.MAX_VALUE) return Map.of();
            if ((float) scaled > 0f) result.put(entry.getKey(), (float) scaled);
        }
        return Map.copyOf(result);
    }
}
