package com.juicyslew.moonstation14.ms14.power.cable;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class CableCutSelectionTest {
    @Test void noSelectorRequiresExactlyOneInstalledTier() {
        assertEquals(CableCutSelection.Refusal.MISSING,
                CableCutSelection.select(Set.of(), null).refusal());
        for (CableTier tier : CableTier.values()) {
            var result = CableCutSelection.select(Set.of(tier), null);
            assertTrue(result.allowed());
            assertEquals(tier, result.tier());
            assertEquals(CableCutSelection.Refusal.NONE, result.refusal());
        }
        assertEquals(CableCutSelection.Refusal.AMBIGUOUS,
                CableCutSelection.select(Set.of(CableTier.HV, CableTier.MV), null).refusal());
        assertEquals(CableCutSelection.Refusal.AMBIGUOUS,
                CableCutSelection.select(Set.of(CableTier.HV, CableTier.MV, CableTier.APC), null).refusal());
    }

    @Test void selectorRequiresMatchingInstalledTierEvenWhenOnlyOneIsPresent() {
        for (CableTier tier : CableTier.values()) {
            assertEquals(CableCutSelection.Refusal.MISSING,
                    CableCutSelection.select(Set.of(), tier).refusal());
            var result = CableCutSelection.select(Set.of(CableTier.HV, CableTier.MV, CableTier.APC), tier);
            assertEquals(tier, result.tier());
            assertEquals(CableCutSelection.Refusal.NONE, result.refusal());
        }
        var mismatched = CableCutSelection.select(Set.of(CableTier.MV), CableTier.HV);
        assertFalse(mismatched.allowed());
        assertNull(mismatched.tier());
        assertEquals(CableCutSelection.Refusal.MISSING, mismatched.refusal());
        assertEquals(CableCutSelection.Refusal.MISSING,
                CableCutSelection.select(Set.of(CableTier.MV, CableTier.APC), CableTier.HV).refusal());
        assertEquals(CableTier.MV, CableCutSelection.select(Set.of(CableTier.MV), CableTier.MV).tier());
    }

    @Test void selectionNeverChangesItsInput() {
        Set<CableTier> tiers = Set.of(CableTier.HV, CableTier.APC);
        assertEquals(CableTier.APC, CableCutSelection.select(tiers, CableTier.APC).tier());
        assertEquals(CableCutSelection.Refusal.AMBIGUOUS, CableCutSelection.select(tiers, null).refusal());
        assertEquals(Set.of(CableTier.HV, CableTier.APC), tiers);
    }
}
