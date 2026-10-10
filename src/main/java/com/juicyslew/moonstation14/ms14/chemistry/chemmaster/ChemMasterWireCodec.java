package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Bounded codec shared by the request and snapshot DTOs. */
final class ChemMasterWireCodec {
 private static final int MAX_ROWS = 256, MAX_REASON = 128;
 private ChemMasterWireCodec() {}
 static void writeRequest(RegistryFriendlyByteBuf b, ChemMasterControlRequest r) {
  b.writeUUID(r.sessionId()); b.writeUUID(r.machineId()); b.writeResourceLocation(r.dimension()); BlockPos.STREAM_CODEC.encode(b,r.position());
  b.writeVarLong(r.requestId()); b.writeVarLong(r.expectedStateRevision()); b.writeVarLong(r.expectedSlotRevision()); b.writeVarInt(r.operation().ordinal()); b.writeVarInt(r.mode().ordinal()); b.writeVarInt(r.sort().ordinal());
  b.writeBoolean(r.reagent().isPresent()); r.reagent().ifPresent(k->b.writeResourceLocation(k.location())); b.writeVarLong(r.amountCents());
 }
 static ChemMasterControlRequest readRequest(RegistryFriendlyByteBuf b) {
  UUID session=b.readUUID(), machine=b.readUUID(); ResourceLocation dim=b.readResourceLocation(); BlockPos pos=BlockPos.STREAM_CODEC.decode(b);
  long id=b.readVarLong(), state=b.readVarLong(), slot=b.readVarLong(); var op=enumAt(ChemMasterControlRequest.Operation.values(),b.readVarInt()); var mode=enumAt(ChemMasterControlRequest.Mode.values(),b.readVarInt()); var sort=enumAt(ChemMasterControlRequest.Sort.values(),b.readVarInt());
  Optional<ResourceKey<ReagentData>> key=b.readBoolean()?Optional.of(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,b.readResourceLocation())):Optional.empty();
  return new ChemMasterControlRequest(session,machine,dim,pos,id,state,slot,op,mode,sort,key,b.readVarLong());
 }
 static void writeState(RegistryFriendlyByteBuf b, ChemMasterWindowState s) {
  b.writeVarLong(s.frame()); b.writeVarLong(s.stateRevision()); b.writeVarLong(s.slotRevision()); b.writeBoolean(s.acknowledgedRequestId() >= 0); if(s.acknowledgedRequestId() >= 0) b.writeVarLong(s.acknowledgedRequestId()); b.writeBoolean(s.rejectionReason().isPresent()); s.rejectionReason().ifPresent(v->b.writeUtf(v,MAX_REASON)); b.writeUUID(s.sessionId()); b.writeUUID(s.machineId()); b.writeUUID(s.bodyId()); b.writeVarLong(s.bodyEpoch()); b.writeResourceLocation(s.dimension()); b.writeBlockPos(new BlockPos(s.x(),s.y(),s.z()));
  b.writeBoolean(s.open()); b.writeBoolean(s.closeReason().isPresent()); s.closeReason().ifPresent(v->b.writeUtf(v,MAX_REASON)); b.writeBoolean(s.loadedItemName().isPresent()); s.loadedItemName().ifPresent(v->ComponentSerialization.STREAM_CODEC.encode(b,v));
  b.writeVarLong(s.inputCapacityCents()); b.writeVarLong(s.inputVolumeCents()); writeRows(b,s.inputReagents()); writeRows(b,s.bufferReagents()); b.writeVarInt(s.mode().ordinal()); b.writeVarInt(s.sort().ordinal());
 }
 static ChemMasterWindowState readState(RegistryFriendlyByteBuf b) {
  long frame=b.readVarLong(), rev=b.readVarLong(), slot=b.readVarLong(), ack=b.readBoolean()?b.readVarLong():-1; Optional<String> rejection=b.readBoolean()?Optional.of(b.readUtf(MAX_REASON)):Optional.empty(); UUID session=b.readUUID(), machine=b.readUUID(), body=b.readUUID(); long epoch=b.readVarLong(); ResourceLocation dim=b.readResourceLocation(); BlockPos pos=b.readBlockPos(); boolean open=b.readBoolean(); Optional<String> reason=b.readBoolean()?Optional.of(b.readUtf(MAX_REASON)):Optional.empty(); Optional<Component> name=b.readBoolean()?Optional.of(ComponentSerialization.STREAM_CODEC.decode(b)):Optional.empty();
  long cap=b.readVarLong(), volume=b.readVarLong(); var input=readRows(b); var buffer=readRows(b); var mode=enumAt(ChemMasterControlRequest.Mode.values(),b.readVarInt()); var sort=enumAt(ChemMasterControlRequest.Sort.values(),b.readVarInt());
  return new ChemMasterWindowState(frame,rev,slot,ack,rejection,session,machine,body,epoch,dim,pos.getX(),pos.getY(),pos.getZ(),open,reason,name,cap,volume,input,buffer,mode,sort);
 }
 private static void writeRows(RegistryFriendlyByteBuf b,List<ChemMasterWindowState.ReagentRow> rows) { if(rows.size()>MAX_ROWS) throw new IllegalArgumentException("too many reagent rows"); b.writeVarInt(rows.size()); for(var r:rows){b.writeResourceLocation(r.reagent().location()); b.writeVarLong(r.amountCents());} }
 private static List<ChemMasterWindowState.ReagentRow> readRows(RegistryFriendlyByteBuf b) { int n=b.readVarInt(); if(n<0||n>MAX_ROWS) throw new IllegalArgumentException("invalid reagent row count"); List<ChemMasterWindowState.ReagentRow> rows=new ArrayList<>(n); for(int i=0;i<n;i++) rows.add(new ChemMasterWindowState.ReagentRow(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,b.readResourceLocation()),ReagentUnits.validateCents(b.readVarLong(),"amountCents"))); return rows; }
 private static <T> T enumAt(T[] values,int index) { if(index<0||index>=values.length) throw new IllegalArgumentException("invalid ChemMaster enum"); return values[index]; }
}
