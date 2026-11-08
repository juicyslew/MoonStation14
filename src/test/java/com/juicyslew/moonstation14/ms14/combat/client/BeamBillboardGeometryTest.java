package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeamBillboardGeometryTest {
    @Test void quadUsesSelectedFrameAndTileScaleWithStableRayBasis() {
        for (Vec3 ray : new Vec3[]{new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,2,3)}) {
            Vec3 from=new Vec3(2,3,4), to=from.add(ray), camera=from.add(ray.scale(10));
            var basis=BeamBillboardGeometry.basis(from,to,camera);
            assertNotNull(basis);
            var corners=BeamBillboardGeometry.quad(basis,to,.5f,.25f,.5f,.5f,1f);
            assertEquals(4,corners.length);
            for(var c:corners){assertTrue(c.u()>=.25f&&c.u()<=.5f);assertTrue(c.v()>=.5f&&c.v()<=1f);assertTrue(Double.isFinite(c.position().x));}
            assertEquals(ray.length(),basis.length(),1e-8);
            assertEquals(ray.length(),corners[0].position().distanceTo(corners[3].position()),1e-8);
            assertEquals(1,corners[0].position().distanceTo(corners[1].position()),1e-8);
            assertEquals(ray.length(),corners[1].position().distanceTo(corners[2].position()),1e-8);
            assertEquals(1,corners[2].position().distanceTo(corners[3].position()),1e-8);
        }
        assertNull(BeamBillboardGeometry.basis(Vec3.ZERO,Vec3.ZERO,new Vec3(0,0,1)));
    }
}
