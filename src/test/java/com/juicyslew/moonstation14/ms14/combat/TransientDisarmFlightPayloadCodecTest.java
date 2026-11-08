package com.juicyslew.moonstation14.ms14.combat;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransientDisarmFlightPayloadCodecTest {
    private static TransientDisarmFlightPayload roundTrip(TransientDisarmFlightPayload payload) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { TransientDisarmFlightPayload.STREAM_CODEC.encode(buffer,payload); return TransientDisarmFlightPayload.STREAM_CODEC.decode(buffer); }
        finally { buffer.release(); }
    }
    private static TransientDisarmFlightPayload payload(boolean participant, boolean actor) {
        return new TransientDisarmFlightPayload(ResourceLocation.withDefaultNamespace("overworld"), UUID.randomUUID(),new Vec3(1,2,3),new Vec3(0,0,1),participant,actor);
    }
    @Test void codecPreservesActorVictimAndObserverRoleBits() {
        var actor=roundTrip(payload(true,true)); assertTrue(actor.participant()); assertTrue(actor.actor());
        var victim=roundTrip(payload(true,false)); assertTrue(victim.participant()); assertFalse(victim.actor());
        var observer=roundTrip(payload(false,false)); assertFalse(observer.participant()); assertFalse(observer.actor());
    }
    @Test void actorMustBeParticipantAndLegacyConstructorMeansNonActor() {
        assertThrows(IllegalArgumentException.class,()->payload(false,true));
        var legacy=new TransientDisarmFlightPayload(ResourceLocation.withDefaultNamespace("overworld"),UUID.randomUUID(),Vec3.ZERO,new Vec3(1,0,0),true);
        assertTrue(legacy.participant()); assertFalse(legacy.actor());
        assertFalse(roundTrip(legacy).actor());
    }
}
