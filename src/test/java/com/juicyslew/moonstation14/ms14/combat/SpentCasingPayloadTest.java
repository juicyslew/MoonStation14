package com.juicyslew.moonstation14.ms14.combat;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SpentCasingPayloadTest {
    @Test void packetRoundTripsCompleteResolvedPresentationAndSeed(){
        var original=new SpentCasingPayload(UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),
                ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/guns/cartridge_spent_body"),
                ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/weapons/guns/casings/casing_fall_2"),
                new Vec3(1,2,3),new Vec3(-.7,.3,.2),2,98765L,36,.16f,.035f,.72f,-1f,.2f);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {SpentCasingPayload.STREAM_CODEC.encode(buffer,original);var decoded=SpentCasingPayload.STREAM_CODEC.decode(buffer);
            assertEquals(original.body(),decoded.body());assertEquals(original.dimension(),decoded.dimension());assertEquals(original.sprite(),decoded.sprite());assertEquals(original.landingEvent(),decoded.landingEvent());
            assertEquals(original.position(),decoded.position());
            assertEquals(original.count(),decoded.count());assertEquals(original.seed(),decoded.seed());assertEquals(original.lifetimeTicks(),decoded.lifetimeTicks());
            assertEquals(original.initialSpeed(),decoded.initialSpeed());assertEquals(original.gravity(),decoded.gravity());
            assertEquals(original.damping(),decoded.damping());assertEquals(original.volumeDb(),decoded.volumeDb());assertEquals(original.pitchVariation(),decoded.pitchVariation());
            assertEquals(original.direction().x,decoded.direction().x,1e-12);assertEquals(original.direction().y,decoded.direction().y,1e-12);assertEquals(original.direction().z,decoded.direction().z,1e-12);
        }
        finally {buffer.release();}
    }
}
