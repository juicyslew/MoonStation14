package com.juicyslew.moonstation14.ms14.combat;

/** Pure admission policy; callers supply already-resolved attack facts. */
public final class MeleeAttackPolicy {
    public enum Reason { ALLOWED, NOT_COMBAT_MODE, STUNNED, CRITICAL, DEAD, COOLDOWN, SOURCE_MISMATCH, UNARMED_SELF_TARGET }
    private MeleeAttackPolicy() { }

    /** Rejection precedence is deterministic and follows attack admission order. Prone is intentionally not a gate. */
    public static Reason evaluate(boolean combatMode, boolean stunned, boolean critical, boolean dead,
                                  boolean sourceMatch, boolean inCooldown, boolean unarmedSelfTarget) {
        if (!sourceMatch) return Reason.SOURCE_MISMATCH;
        if (inCooldown) return Reason.COOLDOWN;
        if (!combatMode) return Reason.NOT_COMBAT_MODE;
        if (stunned) return Reason.STUNNED;
        if (critical) return Reason.CRITICAL;
        if (dead) return Reason.DEAD;
        if (unarmedSelfTarget) return Reason.UNARMED_SELF_TARGET;
        return Reason.ALLOWED;
    }
}
