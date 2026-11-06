package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CharacterWorldStepRecoveryPolicyTest {
    @Test
    void distinguishesOwnerLossFromRecoverableFiniteMovementFault() {
        assertEquals(CharacterWorldStepRecoveryPolicy.Outcome.CONTINUE,
                CharacterWorldStepRecoveryPolicy.afterStep(true, true, true));
        assertEquals(CharacterWorldStepRecoveryPolicy.Outcome.REBASE,
                CharacterWorldStepRecoveryPolicy.afterStep(false, true, true));
        assertEquals(CharacterWorldStepRecoveryPolicy.Outcome.HOLD,
                CharacterWorldStepRecoveryPolicy.afterStep(false, true, false));
        assertEquals(CharacterWorldStepRecoveryPolicy.Outcome.TERMINAL,
                CharacterWorldStepRecoveryPolicy.afterStep(false, false, true));
    }
}
