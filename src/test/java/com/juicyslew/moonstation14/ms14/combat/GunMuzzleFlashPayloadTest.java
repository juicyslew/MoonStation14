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
 @Test void realRegistryFriendlyStreamRoundTripAndInitiatorExclusion(){
  var effect=new ProjectileProfile.ProjectileSpriteEffectData(new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","rsi"),"muzzle_bullet",32,32,1,0,true,false),.4f);
  var actor=UUID.randomUUID(); var initiator=UUID.randomUUID();
  var sent=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.MUZZLE,actor,initiator,ResourceLocation.withDefaultNamespace("overworld"),new Vec3(1,2,3),new Vec3(0,0,1),effect);
  var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  GunMuzzleFlashPayload.STREAM_CODEC.encode(buffer,sent); var received=GunMuzzleFlashPayload.STREAM_CODEC.decode(buffer);
  assertEquals(sent,received); assertFalse(GunMuzzleFlashObserverPublisher.eligible(received,received.initiator())); assertTrue(GunMuzzleFlashObserverPublisher.eligible(received,UUID.randomUUID()));
  for(var kind:GunMuzzleFlashPayload.Kind.values()){var typed=new GunMuzzleFlashPayload(kind,actor,initiator,sent.dimension(),sent.position(),sent.direction(),effect);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);GunMuzzleFlashPayload.STREAM_CODEC.encode(b,typed);assertEquals(typed,GunMuzzleFlashPayload.STREAM_CODEC.decode(b));assertEquals(kind==GunMuzzleFlashPayload.Kind.IMPACT,GunMuzzleFlashClient.observerEligible(typed,initiator,sent.dimension()));}
 }
}
