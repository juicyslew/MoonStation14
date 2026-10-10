package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutlinePriorityTest {
    private static final Vec3 START = Vec3.ZERO;
    private static final Vec3 END = new Vec3(0, 0, 6);

    @Test void onlyForegroundPhysicalBoundsSuppressBlock() {
        Vec3 blockHit = new Vec3(0, 0, 3);
        assertTrue(OutlinePriority.itemPrecedesBlock(new AABB(-.2, -.2, 1, .2, .2, 1.5), START, END, blockHit));
        assertFalse(OutlinePriority.itemPrecedesBlock(new AABB(-.2, -.2, 3.5, .2, .2, 4), START, END, blockHit));
        assertFalse(OutlinePriority.itemPrecedesBlock(new AABB(1, 1, 1, 2, 2, 2), START, END, blockHit));
        assertTrue(OutlinePriority.itemPrecedesBlock(new AABB(-.05, -.05, 2.8, .05, .05, 2.9), START, END, blockHit));
        assertFalse(OutlinePriority.itemPrecedesBlock(new AABB(-.05, -.05, 3.05, .05, .05, 3.1), START, END, blockHit));
        assertFalse(OutlinePriority.itemPrecedesBlock(new AABB(1, 1, 2.8, 2, 2, 2.9), START, END, blockHit));
        assertFalse(OutlinePriority.itemPrecedesBlock(null, START, END, blockHit));
    }

    @Test void rayStartingInsidePhysicalBoundsHasZeroEntry() {
        Vec3 start = new Vec3(0, 0, 1);
        assertTrue(OutlinePriority.itemPrecedesBlock(new AABB(-.2, -.2, .5, .2, .2, 1.5), start, END, new Vec3(0, 0, 3)));
    }

    @Test void bothMarkersUseTheExistingLooseItemStyle() {
        assertEquals(.82f, OutlinePriority.RED);
        assertEquals(.98f, OutlinePriority.GREEN);
        assertEquals(1.0f, OutlinePriority.BLUE);
        assertEquals(.95f, OutlinePriority.ALPHA);
    }
}
