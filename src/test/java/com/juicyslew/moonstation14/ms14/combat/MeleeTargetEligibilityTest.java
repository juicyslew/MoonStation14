package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeleeTargetEligibilityTest {
    @Test void preservesLivingTargetsAndExcludesGhostEquivalentPlayers() {
        assertTrue(CommonMeleeTargetEligibility.eligible(true, false, false, false), "ordinary player/living target");
        assertTrue(CommonMeleeTargetEligibility.eligible(true, false, false, false), "dead or critical living target; no isAlive gate");
        assertFalse(CommonMeleeTargetEligibility.eligible(true, true, false, false), "spectator player");
        assertFalse(CommonMeleeTargetEligibility.eligible(true, false, true, false), "transport carrier");
    }

    @Test void looseItemsRequireResolvedItemDamage() {
        assertTrue(CommonMeleeTargetEligibility.eligible(false, false, false, true));
        assertFalse(CommonMeleeTargetEligibility.eligible(false, false, false, false));
    }
}
