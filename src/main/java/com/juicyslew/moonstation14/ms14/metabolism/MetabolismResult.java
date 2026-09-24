package com.juicyslew.moonstation14.ms14.metabolism;

import java.util.Map;

/** Immutable result of one ordinary, positive-rate metabolism calculation. */
public record MetabolismResult<T>(float actualRemoved, float scale, Map<T, Float> requestedProduction) {
    public MetabolismResult {
        if (!Float.isFinite(actualRemoved) || actualRemoved < 0f) {
            throw new IllegalArgumentException("actualRemoved must be finite and nonnegative");
        }
        if (!Float.isFinite(scale) || scale < 0f || scale > 1f) {
            throw new IllegalArgumentException("scale must be finite and in [0, 1]");
        }
        if (requestedProduction == null) throw new NullPointerException("requestedProduction");
        for (Map.Entry<T, Float> entry : requestedProduction.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !Float.isFinite(entry.getValue()) || entry.getValue() < 0f) {
                throw new IllegalArgumentException("requested production must be finite and nonnegative");
            }
        }
        requestedProduction = Map.copyOf(requestedProduction);
    }

    /** Alias emphasizing that this is production requested before capacity retention. */
    public Map<T, Float> produced() {
        return requestedProduction;
    }

    public Map<T, Float> production() {
        return requestedProduction;
    }

    public Map<T, Float> requested() {
        return requestedProduction;
    }
}
