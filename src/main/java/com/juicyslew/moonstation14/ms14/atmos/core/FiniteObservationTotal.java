package com.juicyslew.moonstation14.ms14.atmos.core;

/** Checked accumulator for observational-only totals; overflow saturates at the finite double boundary. */
public final class FiniteObservationTotal {
    private FiniteObservationTotal() { }

    public static double add(double total, double receipt) {
        if (!Double.isFinite(total) || !Double.isFinite(receipt)) return total;
        if (receipt > 0 && total > Double.MAX_VALUE - receipt) return Double.MAX_VALUE;
        if (receipt < 0 && total < -Double.MAX_VALUE - receipt) return -Double.MAX_VALUE;
        return total + receipt;
    }
}
