package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LooseItemRayGeometryTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void centerAimRankingPrefersCloserAngularCenterRatherThanFrontEdge() {
        Vec3 start = Vec3.ZERO;
        Vec3 look = new Vec3(0, 0, 1);
        AABB frontEdgeNear = new AABB(-1, -1, 2, 0.1, 1, 3);
        AABB rearCenter = new AABB(-0.2, -0.2, 4, 0.2, 0.2, 5);

        assertTrue(LooseItemRayGeometry.entry(frontEdgeNear, start, new Vec3(0, 0, 6)) != null);
        assertTrue(LooseItemRayGeometry.centerAimOrder(start, look).compare(rearCenter, frontEdgeNear) < 0);
    }

    @Test void equalGeometryCanBeOrderedDeterministicallyByUuid() {
        AABB box = new AABB(-1, -1, 2, 1, 1, 4);
        int geometryTie = LooseItemRayGeometry.centerAimOrder(Vec3.ZERO, new Vec3(0, 0, 1)).compare(box, box);
        assertEquals(0, geometryTie);
        assertTrue(geometryTie != 0 || A.compareTo(B) < 0);
    }
}
