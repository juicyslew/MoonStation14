package com.juicyslew.moonstation14.ms14.player_body_control.movement;

/** Shared bounded outcome classification for movement-owned live Character step consumers. */
public final class CharacterWorldStepRecoveryPolicy {
    private CharacterWorldStepRecoveryPolicy() { }

    public enum Outcome { CONTINUE, REBASE, HOLD, TERMINAL }

    public static Outcome afterStep(boolean stepAccepted, boolean exactOwnerStillValid, boolean safeFinitePose) {
        if (!exactOwnerStillValid) return Outcome.TERMINAL;
        if (stepAccepted) return Outcome.CONTINUE;
        return safeFinitePose ? Outcome.REBASE : Outcome.HOLD;
    }
}
