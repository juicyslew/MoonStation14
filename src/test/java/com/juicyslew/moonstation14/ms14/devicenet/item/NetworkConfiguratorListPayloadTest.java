package com.juicyslew.moonstation14.ms14.devicenet.item;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkConfiguratorListPayloadTest {
    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);codec.encode(buffer,value);return codec.decode(buffer);
    }
    @Test void listRequestAndSnapshotCodecsRoundTrip(){
        var id=UUID.randomUUID();
        var request=new NetworkConfiguratorListRequest(id,4,2,8,"main","proof",NetworkConfiguratorListRequest.Operation.ADD,List.of("device:a"),"device:b");
        assertEquals(request,roundTrip(NetworkConfiguratorPayloads.ListRequest.STREAM_CODEC,new NetworkConfiguratorPayloads.ListRequest(request)).value());
        var display=new NetworkConfiguratorStackData.StagedDevice(UUID.randomUUID(),"device:b","device.name");
        var snapshot=new NetworkConfiguratorListSnapshot(id,4,8,2,true,true,"device:a",List.of("device:b"),List.of(new NetworkConfiguratorStackData.StagedDevice(UUID.randomUUID(),"device:c","Device C")),List.of(display),List.of());
        assertEquals(snapshot,roundTrip(NetworkConfiguratorPayloads.ListSnapshot.STREAM_CODEC,new NetworkConfiguratorPayloads.ListSnapshot(snapshot)).value());
    }
}
