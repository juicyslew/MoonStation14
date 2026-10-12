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
 private static final int MAX_ROWS = 256, MAX_REASON = 128, MAX_PILLS = 32;
 private ChemMasterWireCodec() {}
 static void writeRequest(RegistryFriendlyByteBuf b, ChemMasterControlRequest r) {
  b.writeUUID(r.sessionId()); b.writeUUID(r.machineId()); b.writeResourceLocation(r.dimension()); BlockPos.STREAM_CODEC.encode(b,r.position());
  b.writeVarLong(r.requestId()); b.writeVarLong(r.expectedStateRevision()); b.writeVarLong(r.expectedSlotRevision()); b.writeVarInt(r.operation().ordinal()); b.writeVarInt(r.mode().ordinal()); b.writeVarInt(r.sort().ordinal());
  b.writeBoolean(r.reagent().isPresent()); r.reagent().ifPresent(k->b.writeResourceLocation(k.location())); b.writeVarLong(r.amountCents());
  b.writeVarLong(r.expectedOutputSlotRevision()); b.writeVarLong(r.expectedBufferRevision());
  b.writeBoolean(r.drawSource().isPresent()); r.drawSource().ifPresent(v->b.writeVarInt(v.ordinal())); b.writeVarInt(r.pillType());
  b.writeBoolean(r.outputRequest().isPresent()); r.outputRequest().ifPresent(v->{b.writeVarInt(v.count());b.writeVarInt(v.doseUnits());b.writeUtf(v.label(),ChemMasterPackagingPlan.MAX_LABEL_LENGTH);});
 }
 static ChemMasterControlRequest readRequest(RegistryFriendlyByteBuf b) {
  UUID session=b.readUUID(), machine=b.readUUID(); ResourceLocation dim=b.readResourceLocation(); BlockPos pos=BlockPos.STREAM_CODEC.decode(b);
  long id=b.readVarLong(), state=b.readVarLong(), slot=b.readVarLong(); var op=enumAt(ChemMasterControlRequest.Operation.values(),b.readVarInt()); var mode=enumAt(ChemMasterControlRequest.Mode.values(),b.readVarInt()); var sort=enumAt(ChemMasterControlRequest.Sort.values(),b.readVarInt());
  Optional<ResourceKey<ReagentData>> key=b.readBoolean()?Optional.of(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,b.readResourceLocation())):Optional.empty();
  long amount=b.readVarLong(), outputRevision=b.readVarLong(), bufferRevision=b.readVarLong();
  Optional<ChemMasterControlRequest.DrawSource> source=b.readBoolean()?Optional.of(enumAt(ChemMasterControlRequest.DrawSource.values(),b.readVarInt())):Optional.empty();
  int pillType=b.readVarInt(); Optional<ChemMasterControlRequest.OutputRequest> output=b.readBoolean()
          ?Optional.of(new ChemMasterControlRequest.OutputRequest(b.readVarInt(),b.readVarInt(),b.readUtf(ChemMasterPackagingPlan.MAX_LABEL_LENGTH))):Optional.empty();
  return new ChemMasterControlRequest(session,machine,dim,pos,id,state,slot,op,mode,sort,key,amount,outputRevision,bufferRevision,source,pillType,output);
 }
 static void writeState(RegistryFriendlyByteBuf b, ChemMasterWindowState s) {
  b.writeVarLong(s.frame()); b.writeVarLong(s.stateRevision()); b.writeVarLong(s.slotRevision()); b.writeBoolean(s.acknowledgedRequestId() >= 0); if(s.acknowledgedRequestId() >= 0) b.writeVarLong(s.acknowledgedRequestId()); b.writeBoolean(s.rejectionReason().isPresent()); s.rejectionReason().ifPresent(v->b.writeUtf(v,MAX_REASON)); b.writeUUID(s.sessionId()); b.writeUUID(s.machineId()); b.writeUUID(s.bodyId()); b.writeVarLong(s.bodyEpoch()); b.writeResourceLocation(s.dimension()); b.writeBlockPos(new BlockPos(s.x(),s.y(),s.z()));
  b.writeBoolean(s.open()); b.writeBoolean(s.closeReason().isPresent()); s.closeReason().ifPresent(v->b.writeUtf(v,MAX_REASON)); b.writeBoolean(s.loadedItemName().isPresent()); s.loadedItemName().ifPresent(v->ComponentSerialization.STREAM_CODEC.encode(b,v));
   b.writeVarLong(s.bufferRevision()); b.writeVarLong(s.inputCapacityCents()); b.writeVarLong(s.inputVolumeCents()); writeRows(b,s.inputReagents()); writeRows(b,s.bufferReagents()); b.writeVarInt(s.mode().ordinal()); b.writeVarInt(s.sort().ordinal());
   writeOutput(b,s.outputSlot()); b.writeVarInt(s.drawSource().ordinal()); b.writeVarInt(s.pillType()); b.writeVarInt(s.pillDosageLimit()); b.writeBoolean(s.updateLabel());
 }
 static ChemMasterWindowState readState(RegistryFriendlyByteBuf b) {
  long frame=b.readVarLong(), rev=b.readVarLong(), slot=b.readVarLong(), ack=b.readBoolean()?b.readVarLong():-1; Optional<String> rejection=b.readBoolean()?Optional.of(b.readUtf(MAX_REASON)):Optional.empty(); UUID session=b.readUUID(), machine=b.readUUID(), body=b.readUUID(); long epoch=b.readVarLong(); ResourceLocation dim=b.readResourceLocation(); BlockPos pos=b.readBlockPos(); boolean open=b.readBoolean(); Optional<String> reason=b.readBoolean()?Optional.of(b.readUtf(MAX_REASON)):Optional.empty(); Optional<Component> name=b.readBoolean()?Optional.of(ComponentSerialization.STREAM_CODEC.decode(b)):Optional.empty();
   long bufferRevision=b.readVarLong(), cap=b.readVarLong(), volume=b.readVarLong(); var input=readRows(b); var buffer=readRows(b); var mode=enumAt(ChemMasterControlRequest.Mode.values(),b.readVarInt()); var sort=enumAt(ChemMasterControlRequest.Sort.values(),b.readVarInt());
   var output=readOutput(b); var source=enumAt(ChemMasterControlRequest.DrawSource.values(),b.readVarInt()); int type=b.readVarInt(), doseLimit=b.readVarInt(); boolean updateLabel=b.readBoolean();
   return new ChemMasterWindowState(frame,rev,slot,bufferRevision,ack,rejection,session,machine,body,epoch,dim,pos.getX(),pos.getY(),pos.getZ(),open,reason,name,cap,volume,input,buffer,mode,sort,output,source,type,doseLimit,updateLabel);
  }
  private static void writeOutput(RegistryFriendlyByteBuf b,ChemMasterWindowState.OutputSlot s){
   b.writeVarLong(s.revision());b.writeVarInt(s.kind().ordinal());b.writeBoolean(s.itemName().isPresent());s.itemName().ifPresent(v->ComponentSerialization.STREAM_CODEC.encode(b,v));
   b.writeVarLong(s.capacityCents());b.writeVarLong(s.volumeCents());writeRows(b,s.reagents());b.writeVarInt(s.width());b.writeVarInt(s.height());b.writeVarInt(s.freeCells());
   b.writeBoolean(s.label().isPresent());s.label().ifPresent(v->b.writeUtf(v,ChemMasterPackagingPlan.MAX_LABEL_LENGTH));
   if(s.pills().size()>MAX_PILLS)throw new IllegalArgumentException("too many pill descriptors");b.writeVarInt(s.pills().size());
   for(var p:s.pills()){b.writeVarInt(p.x());b.writeVarInt(p.y());b.writeVarInt(p.type());b.writeBoolean(p.label().isPresent());p.label().ifPresent(v->b.writeUtf(v,ChemMasterPackagingPlan.MAX_LABEL_LENGTH));writeRows(b,p.reagents());}
  }
  private static ChemMasterWindowState.OutputSlot readOutput(RegistryFriendlyByteBuf b){
   long revision=b.readVarLong();var kind=enumAt(ChemMasterWindowState.OutputKind.values(),b.readVarInt());Optional<Component> name=b.readBoolean()?Optional.of(ComponentSerialization.STREAM_CODEC.decode(b)):Optional.empty();
   long cap=ReagentUnits.validateCents(b.readVarLong(),"outputCapacity"),volume=ReagentUnits.validateCents(b.readVarLong(),"outputVolume");var reagents=readRows(b);
   int width=b.readVarInt(),height=b.readVarInt(),free=b.readVarInt();Optional<String> label=b.readBoolean()?Optional.of(b.readUtf(ChemMasterPackagingPlan.MAX_LABEL_LENGTH)):Optional.empty();
   int n=b.readVarInt();if(n<0||n>MAX_PILLS)throw new IllegalArgumentException("invalid pill descriptor count");List<ChemMasterWindowState.PillDescriptor> pills=new ArrayList<>(n);
   for(int i=0;i<n;i++){int x=b.readVarInt(),y=b.readVarInt(),type=b.readVarInt();Optional<String> pillLabel=b.readBoolean()?Optional.of(b.readUtf(ChemMasterPackagingPlan.MAX_LABEL_LENGTH)):Optional.empty();pills.add(new ChemMasterWindowState.PillDescriptor(x,y,type,pillLabel,readRows(b)));}
   return new ChemMasterWindowState.OutputSlot(revision,kind,name,cap,volume,reagents,width,height,free,label,pills);
  }
 private static void writeRows(RegistryFriendlyByteBuf b,List<ChemMasterWindowState.ReagentRow> rows) { if(rows.size()>MAX_ROWS) throw new IllegalArgumentException("too many reagent rows"); b.writeVarInt(rows.size()); for(var r:rows){b.writeResourceLocation(r.reagent().location()); b.writeVarLong(r.amountCents());} }
 private static List<ChemMasterWindowState.ReagentRow> readRows(RegistryFriendlyByteBuf b) { int n=b.readVarInt(); if(n<0||n>MAX_ROWS) throw new IllegalArgumentException("invalid reagent row count"); List<ChemMasterWindowState.ReagentRow> rows=new ArrayList<>(n); for(int i=0;i<n;i++) rows.add(new ChemMasterWindowState.ReagentRow(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,b.readResourceLocation()),ReagentUnits.validateCents(b.readVarLong(),"amountCents"))); return rows; }
 private static <T> T enumAt(T[] values,int index) { if(index<0||index>=values.length) throw new IllegalArgumentException("invalid ChemMaster enum"); return values[index]; }
}
