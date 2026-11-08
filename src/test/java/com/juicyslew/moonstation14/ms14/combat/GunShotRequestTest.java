package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionSequenceGate;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.Unpooled;
import java.util.UUID;

class GunShotRequestTest {
    @Test void requestCarriesOnlyBodyBindingAndFiniteAim() {
        var request=new GunShotRequest(9,3,12,"right","owned-token",new Vec3(1,2,3));
        assertEquals(9,request.epoch());assertEquals(3,request.sequence());assertEquals(12,request.expectedHandRevision());
        assertEquals("right",request.activeHand());assertEquals("owned-token",request.expectedItemToken());assertEquals(new Vec3(1,2,3),request.nominatedAimPoint());
        assertThrows(IllegalArgumentException.class,()->new GunShotRequest(9,3,12,"right","owned-token",new Vec3(Double.NaN,0,0)));
    }
    @Test void requestAndReplyUseCompleteStreamCodecRoundTrips() {
        var request=new GunShotRequest(19,7,31,"left","token-17",new Vec3(2.5,-3,8));
        assertEquals(request,roundTrip(GunShotRequest.STREAM_CODEC,request));
        var reply=new GunShotReply(UUID.randomUUID(),19,7,GunFireSystem.Status.CONSUMED_CANCELLED,true,31,"token-17",43);
        assertEquals(reply,roundTrip(GunShotReply.STREAM_CODEC,reply));
        var noSelection=new GunShotReply(null,0,0,GunFireSystem.Status.REJECTED,false,0,null,0);
        assertEquals(noSelection,roundTrip(GunShotReply.STREAM_CODEC,noSelection));
    }
    @Test void sequenceAdmissionRejectsEpochReplayAndRateLimit() {
        var gate=new BodyActionSequenceGate();
        assertEquals(BodyActionSequenceGate.Result.WRONG_EPOCH,gate.admit(4,3,1,1));
        assertEquals(BodyActionSequenceGate.Result.ADMITTED,gate.admit(4,4,1,1));
        assertEquals(BodyActionSequenceGate.Result.REPLAY,gate.admit(4,4,1,2));
        assertEquals(BodyActionSequenceGate.Result.ADMITTED,gate.admit(4,4,2,3));
        for(long i=3;i<=8;i++)assertEquals(BodyActionSequenceGate.Result.ADMITTED,gate.admit(4,4,i,3));
        assertEquals(BodyActionSequenceGate.Result.ADMITTED,gate.admit(4,4,9,3));
        assertEquals(BodyActionSequenceGate.Result.RATE_LIMITED,gate.admit(4,4,10,3));
    }
    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {codec.encode(buffer,value);var decoded=codec.decode(buffer);assertEquals(0,buffer.readableBytes());return decoded;}finally{buffer.release();}
    }
}
