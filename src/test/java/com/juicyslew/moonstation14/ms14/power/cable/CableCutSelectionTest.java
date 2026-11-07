package com.juicyslew.moonstation14.ms14.power.cable;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class CableCutSelectionTest {
    @Test void noGeometryHitNeverFallsBackToSingleOrMultipleInstalledTiers() {
        assertEquals(CableCutSelection.Refusal.NO_VISIBLE_HIT,
                CableCutSelection.select(Set.of(), null).refusal());
        for (CableTier tier : CableTier.values()) {
            var result = CableCutSelection.select(Set.of(tier), null);
            assertFalse(result.allowed());
            assertEquals(CableCutSelection.Refusal.NO_VISIBLE_HIT, result.refusal());
        }
        assertEquals(CableCutSelection.Refusal.NO_VISIBLE_HIT,
                CableCutSelection.select(Set.of(CableTier.HV, CableTier.MV), null).refusal());
        assertEquals(CableCutSelection.Refusal.NO_VISIBLE_HIT,
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
        assertEquals(CableCutSelection.Refusal.NO_VISIBLE_HIT, CableCutSelection.select(tiers, null).refusal());
        assertEquals(Set.of(CableTier.HV, CableTier.APC), tiers);
    }

    @Test void sharedVisibilityRejectsCoveredFloorTopButAllowsExposedFaces() {
        for (CableTier tier : CableTier.values()) {
            assertTrue(CableCutSelection.select(Set.of(tier), tier).allowed());
            assertEquals(CableCutSelection.Refusal.MISSING,
                    CableCutSelection.select(Set.of(), tier).refusal());
            CableTier nonMatchingTier = tier == CableTier.HV ? CableTier.MV : CableTier.HV;
            var mismatch = CableCutSelection.select(Set.of(tier), nonMatchingTier);
            assertFalse(mismatch.allowed());
            assertNull(mismatch.tier());
            assertEquals(CableCutSelection.Refusal.MISSING,
                    mismatch.refusal());
        }
        assertFalse(CableFacePresentation.isVisible(net.minecraft.core.Direction.UP, true, true));
        assertTrue(CableFacePresentation.isVisible(net.minecraft.core.Direction.NORTH, true, true));
        assertTrue(CableFacePresentation.isVisible(net.minecraft.core.Direction.UP, true, false));
    }
}
