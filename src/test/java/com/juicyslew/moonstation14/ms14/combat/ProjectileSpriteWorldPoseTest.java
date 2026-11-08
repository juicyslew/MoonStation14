package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSpriteWorldPoseTest {
 @Test void imageUpTracksWorldVelocityForHorizontalVerticalDiagonalAndHeadOnViews(){
  for(Vec3 v:new Vec3[]{new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(1,2,3)}) for(Vec3 view:new Vec3[]{v.normalize(),new Vec3(0,0,10),new Vec3(4,6,2)}) {
   var q=ProjectileSpritePose.worldRotation(v,view,Vec3.ZERO,0); assertNotNull(q);
   assertTrue(Float.isFinite(q.x)&&Float.isFinite(q.y)&&Float.isFinite(q.z)&&Float.isFinite(q.w));
   var up=q.transform(new org.joml.Vector3f(0,1,0));
   assertEquals(1,up.dot(new org.joml.Vector3f((float)v.normalize().x,(float)v.normalize().y,(float)v.normalize().z)),1e-5);
   var width=q.transform(new org.joml.Vector3f(1,0,0)); assertEquals(0,width.dot(up),1e-5);
  }
 }
 @Test void invalidVelocityAllowsRendererCameraFallback(){assertNull(ProjectileSpritePose.worldRotation(Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,0));}
 @Test void localZOffsetRotatesImageWithinTravelPlane(){var q=ProjectileSpritePose.worldRotation(new Vec3(0,1,0),new Vec3(0,0,1),Vec3.ZERO,90);var right=q.transform(new org.joml.Vector3f(1,0,0));var up=q.transform(new org.joml.Vector3f(0,1,0));var normal=q.transform(new org.joml.Vector3f(0,0,1));assertEquals(1,right.dot(new org.joml.Vector3f(0,1,0)),1e-5);assertEquals(0,up.dot(new org.joml.Vector3f(0,1,0)),1e-5);assertEquals(1,normal.length(),1e-5);}
 @Test void stableVolumePoseAlignsImageAxesAndUsesDeterministicVerticalFallback(){
  var y=ProjectileSpritePose.volumeRotation(new Vec3(1,0,0),new Vec3(0,1,0),0);
  assertEquals(1,y.transform(new org.joml.Vector3f(0,1,0)).dot(new org.joml.Vector3f(1,0,0)),1e-5);
  var laser=ProjectileSpritePose.volumeRotation(new Vec3(1,0,0),new Vec3(0,1,0),90);
   assertEquals(1,laser.transform(new org.joml.Vector3f(1,0,0)).dot(new org.joml.Vector3f(1,0,0)),1e-5);
   assertEquals(1,laser.transform(new org.joml.Vector3f(0,1,0)).dot(new org.joml.Vector3f(0,1,0)),1e-5);
  for(double sign:new double[]{-1,1}) { var q=ProjectileSpritePose.volumeRotation(new Vec3(0,sign,0),new Vec3(0,1,0),0); assertTrue(Float.isFinite(q.w)); }
 }
}
