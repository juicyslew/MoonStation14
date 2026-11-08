package com.juicyslew.moonstation14.ms14.combat;

/** Source-backed disarm chance policy (SS14 SharedMeleeWeaponSystem.CalculateDisarmChance). */
public final class DisarmPolicy {
    private DisarmPolicy() { }
    public static boolean canTarget(boolean hasLiveHands, boolean allowsKnockdown) { return hasLiveHands || allowsKnockdown; }
    public static float failureChance(float base, float heldMalus, boolean attackerDisarmProne, boolean targetDisarmProne) {
        if (attackerDisarmProne) return 1f;
        if (targetDisarmProne) return 0f;
        return Math.clamp(base + heldMalus, 0f, 1f);
    }
    public static float pushDamage(float failureChance, float critThreshold) {
        if (!Float.isFinite(failureChance) || !Float.isFinite(critThreshold) || critThreshold < 0) throw new IllegalArgumentException("invalid disarm push inputs");
        return (1f - Math.clamp(failureChance, 0f, 1f)) * critThreshold;
    }
    public static boolean shouldFallbackToStamina(com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.Result throwResult) {
        return throwResult == com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.Result.DENIED;
    }
    public static boolean shouldTerminate(com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.Result throwResult) {
        return throwResult != com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.Result.DENIED;
    }
    public static boolean skipStaminaWhenCritical(boolean staminaCritical) { return staminaCritical; }
}
