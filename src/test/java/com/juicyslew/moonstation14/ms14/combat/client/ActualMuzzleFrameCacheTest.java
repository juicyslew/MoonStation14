package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActualMuzzleFrameCacheTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID BODY = UUID.randomUUID();
    private static final ResourceLocation ITEM = ResourceLocation.withDefaultNamespace("bow");
    @Test void eachRenderGenerationEvictsSameTickCapturesAndIdentityMustMatch() {
        var cache = new ActualMuzzleFrameCache();
        var value = new ActualMuzzleFrame(new Vec3(1,2,3), new Vec3(0,0,1), new Vec3(0,1,0), 7);
        cache.beginFrame(7); cache.put(DIM,BODY,"right",ITEM,4,value);
        assertTrue(cache.get(DIM,BODY,"right",ITEM,4,7).isPresent());
        cache.beginFrame(8); // a second rendered frame can occur in the same game tick
        assertTrue(cache.get(DIM,BODY,"right",ITEM,4,8).isEmpty());
        var fresh = new ActualMuzzleFrame(new Vec3(9,8,7), new Vec3(0,0,1), new Vec3(0,1,0), 8);
        cache.put(DIM,BODY,"right",ITEM,4,fresh);
        assertTrue(cache.get(DIM,BODY,"left",ITEM,4,8).isEmpty());
        assertTrue(cache.get(DIM,UUID.randomUUID(),"right",ITEM,4,8).isEmpty());
        assertTrue(cache.get(DIM,BODY,"right",ResourceLocation.withDefaultNamespace("stone"),4,8).isEmpty());
        assertTrue(cache.get(DIM,BODY,"right",ITEM,5,8).isEmpty());
        assertEquals(fresh,cache.get(DIM,BODY,"right",ITEM,4,8).orElseThrow());
        cache.clear(); assertTrue(cache.get(DIM,BODY,"right",ITEM,4,8).isEmpty());
    }

    @Test void productionCaptureAddsCameraWorldTranslationWithoutCameraRotation() {
        var binding=new GunPresentationBindings.Binding(new GunArmAimMath.Vec(0,0,2),new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,0),null,new GunArmAimMath.Vec(1,0,0));
        var matrix=new org.joml.Matrix4f().translation(4,5,6).rotateY((float)Math.PI/2).scale(.7f);
        var camera=new Vec3(100,50,-20);
        var frame=ActualMuzzleFrame.capture(matrix,binding,camera,9);
        assertEquals(camera.add(matrix.transformPosition(new org.joml.Vector3f(1,0,0)).x,
                matrix.transformPosition(new org.joml.Vector3f(1,0,0)).y,matrix.transformPosition(new org.joml.Vector3f(1,0,0)).z),frame.muzzle());
        assertEquals(1,frame.bore().length(),1e-6);assertEquals(1,frame.up().length(),1e-6);
        assertEquals(9,frame.frame());
    }
}
