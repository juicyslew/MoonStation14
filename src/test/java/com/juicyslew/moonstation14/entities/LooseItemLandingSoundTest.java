package com.juicyslew.moonstation14.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LooseItemLandingSoundTest {
    @Test void cuesOnlyFirstTerrainContactOfEachArmedThrow() {
        boolean consumed = false;
        assertTrue(LooseItemEntity.shouldCueThrownLandingSound(true, consumed));
        consumed = true;
        assertFalse(LooseItemEntity.shouldCueThrownLandingSound(true, consumed)); // bounce/sliding contact
        assertFalse(LooseItemEntity.shouldCueThrownLandingSound(false, false)); // unarmed fall
        consumed = false; // armThrownCombat resets this on every canonical rethrow
        assertTrue(LooseItemEntity.shouldCueThrownLandingSound(true, consumed));
        assertFalse(LooseItemEntity.shouldCueThrownLandingSound(false, true)); // embedding ends free flight
    }
}
