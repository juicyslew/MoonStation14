package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeWideAttackPolicyTest {
    @Test void nominationsAreCappedBeforeFilteringAndDeduplicatedInOrder() {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        UUID c=UUID.randomUUID(), d=UUID.randomUUID();
        assertEquals(List.of(a,b,c,d), LiveMeleeAttackService.wideNominationPolicy(List.of(a,a,b,c,d,UUID.randomUUID())));
    }
    @Test void modeControlsBaseScalingAndEmptySwing() {
        assertEquals(2f, LiveMeleeAttackService.baseScale(false, 2f));
        assertEquals(1f, LiveMeleeAttackService.baseScale(true, 2f));
        assertTrue(MeleeSoundPolicy.shouldPredictSwing(true));
        assertFalse(MeleeSoundPolicy.shouldPredictSwing(false));
    }
    @Test void firstInvalidCandidateDoesNotPreventLaterCandidatesAndValidatedMissSwings() {
        UUID missing=UUID.randomUUID(), valid=UUID.randomUUID();
        assertEquals(List.of(missing, valid), LiveMeleeAttackService.wideNominationPolicy(List.of(missing, valid)));
        assertFalse(MeleeSoundPolicy.shouldPredictSwing(false)); // valid contacts suppress miss feedback
        assertTrue(MeleeSoundPolicy.shouldPredictSwing(true)); // even when nominations existed but validation removed them all
    }
}
