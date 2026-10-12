package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterProtocolTest {
 private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY); }
 @Test void requestRoundTripsAndRejectsNoncanonicalQuantity() {
  var request = new ChemMasterControlRequest(UUID.randomUUID(),UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),new BlockPos(1,2,3),4,5,6,ChemMasterControlRequest.Operation.INPUT_REAGENT,ChemMasterControlRequest.Mode.TRANSFER,ChemMasterControlRequest.Sort.OLDEST_FIRST,Optional.of(key("water")),500);
  var b=buffer(); ChemMasterWireCodec.writeRequest(b,request); assertEquals(request,ChemMasterWireCodec.readRequest(b));
  assertThrows(IllegalArgumentException.class,()->new ChemMasterControlRequest(request.sessionId(),request.machineId(),request.dimension(),request.position(),1,0,0,ChemMasterControlRequest.Operation.QUERY,request.mode(),request.sort(),Optional.empty(),-1));
 }
 @Test void snapshotRoundTripsAndChecksCapacityAndRows() {
  var key=key("water");
  var state=new ChemMasterWindowState(3,8,2,7,Optional.empty(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),2,ResourceLocation.withDefaultNamespace("overworld"),4,5,6,true,Optional.empty(),Optional.of(net.minecraft.network.chat.Component.literal("Beaker")),1000,250,List.of(new ChemMasterWindowState.ReagentRow(key,250)),List.of(new ChemMasterWindowState.ReagentRow(key,50)),ChemMasterControlRequest.Mode.TRANSFER,ChemMasterControlRequest.Sort.RECENT_FIRST);
  var b=buffer(); ChemMasterWireCodec.writeState(b,state); assertEquals(state,ChemMasterWireCodec.readState(b));
  assertThrows(IllegalArgumentException.class,()->new ChemMasterWindowState(0,0,0,-1,Optional.empty(),state.sessionId(),state.machineId(),state.bodyId(),0,state.dimension(),0,0,0,true,Optional.empty(),Optional.empty(),1,2,List.of(),List.of(),state.mode(),state.sort()));
 }
 private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> key(String path) { return net.minecraft.resources.ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,ResourceLocation.fromNamespaceAndPath("moonstation14",path)); }
}
