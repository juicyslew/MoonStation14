package com.juicyslew.moonstation14.ms14.disposal.ui;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalConstructionPayloadTest {
    @Test void requestAndStateCodecsRoundTrip(){
        var request=new DisposalConstructionRequest(UUID.randomUUID(),7,2,DisposalConstructionRequest.Action.SELECT,"moonstation14:disposal_pipe");
        assertEquals(request,roundTrip(DisposalConstructionRequest.STREAM_CODEC,request));
        var state=new DisposalConstructionState(request.bodyUUID(),7,2,true,request.prototype(),1,5);
        assertEquals(state,roundTrip(DisposalConstructionState.STREAM_CODEC,state));
    }
    @Test void requestAndSnapshotRejectInvalidCorrelationOrState(){
        assertThrows(IllegalArgumentException.class,()->new DisposalConstructionRequest(UUID.randomUUID(),0,1,DisposalConstructionRequest.Action.ENTER,""));
        assertThrows(IllegalArgumentException.class,()->new DisposalConstructionRequest(UUID.randomUUID(),1,1,DisposalConstructionRequest.Action.ENTER,"moonstation14:disposal_pipe"));
        assertThrows(IllegalArgumentException.class,()->new DisposalConstructionState(UUID.randomUUID(),1,0,false,"moonstation14:disposal_pipe",0,0));
    }
    private static <T>T roundTrip(StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{codec.encode(buf,value);return codec.decode(buf);}finally{buf.release();}
    }
}
