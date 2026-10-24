package com.juicyslew.moonstation14.ms14.singularity;

/** SS14 SinguloFood composition: reserveAfter = reserveBefore * factor + flat. */
public final class SingularityFoodMath {
    private SingularityFoodMath() { }
    public static double after(double reserve, double flat, double factor) {
        if (!Double.isFinite(reserve) || reserve < 0 || !Double.isFinite(flat)
                || !Double.isFinite(factor) || factor < 0) return Double.NaN;
        double result = reserve * factor + flat;
        return Double.isFinite(result) && result <= Float.MAX_VALUE ? Math.max(0, result) : Double.NaN;
    }
    public static double afterOrdinaryEntity(double reserve, double perEntityEnergy) {
        if (!Double.isFinite(reserve) || reserve < 0 || !Double.isFinite(perEntityEnergy) || perEntityEnergy < 0)
            return Double.NaN;
        double result = reserve + perEntityEnergy;
        return Double.isFinite(result) && result <= Float.MAX_VALUE ? result : Double.NaN;
    }
}
