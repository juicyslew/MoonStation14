package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemSlipContactGeometryTest {
    @Test void peelRatioUsesLargerNormalizedAreaAndThreshold() {
        AABB target = new AABB(-.25, 0, -.25, .25, 1.8, .25);
        AABB peel = new AABB(-.2, 0, -.2, .2, .15, .2);
        assertEquals(1d, SlipSystem.contactIntersectionRatio(target, peel), 1e-9);
        assertEquals(.375d, SlipSystem.contactIntersectionRatio(
                new AABB(.05, 0, -.25, .55, 1.8, .25), peel), 1e-9);
    }

    @Test void feetBandExcludesChestItemsAndPreservesVerticalAndEdgeSeparation() {
        AABB body = new AABB(0, 0, 0, .6, 1.8, .6);
        AABB feet = SlipSystem.itemContactFeetBand(body);
        assertEquals(.25d, feet.getYsize(), 1e-9);
        assertEquals(0d, SlipSystem.contactIntersectionRatio(feet, new AABB(.1, .8, .1, .5, 1, .5)), 1e-9);
        assertEquals(0d, SlipSystem.contactIntersectionRatio(feet, new AABB(.6, 0, .1, .8, .15, .5)), 1e-9);
        assertEquals(0d, SlipSystem.contactIntersectionRatio(feet, new AABB(.1, .25, .1, .5, .4, .5)), 1e-9);
    }

    @Test void invalidAreaIsNeutral() {
        assertEquals(0d, SlipSystem.contactIntersectionRatio(new AABB(0, 0, 0, 0, 1, 1),
                new AABB(0, 0, 0, 1, 1, 1)), 0d);
    }
}
