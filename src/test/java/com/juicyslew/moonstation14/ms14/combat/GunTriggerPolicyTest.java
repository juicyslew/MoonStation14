package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GunTriggerPolicyTest {
    private static final UUID BODY=UUID.randomUUID();
    @Test void fullAutoHoldReleaseAndNewerPress(){var p=new GunTriggerPolicy();assertTrue(p.admit(BODY,1,1,"right","a","FULL_AUTO",true));assertTrue(p.admit(BODY,1,1,"right","a","FULL_AUTO",false));assertTrue(p.stop(1,1,"right","a"));assertFalse(p.admit(BODY,1,1,"right","a","FULL_AUTO",false));assertTrue(p.admit(BODY,1,2,"right","a","FULL_AUTO",true));assertFalse(p.stop(1,1,"right","a"));assertTrue(p.admit(BODY,1,2,"right","a","FULL_AUTO",false));}
    @Test void semiAutoAllowsOneShot(){var p=new GunTriggerPolicy();assertTrue(p.admit(BODY,1,1,"right","a","SEMI_AUTO",true));assertFalse(p.admit(BODY,1,1,"right","a","SEMI_AUTO",false));assertFalse(p.admit(BODY,1,1,"right","a","SEMI_AUTO",true));}
    @Test void changedSourceOrModeNeedsPressAndEpochAllowsCounterRestart(){var p=new GunTriggerPolicy();assertTrue(p.admit(BODY,4,9,"right","a","FULL_AUTO",true));assertFalse(p.admit(BODY,4,9,"left","a","FULL_AUTO",false));assertFalse(p.admit(BODY,4,9,"right","a","SEMI_AUTO",false));assertTrue(p.admit(BODY,4,10,"right","a","SEMI_AUTO",true));assertTrue(p.admit(BODY,5,1,"right","a","FULL_AUTO",true));}
    @Test void requestAndStopCodecsRoundTrip(){var shot=new GunShotRequest(1,2,3,"right","a",new Vec3(1,2,3),8,false);var decoded=roundTrip(GunShotRequest.STREAM_CODEC,shot);assertEquals(shot.epoch(),decoded.epoch());assertEquals(shot.sequence(),decoded.sequence());assertEquals(shot.expectedHandRevision(),decoded.expectedHandRevision());assertEquals(shot.activeHand(),decoded.activeHand());assertEquals(shot.expectedItemToken(),decoded.expectedItemToken());assertEquals(shot.nominatedAimPoint().x,decoded.nominatedAimPoint().x);assertEquals(shot.nominatedAimPoint().y,decoded.nominatedAimPoint().y);assertEquals(shot.nominatedAimPoint().z,decoded.nominatedAimPoint().z);assertEquals(shot.triggerId(),decoded.triggerId());assertEquals(shot.start(),decoded.start());var stop=new GunStopShootRequest(1,8,"right","a");assertEquals(stop,roundTrip(GunStopShootRequest.STREAM_CODEC,stop));}
    private static <T>T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{codec.encode(b,value);return codec.decode(b);}finally{b.release();}}
}
