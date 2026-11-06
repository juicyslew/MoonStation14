package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NativeHullContactTargetSelectorTest {
    private static NativeSelectedContactAccess.Entry entry(Vec3 normal, boolean vertical, long time, Vec3 position) {
        return new NativeSelectedContactAccess.Entry(UUID.randomUUID(), BlockPos.ZERO, normal, vertical,
                position, Vec3.ZERO, 0, time, false);
    }

    @Test void allowsFloorAndSideAndDoesNotRequireMoveInFlight() {
        var floor = entry(new Vec3(0, 1, 0), true, 10, Vec3.ZERO);
        var side = entry(new Vec3(-1, 0, 0), false, 10, Vec3.ZERO);
        var target = NativeHullContactTargetSelector.select(new Vec3(3, -2, 0), List.of(floor, side), 10,
                false, ignored -> Vec3.ZERO);
        assertNotNull(target);
        assertEquals(0, target.outgoingNormalSpeed(), 1e-9);
    }

    @Test void refusesStaleOverflowInvalidAndDistinctPlanes() {
        var a = entry(new Vec3(-1, 0, 0), false, 10, Vec3.ZERO);
        assertNull(NativeHullContactTargetSelector.select(new Vec3(1, 0, 0), List.of(a), 11, false, e -> Vec3.ZERO));
        assertNull(NativeHullContactTargetSelector.select(new Vec3(1, 0, 0), List.of(a), 10, true, e -> Vec3.ZERO));
        assertNull(NativeHullContactTargetSelector.select(new Vec3(1, 0, 0), List.of(a,
                entry(new Vec3(0, 0, -1), false, 10, Vec3.ZERO)), 10, false, e -> Vec3.ZERO));
    }

    @Test void incomingIsCallerSuppliedRatherThanCapturedOrClippedMotion() {
        var side = entry(new Vec3(-1, 0, 0), false, 10, Vec3.ZERO);
        var target = NativeHullContactTargetSelector.select(new Vec3(4, 0, 0), List.of(side), 10,
                false, e -> Vec3.ZERO);
        assertEquals(0, target.outgoingNormalSpeed(), 1e-9);
    }
}
