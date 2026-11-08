package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAttackRequestTest {
    @Test void requestCodecRoundTripsKindsAndOptionalFields() {
        for (var kind:Kind.values()) { var value=new MeleeAttackRequest(2,3,kind,4,"main","",UUID.randomUUID()); roundTrip(MeleeAttackRequest.STREAM_CODEC,value); }
        roundTrip(MeleeAttackRequest.STREAM_CODEC,new MeleeAttackRequest(2,3,Kind.UNARMED,0,"body","",null));
        var receipt = new MeleeAttackRequest(2,9,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.LIGHT,List.of(),true);
        assertTrue(roundTrip(MeleeAttackRequest.STREAM_CODEC,receipt).swingPredicted());
        assertFalse(roundTrip(MeleeAttackRequest.STREAM_CODEC,receipt.withSwingPredicted(false)).swingPredicted());
    }
    @Test void replyCodecRoundTripsKindsAndOptionalFields() {
        for (var kind:Kind.values()) roundTrip(MeleeAttackReply.STREAM_CODEC,new MeleeAttackReply(UUID.randomUUID(),2,3,MeleeAttackReply.Status.HIT,true,kind,4,"item",7));
        roundTrip(MeleeAttackReply.STREAM_CODEC,new MeleeAttackReply(null,2,3,MeleeAttackReply.Status.REJECTED,false,null,0,null,0));
    }
    @Test void requestCodecRoundTripsLightAndWideNominations() {
        UUID light=UUID.randomUUID(); roundTrip(MeleeAttackRequest.STREAM_CODEC,new MeleeAttackRequest(2,3,Kind.UNARMED,0,"body","",light));
        var ids=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        roundTrip(MeleeAttackRequest.STREAM_CODEC,new MeleeAttackRequest(2,4,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,null,ids));
        roundTrip(MeleeAttackRequest.STREAM_CODEC,new MeleeAttackRequest(2,8,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.DISARM,light,List.of()));
        roundTrip(MeleeAttackRequest.STREAM_CODEC,new MeleeAttackRequest(2,5,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,null,List.of()));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,light,List.of()));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.LIGHT,null,List.of(light)));
        var mutable=new ArrayList<>(ids); var copied=new MeleeAttackRequest(2,6,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,null,mutable); mutable.clear();
        assertEquals(ids,copied.wideTargets()); assertThrows(UnsupportedOperationException.class,()->copied.wideTargets().clear());
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { buffer.writeVarLong(2);buffer.writeVarLong(7);buffer.writeVarInt(Kind.UNARMED.ordinal());buffer.writeVarLong(0);buffer.writeUtf("body",32);buffer.writeUtf("",128);buffer.writeVarInt(MeleeAttackRequest.Mode.WIDE.ordinal());buffer.writeVarInt(6);assertThrows(IllegalArgumentException.class,()->MeleeAttackRequest.STREAM_CODEC.decode(buffer)); } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,null,List.of(light,light,light,light,light,light)));
    }
    @Test void typedBlockNominationsRoundTripAndValidate() {
        var block=new MeleeTargetNomination.Block(new net.minecraft.core.BlockPos(2,3,4),net.minecraft.core.Direction.UP,new net.minecraft.world.phys.Vec3(2.5,4,4.5),12);
        var light=new MeleeAttackRequest(2,3,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.LIGHT,List.of(block),false);
        assertEquals(light,roundTrip(MeleeAttackRequest.STREAM_CODEC,light));
        var wide=new MeleeAttackRequest(2,4,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,List.of(block,new MeleeTargetNomination.Entity(UUID.randomUUID())),false);
        assertEquals(wide,roundTrip(MeleeAttackRequest.STREAM_CODEC,wide));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.DISARM,List.of(block),false));
        assertThrows(IllegalArgumentException.class,()->new MeleeTargetNomination.Block(new net.minecraft.core.BlockPos(0,0,0),net.minecraft.core.Direction.UP,new net.minecraft.world.phys.Vec3(Double.NaN,0,0),0));
        assertThrows(IllegalArgumentException.class,()->new MeleeTargetNomination.Block(new net.minecraft.core.BlockPos(0,0,0),net.minecraft.core.Direction.UP,new net.minecraft.world.phys.Vec3(0,0,0),-1));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.DISARM,List.of(block),false));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,null,null));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.LIGHT,UUID.randomUUID(),List.of(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,()->new MeleeAttackRequest(2,1,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.WIDE,UUID.randomUUID(),List.of()));
    }
    @Test void blockHitFactoryUsesCanonicalDistinctStateIdsAndNominationRoundTrips() {
        var x=net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X);
        var y=net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.Y);
        assertEquals(x.getBlock(),y.getBlock());
        assertNotEquals(net.minecraft.world.level.block.Block.getId(x),net.minecraft.world.level.block.Block.getId(y));
        assertNotEquals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(x.getBlock()),net.minecraft.world.level.block.Block.getId(x));
        var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(2.5,3,4.5),net.minecraft.core.Direction.UP,new net.minecraft.core.BlockPos(2,3,4),false);
        var nomination=MeleeTargetNomination.Block.fromHit(hit,x);
        assertEquals(net.minecraft.world.level.block.Block.getId(x),nomination.expectedBlockStateID());
        assertNotEquals(MeleeTargetNomination.Block.fromHit(hit,y).expectedBlockStateID(),nomination.expectedBlockStateID());
        var request=new MeleeAttackRequest(2,3,Kind.UNARMED,0,"body","",MeleeAttackRequest.Mode.LIGHT,List.of(nomination),false);
        assertEquals(request,roundTrip(MeleeAttackRequest.STREAM_CODEC,request));
        assertThrows(IllegalArgumentException.class,()->MeleeTargetNomination.Block.fromHit(net.minecraft.world.phys.BlockHitResult.miss(net.minecraft.world.phys.Vec3.ZERO,net.minecraft.core.Direction.UP,net.minecraft.core.BlockPos.ZERO),x));
    }
    @Test void durationProjectionIsClockIndependentAndOverflowSafe() {
        assertEquals(7,MeleeAttackRequestService.remainingTicks(100007,100000));
        assertEquals(0,MeleeAttackRequestService.remainingTicks(100000,100000));
        assertEquals(Long.MAX_VALUE,MeleeAttackRequestService.remainingTicks(Long.MAX_VALUE,Long.MIN_VALUE));
    }
    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,T> codec,T value) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { codec.encode(buffer,value); T decoded=codec.decode(buffer); assertEquals(value,decoded); return decoded; } finally { buffer.release(); }
    }
}
