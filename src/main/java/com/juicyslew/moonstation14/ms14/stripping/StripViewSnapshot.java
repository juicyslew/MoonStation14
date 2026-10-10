package com.juicyslew.moonstation14.ms14.stripping;

import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Bounded authorized view. A hidden occupied slot intentionally carries no item presentation. */
public record StripViewSnapshot(long epoch,long sequence,UUID target,ResourceLocation dimension,int entityId,long targetRevision,
                                String templateId,List<Slot> slots,Status status,boolean canAct) implements CustomPacketPayload {
    public enum Status { AVAILABLE, UNAVAILABLE, CLOSED }
    public record Slot(StripSlotRef.Kind kind,String id,boolean occupied,boolean hidden,ItemPresentation item) {
        public static final StreamCodec<RegistryFriendlyByteBuf,Slot> CODEC=StreamCodec.of(
                (b,s)->{b.writeByte(s.kind.ordinal());b.writeUtf(s.id,64);b.writeBoolean(s.occupied);b.writeBoolean(s.hidden);b.writeBoolean(s.item!=null);if(s.item!=null)ItemPresentation.STREAM_CODEC.encode(b,s.item);},
                b->{int k=b.readUnsignedByte();if(k>=StripSlotRef.Kind.values().length)throw new IllegalArgumentException("Bad slot kind");String id=b.readUtf(64);boolean occupied=b.readBoolean(),hidden=b.readBoolean();ItemPresentation item=b.readBoolean()?ItemPresentation.STREAM_CODEC.decode(b):null;return new Slot(StripSlotRef.Kind.values()[k],id,occupied,hidden,item);});
        public Slot { if(kind==null||id==null||id.isBlank()||id.length()>64||item!=null&&(!occupied||hidden))throw new IllegalArgumentException("Invalid slot projection"); }
    }
    public static final Type<StripViewSnapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","strip_view_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,StripViewSnapshot> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeVarLong(p.epoch);b.writeVarLong(p.sequence);b.writeUUID(p.target);b.writeResourceLocation(p.dimension);b.writeVarInt(p.entityId);b.writeVarLong(p.targetRevision);b.writeBoolean(p.templateId!=null);if(p.templateId!=null)b.writeUtf(p.templateId,128);b.writeVarInt(p.slots.size());for(var s:p.slots)Slot.CODEC.encode(b,s);b.writeByte(p.status.ordinal());b.writeBoolean(p.canAct);},
            b->{long e=b.readVarLong(),q=b.readVarLong();UUID t=b.readUUID();ResourceLocation d=b.readResourceLocation();int id=b.readVarInt();long rev=b.readVarLong();String template=b.readBoolean()?b.readUtf(128):null;int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Strip view too large");List<Slot> slots=new ArrayList<>(n);for(int i=0;i<n;i++)slots.add(Slot.CODEC.decode(b));int status=b.readUnsignedByte();if(status>=Status.values().length)throw new IllegalArgumentException("Bad strip status");var p=new StripViewSnapshot(e,q,t,d,id,rev,template,slots,Status.values()[status],b.readBoolean());if(b.isReadable())throw new IllegalArgumentException("Trailing strip snapshot");return p;});
    public StripViewSnapshot { if(epoch<=0||sequence<=0||target==null||dimension==null||entityId<0||targetRevision<0||slots==null||slots.size()>64||status==null)throw new IllegalArgumentException("Invalid strip snapshot");slots=List.copyOf(slots); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
