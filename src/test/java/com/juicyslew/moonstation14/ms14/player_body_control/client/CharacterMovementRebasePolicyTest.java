package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CharacterMovementRebasePolicyTest {
    @Test void rebaseIsAuthorityOnlyAndRevisionScoped() {
        assertEquals(CharacterMovementRebasePolicy.Decision.REBASE,
                CharacterMovementRebasePolicy.decide(3, 4, true));
        assertEquals(CharacterMovementRebasePolicy.Decision.REJECT,
                CharacterMovementRebasePolicy.decide(4, 3, true));
        assertEquals(CharacterMovementRebasePolicy.Decision.NORMAL,
                CharacterMovementRebasePolicy.decide(4, 4, false));
        assertEquals(CharacterMovementRebasePolicy.Decision.BARRIER,
                CharacterMovementRebasePolicy.decide(4, 4, true));
    }

    @Test void stoppedPredictionResumesOnlyAtKnownFreshAcknowledgedAuthorityBarrier() {
        assertEquals(CharacterMovementRebasePolicy.Decision.RESUME_BARRIER,
                CharacterMovementRebasePolicy.decide(4, 4, true, 12, 12, true, true));
        assertEquals(CharacterMovementRebasePolicy.Decision.BARRIER,
                CharacterMovementRebasePolicy.decide(4, 4, true, 12, 11, true, true),
                "an older acknowledgement cannot settle the failed intent");
        assertEquals(CharacterMovementRebasePolicy.Decision.REJECT,
                CharacterMovementRebasePolicy.decide(4, 4, true, 12, 12, false, true),
                "duplicate/stale sample cannot resume prediction");
        assertEquals(CharacterMovementRebasePolicy.Decision.REJECT,
                CharacterMovementRebasePolicy.decide(4, 4, true, 12, 12, true, false),
                "unknown or invalid frame cannot resume prediction");
        assertEquals(CharacterMovementRebasePolicy.Decision.REJECT,
                CharacterMovementRebasePolicy.decide(4, 3, true, 12, 12, true, true),
                "old movement revision cannot resume this session");
    }
}
