package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSpritePoseTest {
    private static final Quaternionf IDENTITY = new Quaternionf();
    private static final float HALF_PI=(float)(Math.PI/2);
    @Test void alignsSourceUpAxisToProjectedFlight() {
        assertEquals(0f,ProjectileSpritePose.rotation(new Vec3(0,1,0),IDENTITY,false),1e-6f);
        assertEquals(-HALF_PI,ProjectileSpritePose.rotation(new Vec3(1,0,0),IDENTITY,false),1e-6f);
        assertEquals((float)Math.PI,Math.abs(ProjectileSpritePose.rotation(new Vec3(0,-1,0),IDENTITY,false)),1e-6f);
    }
    @Test void noRotAndDegenerateCameraAlignedTravelStayStable() {
        assertEquals(0f,ProjectileSpritePose.rotation(new Vec3(1,0,0),IDENTITY,true),1e-6f);
        assertEquals(0f,ProjectileSpritePose.rotation(Vec3.ZERO,IDENTITY,false),1e-6f);
        assertEquals(0f,ProjectileSpritePose.rotation(new Vec3(0,0,1),IDENTITY,false),1e-6f);
        Quaternionf camera=new Quaternionf().rotationY(HALF_PI);
        assertEquals(0f,ProjectileSpritePose.rotation(new Vec3(1,0,0),camera,false),1e-6f);
    }
}
