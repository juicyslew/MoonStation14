package com.juicyslew.moonstation14.ms14.combat;

/** Pure test oracle: timestamp gaps do not equal authorization-admission counts. */
public final class ChargerAuthorizationObservation {
    private ChargerAuthorizationObservation() { }

    public record Decision(boolean valid, boolean admitted, float chargeDelta, String failure) { }

    /** A changed monotonic stamp proves one new authorization; only its charge quantum is counted. */
    public static Decision observe(long previousStamp, long currentStamp,
                                   float previousCharge, float currentCharge, float expectedJoules) {
        if (currentStamp < previousStamp) return invalid(0, "authorization stamp moved backwards");
        if (!Float.isFinite(previousCharge) || previousCharge < 0 || !Float.isFinite(currentCharge) || currentCharge < 0
                || !Float.isFinite(expectedJoules) || expectedJoules <= 0)
            return invalid(0, "invalid charge observation or expected quantum");
        float delta = currentCharge - previousCharge;
        if (currentStamp == previousStamp) {
            if (Math.abs(delta) > 1.0e-4f) return invalid(delta, "charge changed without a new authorization stamp");
            return new Decision(true, false, delta, "unchanged stamp/charge");
        }
        if (Math.abs(delta - expectedJoules) > 1.0e-3f)
            return invalid(delta, "new authorization did not deliver exactly one resolved-rate quantum");
        return new Decision(true, true, delta, "one admitted advance");
    }

    private static Decision invalid(float delta, String failure) {
        return new Decision(false, false, delta, failure);
    }
}
