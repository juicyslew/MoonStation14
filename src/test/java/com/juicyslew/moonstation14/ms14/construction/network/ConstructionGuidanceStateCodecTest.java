package com.juicyslew.moonstation14.ms14.construction.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionGuidanceStateCodecTest {
    private static ConstructionGuidanceState roundTrip(ConstructionGuidanceState state) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        ConstructionGuidanceState.STREAM_CODEC.encode(buffer,state);
        return ConstructionGuidanceState.STREAM_CODEC.decode(buffer);
    }
    @Test void packetRoundTripCarriesBothContentFieldsAndOptionalHost() {
        var body=UUID.randomUUID();var host=UUID.randomUUID();var target=new BlockPos(1,2,3);
        var populated=new ConstructionGuidanceState(4,7,body,target,host,"Steel Wall","2 Steel");
        assertEquals(populated,roundTrip(populated));
        var empty=new ConstructionGuidanceState(4,8,body,target,null,"","");
        assertEquals(empty,roundTrip(empty));
    }
    @Test void packetValidatesIdentityAndContentBounds() {
        assertThrows(IllegalArgumentException.class,()->new ConstructionGuidanceState(0,1,UUID.randomUUID(),BlockPos.ZERO,null,"", ""));
        assertThrows(IllegalArgumentException.class,()->new ConstructionGuidanceState(1,0,UUID.randomUUID(),BlockPos.ZERO,null,"", ""));
        assertThrows(IllegalArgumentException.class,()->new ConstructionGuidanceState(1,1,null,BlockPos.ZERO,null,"", ""));
        assertThrows(IllegalArgumentException.class,()->new ConstructionGuidanceState(1,1,UUID.randomUUID(),null,null,"", ""));
    }
}
