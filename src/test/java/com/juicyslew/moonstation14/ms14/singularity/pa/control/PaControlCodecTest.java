package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaControlCodecTest {
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    @Test void requestRoundTrips(){
        var value=new PaControlRequest(UUID.randomUUID(),42,8,PaControlRequest.Operation.STRENGTH,false,3);
        var buffer=buffer();PaControlRequest.STREAM_CODEC.encode(buffer,value);
        assertEquals(value,PaControlRequest.STREAM_CODEC.decode(buffer));
    }
    @Test void snapshotCarriesBoundTargetAndPartPresence(){
        var value=new PaControlSnapshot(UUID.randomUUID(),11,UUID.randomUUID(),UUID.randomUUID(),7,
                ResourceLocation.withDefaultNamespace("overworld"),1,2,3,ResourceLocation.withDefaultNamespace("pa"),9,
                5,2,false,false,true,500,499,java.util.List.of("power","port","fore"),new boolean[]{true,false,true},true,false);
        var buffer=buffer();PaControlSnapshot.STREAM_CODEC.encode(buffer,value);
        var decoded=PaControlSnapshot.STREAM_CODEC.decode(buffer);
        assertEquals(value.sessionId(),decoded.sessionId());assertEquals(value.machineId(),decoded.machineId());
        assertEquals(value.bodyId(),decoded.bodyId());assertEquals(value.authorityEpoch(),decoded.authorityEpoch());
        assertEquals(value.profileId(),decoded.profileId());assertEquals(value.revision(),decoded.revision());
        assertArrayEquals(value.present(),decoded.present());assertEquals(value.partRoles(),decoded.partRoles());assertEquals(value.accepted(),decoded.accepted());
    }
    @Test void strengthWireValuesAreTheExplicitSemanticLevels(){
        var values=com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.values();
        assertEquals(5,values.length);
        for(var value:values){
            int wire=value.level();
            assertEquals(value,java.util.Arrays.stream(values).filter(candidate->candidate.level()==wire).findFirst().orElseThrow());
            var request=new PaControlRequest(UUID.randomUUID(),1,1,PaControlRequest.Operation.STRENGTH,false,wire);
            var buffer=buffer();PaControlRequest.STREAM_CODEC.encode(buffer,request);
            assertEquals(request,PaControlRequest.STREAM_CODEC.decode(buffer));
        }
    }
}
