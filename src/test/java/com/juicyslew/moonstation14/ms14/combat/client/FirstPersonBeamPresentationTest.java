package com.juicyslew.moonstation14.ms14.combat.client;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FirstPersonBeamPresentationTest {
 @Test void onlyLocalFirstPersonMovesVisualOriginAndEndpointRemainsAuthoritative(){
  Vec3 eye=Vec3.ZERO,end=new Vec3(0,0,5),src=eye;
  Vec3 moved=FirstPersonBeamPresentation.start(src,end,eye,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),true);
   assertNotEquals(eye,moved); assertTrue(moved.z>0); assertEquals(new Vec3(0,0,5),end);
   assertEquals(src,FirstPersonBeamPresentation.start(src,end,eye,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),false));
 }
 @Test void closeImpactIsNotOffsetAndVeryShortBeamDoesNotPassEndpoint(){
  Vec3 eye=Vec3.ZERO,hit=new Vec3(0,0,.1);
   assertEquals(eye,FirstPersonBeamPresentation.start(eye,hit,eye,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),true));
   Vec3 start=FirstPersonBeamPresentation.start(eye,hit,eye,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),true);
   assertTrue(start.distanceTo(hit)<=hit.length());
   assertEquals(eye,FirstPersonBeamPresentation.start(eye,new Vec3(0,0,2),eye,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),true,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.IMPACT));
 }
 @Test void cameraOnRayBeamHasFiniteProjectedAreaAndExactEndpoint(){
  Vec3 eye=Vec3.ZERO,dir=new Vec3(0,0,1),right=new Vec3(1,0,0),up=new Vec3(0,1,0);
  for(double length:new double[]{5,20}){
   Vec3 endpoint=dir.scale(length),start=FirstPersonBeamPresentation.start(eye,endpoint,eye,right,up,dir,true);
   var basis=BeamBillboardGeometry.basis(start,endpoint,eye);assertNotNull(basis);
   var corners=BeamBillboardGeometry.quad(basis,endpoint,.5f,.0f,1f,0f,1f);
    Vec3 reconstructed=basis.along().scale(basis.length()).add(start);
    assertEquals(endpoint.x,reconstructed.x,1e-12);assertEquals(endpoint.y,reconstructed.y,1e-12);assertEquals(endpoint.z,reconstructed.z,1e-12);
   double area=0;for(int i=0;i<4;i++){var a=corners[i].position();var b=corners[(i+1)%4].position();assertTrue(a.z>.05);area+=a.x*b.y-b.x*a.y;}assertTrue(Math.abs(area)>1e-5);
   assertEquals(eye,FirstPersonBeamPresentation.start(eye,endpoint,eye,right,up,dir,false));
  }
 }
 @Test void hitscanCompositionJoinsCapsAndInsetsAndKeepsHitAuthoritative(){
  Vec3 origin=Vec3.ZERO,hit=new Vec3(0,0,20),dir=new Vec3(0,0,1);
  var sprite=new com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.SpriteData(net.minecraft.resources.ResourceLocation.withDefaultNamespace("x"),"x",32,32,1,0,true,false);
  var beamEffect=new com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.ProjectileSpriteEffectData(sprite,.48f,false,0,1,.5f);
  var muzzleEffect=new com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.ProjectileSpriteEffectData(sprite,.48f,true,.5f,0,0);
  var p=FirstPersonBeamPresentation.compose(origin,hit,dir,origin,new Vec3(1,0,0),new Vec3(0,1,0),dir,false,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM,beamEffect);
  var muzzle=FirstPersonBeamPresentation.compose(origin,hit,dir,origin,new Vec3(1,0,0),new Vec3(0,1,0),dir,false,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.MUZZLE,muzzleEffect);
  assertEquals(.5,muzzle.source().z,1e-6);assertEquals(1,p.source().z,1e-6);assertEquals(19.5,p.endpoint().z,1e-6);assertEquals(muzzle.source().z+.5,p.source().z,1e-6);assertEquals(hit,p.endpoint().add(dir.scale(.5)));assertTrue(p.renderTravel());
  var shortBeam=FirstPersonBeamPresentation.compose(origin,new Vec3(1,0,0),dir,origin,new Vec3(1,0,0),new Vec3(0,1,0),dir,false,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM,beamEffect);assertFalse(shortBeam.renderTravel());
   var fps=FirstPersonBeamPresentation.compose(origin,hit,dir,origin,new Vec3(1,0,0),new Vec3(0,1,0),dir,true,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM,beamEffect);var beamRay=hit.subtract(fps.source()).normalize();var reconstructedHit=fps.endpoint().add(beamRay.scale(.5));assertEquals(hit.x,reconstructedHit.x,2e-5);assertEquals(hit.y,reconstructedHit.y,2e-5);assertEquals(hit.z,reconstructedHit.z,2e-5);
  var custom=new com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.ProjectileSpriteEffectData(sprite,.48f,false,.7f,.2f,.3f);
  assertEquals(.2,FirstPersonBeamPresentation.compose(origin,hit,dir,origin,new Vec3(1,0,0),new Vec3(0,1,0),dir,false,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM,custom).source().z,1e-6);
  assertEquals(.7,FirstPersonBeamPresentation.compose(origin,hit,dir,origin,new Vec3(1,0,0),new Vec3(0,1,0),dir,false,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.MUZZLE,custom).source().z,1e-6);
 }
}
