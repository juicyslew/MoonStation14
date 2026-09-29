package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Pure, fail-closed damage calculation for one actually inhaled mixture. */
public final class LungToxicity {
    private LungToxicity() {}

    public static Optional<Map<String, Float>> perInhale(GasMixture inhaled,
            Map<String, Map<String, Double>> rates, double cap) {
        if (inhaled == null || rates == null || !Double.isFinite(cap) || cap < 0) return Optional.empty();
        try {
            Map<String, Double> totals = new LinkedHashMap<>();
            double total = 0;
            for (var gasEntry : rates.entrySet()) {
                GasType gas = GasType.fromId(gasEntry.getKey());
                if (!gas.id().equals(gasEntry.getKey()) || gasEntry.getValue() == null) return Optional.empty();
                double moles = inhaled.moles(gas);
                if (!Double.isFinite(moles) || moles < 0) return Optional.empty();
                for (var damageEntry : gasEntry.getValue().entrySet()) {
                    DamageKeys.requireCanonical(damageEntry.getKey());
                    Double rate = damageEntry.getValue();
                    if (rate == null || !Double.isFinite(rate) || rate < 0) return Optional.empty();
                    double amount = rate * moles;
                    if (!Double.isFinite(amount)) return Optional.empty();
                    totals.merge(damageEntry.getKey(), amount, Double::sum);
                    total += amount;
                    if (!Double.isFinite(total) || !Double.isFinite(totals.get(damageEntry.getKey())))
                        return Optional.empty();
                }
            }
            double factor = total > cap ? cap / total : 1d;
            Map<String, Float> result = new LinkedHashMap<>();
            for (var entry : totals.entrySet()) {
                double scaled = entry.getValue() * factor;
                if (!Double.isFinite(scaled) || scaled > Float.MAX_VALUE) return Optional.empty();
                if (scaled > 0) {
                    float rounded = (float) scaled;
                    // Float conversion must never round the aggregate beyond the typed cap.
                    if (factor < 1d && rounded > scaled) rounded = Math.nextDown(rounded);
                    if (rounded > 0) result.put(entry.getKey(), rounded);
                }
            }
            return Optional.of(Map.copyOf(result));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
