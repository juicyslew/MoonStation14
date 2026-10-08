package com.juicyslew.moonstation14.ms14.construction.inventory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyCraftNearbyEligibilityTest {
    @Test void pickupDelayIsNotACraftingEligibilityInputButTargetedSourcesStayProtected() {
        assertTrue(BodyCraftInventoryService.nearbyCandidateEligible(false,true,true));
        assertFalse(BodyCraftInventoryService.nearbyCandidateEligible(true,true,true));
        assertFalse(BodyCraftInventoryService.nearbyCandidateEligible(false,false,true));
        assertFalse(BodyCraftInventoryService.nearbyCandidateEligible(false,true,false));
    }
}
