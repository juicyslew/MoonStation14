package com.juicyslew.moonstation14.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LooseImpactContactTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void requiresClippingOfNonzeroRequestedTravel() {
        assertFalse(contact(.75, 0, 0, .75, 1.877461613730702e-6, 0)); // R33 pushout
        assertFalse(contact(.75, 0, 0, .8, 1.877461613730702e-6, 0)); // further along request
        assertFalse(contact(0, 0, 0, .01, -.01, .01)); // zero-axis correction
        assertFalse(contact(.75, 0, 0, .75 - EPSILON, 0, 0)); // at epsilon boundary
        assertFalse(contact(.75, 0, 0, .75 - EPSILON / 2, 0, 0)); // below epsilon
        assertFalse(contact(.75, 0, 0, .75, 0, 0)); // unchanged
    }

    @Test
    void recognizesOpposingClipsAlongEveryAxis() {
        assertTrue(contact(.75, 0, 0, .74, 0, 0));
        assertTrue(contact(-.75, 0, 0, -.74, 0, 0));
        assertTrue(contact(0, .75, 0, 0, .74, 0));
        assertTrue(contact(0, -.75, 0, 0, -.74, 0));
        assertTrue(contact(0, 0, .75, 0, 0, .74));
        assertTrue(contact(0, 0, -.75, 0, 0, -.74));
        assertTrue(contact(.75, 0, 0, -.01, 0, 0)); // opposed response is genuine clipping
        assertTrue(contact(.75, -.02, 0, .75, 0, 0)); // low floor approach clips Y despite unchanged fast horizontal travel
        assertTrue(contact(.75, 0, 0, .74, 0, 0));
        assertTrue(contact(-.75, 0, 0, -.74, 0, 0));
    }

    @Test
    void fullBoxFloorContactClipsWhenCenterRayMisses() {
        var box = new net.minecraft.world.phys.AABB(0, 1.1, 0, 1, 1.6, 1);
        var floor = net.minecraft.world.phys.shapes.Shapes.block();
        var clipped = LooseItemEntity.clipVanillaWorldMotion(box, java.util.List.of(floor),
                new net.minecraft.world.phys.Vec3(.4, -.2, .3));
        // The center ray stays above the floor (1.35 -> 1.15), while the box bottom reaches y=1.
        assertTrue(1.35 - .2 > 1.0);
        assertEquals(-.1, clipped.y, 1.0e-9);
        assertEquals(.4, clipped.x, 1.0e-9);
        assertEquals(.3, clipped.z, 1.0e-9);
    }

    private static boolean contact(double rx, double ry, double rz, double ax, double ay, double az) {
        return LooseImpactContact.clippedOwnTravel(rx, ry, rz, ax, ay, az, EPSILON);
    }
}
