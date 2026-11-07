package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.combat.client.GunMuzzleFlashClient;

 class GunMuzzleFlashPayloadTest {
 @Test void shotSourceRoundTripsAsPublicIdentityOnly(){
   var effect=new ProjectileProfile.ProjectileSpriteEffectData(new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","rsi"),"muzzle_bullet",32,32,1,0,true,false),.4f);
   var source=new ShotVisualSource("left",ResourceLocation.withDefaultNamespace("bow"),42);
   var sent=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.MUZZLE,UUID.randomUUID(),UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),Vec3.ZERO,new Vec3(0,0,1),effect,null,source);
   var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);GunMuzzleFlashPayload.STREAM_CODEC.encode(buffer,sent);
   var decoded=GunMuzzleFlashPayload.STREAM_CODEC.decode(buffer);assertEquals(source,decoded.source());assertEquals(sent,decoded);
   assertEquals("left",decoded.source().handId());assertEquals(ResourceLocation.withDefaultNamespace("bow"),decoded.source().itemId());assertEquals(42,decoded.source().shotSequence());
 }
 @Test void realRegistryFriendlyStreamRoundTripAndInitiatorExclusion(){
  var effect=new ProjectileProfile.ProjectileSpriteEffectData(new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","rsi"),"muzzle_bullet",32,32,1,0,true,false),.4f);
  var actor=UUID.randomUUID(); var initiator=UUID.randomUUID();
  var sent=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.MUZZLE,actor,initiator,ResourceLocation.withDefaultNamespace("overworld"),new Vec3(1,2,3),new Vec3(0,0,1),effect);
  var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  GunMuzzleFlashPayload.STREAM_CODEC.encode(buffer,sent); var received=GunMuzzleFlashPayload.STREAM_CODEC.decode(buffer);
  assertEquals(sent,received); assertFalse(GunMuzzleFlashObserverPublisher.eligible(received,received.initiator())); assertTrue(GunMuzzleFlashObserverPublisher.eligible(received,UUID.randomUUID()));
    for(var kind:GunMuzzleFlashPayload.Kind.values()){var typed=kind==GunMuzzleFlashPayload.Kind.BEAM?new GunMuzzleFlashPayload(kind,actor,initiator,sent.dimension(),sent.position(),sent.direction(),effect,new Vec3(1,2,4)):new GunMuzzleFlashPayload(kind,actor,initiator,sent.dimension(),sent.position(),sent.direction(),effect);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);GunMuzzleFlashPayload.STREAM_CODEC.encode(b,typed);assertEquals(typed,GunMuzzleFlashPayload.STREAM_CODEC.decode(b));assertEquals(kind!=GunMuzzleFlashPayload.Kind.MUZZLE,GunMuzzleFlashClient.observerEligible(typed,initiator,sent.dimension()));}
  }
 @Test void beamRoundTripAndBeamGeometryHandlesParallelCameraAndDegenerateSegments(){
  var effect=new ProjectileProfile.ProjectileSpriteEffectData(new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","beam"),"beam",32,32,1,0,true,false),.2f);
  var beam=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.BEAM,UUID.randomUUID(),UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),Vec3.ZERO,new Vec3(1,0,0),effect,new Vec3(0,0,10));
  var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);GunMuzzleFlashPayload.STREAM_CODEC.encode(b,beam);assertEquals(beam,GunMuzzleFlashPayload.STREAM_CODEC.decode(b));
  var basis=com.juicyslew.moonstation14.ms14.combat.client.BeamBillboardGeometry.basis(Vec3.ZERO,new Vec3(0,0,10),new Vec3(0,0,20));assertNotNull(basis);assertEquals(10,basis.length(),1e-8);assertEquals(0,basis.along().dot(basis.across()),1e-8);
  assertNull(com.juicyslew.moonstation14.ms14.combat.client.BeamBillboardGeometry.basis(Vec3.ZERO,Vec3.ZERO,new Vec3(0,1,0)));
 }
}
