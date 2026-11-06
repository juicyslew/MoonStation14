package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedPuddleContactQueryLimitsTest {
    @Test void worldFeetBandPreservesLegacyWideEligibleEntities() {
        assertFalse(SharedPuddleContactQuery.valid(box(1.6, .25, 1), true, false));
        assertTrue(SharedPuddleContactQuery.valid(box(1.6, .25, 1), true, true));
        assertTrue(SharedPuddleContactQuery.valid(box(16, .25, 1), true, true));
        assertFalse(SharedPuddleContactQuery.valid(box(16.01, .25, 1), true, true));
        assertFalse(SharedPuddleContactQuery.valid(box(1, .26, 1), true, true));
        assertFalse(SharedPuddleContactQuery.valid(new AABB(0, 0, 0, 0, .2, 1), true, true));
    }

    @Test void feetBandScanCellCapCoversTheBoundedLegacyFootprint() {
        assertTrue(SharedPuddleContactQuery.withinCellLimit(578, true));
        assertFalse(SharedPuddleContactQuery.withinCellLimit(579, true));
        assertTrue(SharedPuddleContactQuery.withinCellLimit(16, false));
        assertFalse(SharedPuddleContactQuery.withinCellLimit(17, false));
    }

    private static AABB box(double width, double height, double depth) {
        return new AABB(0, 0, 0, width, height, depth);
    }
}
