package com.juicyslew.moonstation14.ms14.player_body_control.movement;

/** Pure hold/resume decision shared by the live lifecycle path and focused tests. */
public final class CharacterMovementHoldPolicy {
    private CharacterMovementHoldPolicy() { }

    public static Decision evaluate(boolean exactAuthorityAndMode, boolean knownSafeAuthorityPoint) {
        if (!exactAuthorityAndMode) return Decision.TERMINAL_GUARD;
        return knownSafeAuthorityPoint ? Decision.RESUME : Decision.HOLD;
    }

    public enum Decision { HOLD, RESUME, TERMINAL_GUARD }
}
