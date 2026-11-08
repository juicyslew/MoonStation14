package com.juicyslew.moonstation14.ms14.combat;

/** Converts committed blunt typed damage to the authored stamina dose. */
public final class MeleeStaminaPolicy {
    private MeleeStaminaPolicy() { }

    public static float amount(float committedBlunt, float factor) {
        if (!Float.isFinite(committedBlunt) || committedBlunt <= 0f
                || !Float.isFinite(factor) || factor <= 0f) return 0f;
        float amount = committedBlunt * factor;
        return Float.isFinite(amount) && amount > 0f ? amount : 0f;
    }
}
