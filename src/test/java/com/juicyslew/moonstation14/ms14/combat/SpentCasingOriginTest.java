package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpentCasingOriginTest {
    @Test void bothHandOriginsStayAtHeldWeaponHeightAndToTheirLateralSide(){
        Vec3 eye=new Vec3(4,1.62,7),aim=new Vec3(0,0,1);
        var right=SpentCasingOrigin.resolve(eye,aim,false);var left=SpentCasingOrigin.resolve(eye,aim,true);
        assertEquals(1.30,right.y,1e-9);assertEquals(1.30,left.y,1e-9);
        assertEquals(-.22,right.x-eye.x,1e-9);assertEquals(.22,left.x-eye.x,1e-9);
        assertEquals(.35,right.z-eye.z,1e-9);assertEquals(.35,left.z-eye.z,1e-9);
        assertTrue(right.distanceTo(eye)<.6);assertTrue(left.distanceTo(eye)<.6);
    }
}
