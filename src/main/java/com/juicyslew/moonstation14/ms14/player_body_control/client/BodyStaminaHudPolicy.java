package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaState;

/** Read-only projection of the server-owned stamina snapshot for the character HUD. */
public final class BodyStaminaHudPolicy {
    public record View(float remaining, float capacity, boolean critical, boolean afterCritical) {}

    public static View project(StaminaPrototypeComponent capability, StaminaState state) {
        if (capability == null || state == null) return null;
        float capacity = capability.baseCritThreshold();
        if (!Float.isFinite(capacity) || capacity <= 0 || !Float.isFinite(state.damage())) return null;
        return new View(Math.max(0, capacity - state.damage()), capacity, state.critical(), state.afterCritical());
    }
}
