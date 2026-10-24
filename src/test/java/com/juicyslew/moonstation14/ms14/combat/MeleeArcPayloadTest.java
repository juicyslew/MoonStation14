package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcPayloadTest {
    @Test void observerArcUsesModeSpecificSourceArc() {
        var light=ResourceLocation.fromNamespaceAndPath("test","light_arc"); var wide=ResourceLocation.fromNamespaceAndPath("test","wide_arc");
        var data=new MeleeWeaponData(java.util.Map.of("blunt",1f),1f,1.5f,false,false,1f,false,MeleeSoundData.DEFAULT,light,1f,0f,0.5f,true,60f,wide,true);
        assertEquals(light,MeleeArcObserverPublisher.arcForMode(data,MeleeAttackRequest.Mode.LIGHT));
        assertEquals(wide,MeleeArcObserverPublisher.arcForMode(data,MeleeAttackRequest.Mode.WIDE));
    }
    @Test void streamCodecRoundTripsUnarmedAndWeaponSnapshots() {
        var base = new MeleeArcPayload(17,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("the_nether"),UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("moonstation14","swing"),1.75f,60f,0.25f,37.5f,
                new Vec3(1.25,2.5,3.75),new Vec3(-0.5,0.25,1),new Vec3(8,9,10),null,true);
        assertEquals(base,roundTrip(base));
        var armed = new MeleeArcPayload(base.bodyEntityId(),base.bodyUUID(),base.dimension(),base.initiatingPlayerUUID(),base.arc(),
                base.attackRate(),base.angleDegrees(),base.animationOffset(),base.wideAnimationRotation(),base.frozenBodyPosition(),base.direction(),
                base.attackPoint(),ItemPresentation.fromStack(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("stick")))),base.swingLeft());
        assertEquals(armed,roundTrip(armed));
        var decoded=roundTrip(armed);
        assertNotEquals(decoded.bodyUUID(),decoded.initiatingPlayerUUID());
        assertEquals(base.dimension(),decoded.dimension()); assertEquals(base.attackRate(),decoded.attackRate());
        assertEquals(base.wideAnimationRotation(),decoded.wideAnimationRotation()); assertEquals(base.attackPoint(),decoded.attackPoint());
    }
    private static MeleeArcPayload roundTrip(MeleeArcPayload value) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try { MeleeArcPayload.STREAM_CODEC.encode(buffer,value); return MeleeArcPayload.STREAM_CODEC.decode(buffer); }
        finally { buffer.release(); }
    }
}
