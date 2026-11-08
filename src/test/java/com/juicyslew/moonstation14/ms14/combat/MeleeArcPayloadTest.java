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
    @Test void codecPreservesFullPitchStrikeGeometryAndLegacyNull() {
        var points = java.util.stream.IntStream.range(0,13).mapToObj(i -> new Vec3(i, i + .1, -i)).toList();
        var bounds=new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        var strike = new MeleeArcGeometry.StrikeCapture(new Vec3(1,2,3),new Vec3(1,3,3),new Vec3(1,3.2,3),new Vec3(.1,.9,.2),45,
                2.1,MeleeAttackRequest.Mode.WIDE,120,new Vec3(2,8,4),points,bounds);
        var base = new MeleeArcPayload(1,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("test","arc"),1,120,0,0,new Vec3(1,2,3),new Vec3(0,0,1),new Vec3(1,2,4),null,false,strike,"off_hand");
        var decoded = roundTrip(base);
        assertEquals(base,decoded); assertEquals(.9,decoded.strikeGeometry().fullDirection().y); assertEquals(2.1,decoded.strikeGeometry().range());
        assertEquals(points,decoded.strikeGeometry().boundaryEndpoints()); assertEquals("off_hand",decoded.activeHand());
        assertEquals(bounds,decoded.strikeGeometry().sourceBounds());
        assertThrows(UnsupportedOperationException.class, () -> decoded.strikeGeometry().boundaryEndpoints().add(Vec3.ZERO));
        var legacy = new MeleeArcPayload(2,UUID.randomUUID(),base.dimension(),UUID.randomUUID(),base.arc(),1,0,0,0,
                Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,null,false);
        assertNull(roundTrip(legacy).strikeGeometry());
    }
    @Test void bodyRelativeHandlessSnapshotRoundTripsAndInvalidHandIdsReject() {
        var strike = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,1,0),new Vec3(0,0,1),0,
                1,MeleeAttackRequest.Mode.LIGHT,0,new Vec3(0,1,1),java.util.List.of());
        var value = new MeleeArcPayload(4,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),null,
                ResourceLocation.fromNamespaceAndPath("test","body_arc"),1,0,0,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,null,false,strike,"");
        assertEquals(value,roundTrip(value));
        for (String invalid : new String[]{null," ","\t","x".repeat(65)})
            assertThrows(IllegalArgumentException.class,()->new MeleeArcPayload(4,value.bodyUUID(),value.dimension(),null,value.arc(),1,0,0,0,
                    Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,null,false,strike,invalid));
        var copied=ItemPresentation.fromStack(new ItemStack(Items.STICK));
        assertThrows(IllegalArgumentException.class,()->new MeleeArcPayload(4,value.bodyUUID(),value.dimension(),null,value.arc(),1,0,0,0,
                Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,copied,false,strike,""));
    }
    @Test void codecRejectsInvalidSourceBoundsAndAllowsAbsentBounds() {
        var good=new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        var strike = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,1,0),new Vec3(0,0,1),0,
                1,MeleeAttackRequest.Mode.LIGHT,0,new Vec3(0,1,1),java.util.List.of(),good);
        var payload=new MeleeArcPayload(1,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("test","arc"),1,0,0,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,null,false,strike,"main_hand");
        assertEquals(good,roundTrip(payload).strikeGeometry().sourceBounds());
        var legacyStrike=new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,1,0),new Vec3(0,0,1),0,
                1,MeleeAttackRequest.Mode.LIGHT,0,new Vec3(0,1,1),java.util.List.of());
        var absent=new MeleeArcPayload(1,payload.bodyUUID(),payload.dimension(),payload.initiatingPlayerUUID(),payload.arc(),1,0,0,0,
                Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,null,false,legacyStrike,"main_hand");
        assertNull(roundTrip(absent).strikeGeometry().sourceBounds());
        for(double[] values:new double[][]{{Double.NaN,0,0,1,1,1},{0,0,0,0,1,1},{0,0,0,1,1,Double.POSITIVE_INFINITY}}) {
            var invalid=new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,1,0),new Vec3(0,0,1),0,
                    1,MeleeAttackRequest.Mode.LIGHT,0,new Vec3(0,1,1),java.util.List.of(),
                    new net.minecraft.world.phys.AABB(values[0],values[1],values[2],values[3],values[4],values[5]));
            assertThrows(IllegalArgumentException.class,()->new MeleeArcPayload(1,payload.bodyUUID(),payload.dimension(),payload.initiatingPlayerUUID(),payload.arc(),
                    1,0,0,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,null,false,invalid,"main_hand"));
        }
        var normalized=new net.minecraft.world.phys.AABB(1,1,1,0,0,0);
        assertEquals(new net.minecraft.world.phys.AABB(0,0,0,1,1,1),normalized);
    }
    private static MeleeArcPayload roundTrip(MeleeArcPayload value) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try { MeleeArcPayload.STREAM_CODEC.encode(buffer,value); return MeleeArcPayload.STREAM_CODEC.decode(buffer); }
        finally { buffer.release(); }
    }
}
