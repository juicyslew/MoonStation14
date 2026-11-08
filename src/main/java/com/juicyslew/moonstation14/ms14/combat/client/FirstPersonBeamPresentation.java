package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;

/** Presentation-only camera clearance for the controlled shooter's own beam/muzzle. */
public final class FirstPersonBeamPresentation {
 private FirstPersonBeamPresentation(){}
 public static Vec3 start(Vec3 source,Vec3 endpoint,Vec3 eye,Vec3 right,Vec3 up,Vec3 forward,boolean localFirstPerson){
  return start(source,endpoint,eye,right,up,forward,localFirstPerson,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM);
 }
 public static Vec3 start(Vec3 source,Vec3 endpoint,Vec3 eye,Vec3 right,Vec3 up,Vec3 forward,boolean localFirstPerson,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind kind){
  if(!localFirstPerson||kind==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.IMPACT||source.distanceTo(eye)>.65||source.distanceTo(endpoint)<=.15)return source;
  Vec3 offset=right.scale(.12).add(up.scale(-.12)).add(forward.scale(.25));
  double max=Math.max(0,source.distanceTo(endpoint)-.03); if(offset.length()>max&&offset.length()>0)offset=offset.scale(max/offset.length());
  return source.add(offset);
 }
 public record Placement(Vec3 source,Vec3 endpoint,boolean renderTravel){}
 /** Resolved effect fields are authoritative for point offsets and beam insets. */
 public static Placement compose(Vec3 rawOrigin,Vec3 hit,Vec3 direction,Vec3 eye,Vec3 right,Vec3 up,Vec3 forward,boolean localFirstPerson,com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind kind,com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.ProjectileSpriteEffectData effect){
  var visualOrigin=start(rawOrigin,hit,eye,right,up,forward,localFirstPerson,kind);
  if(kind==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.IMPACT)return new Placement(hit,hit,false);
  Vec3 ray=kind==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM?hit.subtract(visualOrigin).normalize():direction.normalize();
  if(kind==com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload.Kind.BEAM){double distance=visualOrigin.distanceTo(hit);double remaining=distance-effect.startInset()-effect.endInset();return new Placement(visualOrigin.add(ray.scale(effect.startInset())),hit.subtract(ray.scale(effect.endInset())),remaining>1e-5);}
  return new Placement(visualOrigin.add(ray.scale(effect.forwardOffset())),null,false);
 }
}
