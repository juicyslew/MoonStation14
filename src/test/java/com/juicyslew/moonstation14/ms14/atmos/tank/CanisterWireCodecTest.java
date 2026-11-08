package com.juicyslew.moonstation14.ms14.atmos.tank;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CanisterWireCodecTest {
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)); }
    @Test void openAndClosePayloadsRoundTrip() {
        var open=new CanisterOpenRequest(UUID.randomUUID()); var b=buffer(); CanisterOpenRequest.STREAM_CODEC.encode(b,open); assertEquals(open,CanisterOpenRequest.STREAM_CODEC.decode(b));
        var close=new CanisterCloseRequest(UUID.randomUUID()); b=buffer(); CanisterCloseRequest.STREAM_CODEC.encode(b,close); assertEquals(close,CanisterCloseRequest.STREAM_CODEC.decode(b));
    }
    @Test void controlPreservesObservedHandRevisionAndToken() {
        var r=new CanisterControlRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),7,ResourceLocation.withDefaultNamespace("overworld"),1,2,3,4,5,6,7,89,"right","observed-token",CanisterControlRequest.Operation.INSERT_ACTIVE,false,0);
        var b=buffer(); CanisterControlRequest.STREAM_CODEC.encode(b,r); assertEquals(r,CanisterControlRequest.STREAM_CODEC.decode(b));
    }
    @Test void snapshotRoundTripsPressureGasAndDefensiveTankStack() {
        var stack=new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Finite O2 tank")); var snapshot=new CanisterSessionSnapshot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),3,ResourceLocation.withDefaultNamespace("overworld"),1,2,3,4,5,6,101.25,10,500,true,Map.of("oxygen",2.5,"nitrogen",1.25),293.15,stack,true);
        var b=buffer(); CanisterSnapshotPayload.STREAM_CODEC.encode(b,new CanisterSnapshotPayload(snapshot)); var decoded=CanisterSnapshotPayload.STREAM_CODEC.decode(b).snapshot();
        assertEquals(snapshot.pressureKpa(),decoded.pressureKpa()); assertEquals(snapshot.gasMoles(),decoded.gasMoles()); assertEquals("Finite O2 tank",decoded.activeTank().getHoverName().getString());
        var exposed=decoded.activeTank(); exposed.setCount(8); assertEquals(1,decoded.activeTank().getCount());
    }
}
