package com.juicyslew.moonstation14.ms14.stripping;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Intent only: all source identity and target ownership are captured/revalidated by the server. */
public record StripSlotAction(long epoch,long sequence,long actorRevision,UUID target,long targetRevision,
                              StripSlotRef.Kind kind,String slot) implements CustomPacketPayload {
    public static final Type<StripSlotAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","strip_slot_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,StripSlotAction> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeVarLong(p.epoch);b.writeVarLong(p.sequence);b.writeVarLong(p.actorRevision);b.writeUUID(p.target);b.writeVarLong(p.targetRevision);b.writeByte(p.kind.ordinal());b.writeUtf(p.slot,64);},
            b->{long e=b.readVarLong(),s=b.readVarLong(),ar=b.readVarLong();UUID t=b.readUUID();long r=b.readVarLong();int k=b.readUnsignedByte();if(k>=StripSlotRef.Kind.values().length)throw new IllegalArgumentException("Bad strip slot kind");var p=new StripSlotAction(e,s,ar,t,r,StripSlotRef.Kind.values()[k],b.readUtf(64));if(b.isReadable())throw new IllegalArgumentException("Trailing strip action");return p;});
    public StripSlotAction { if(epoch<=0||sequence<=0||actorRevision<0||target==null||targetRevision<0||kind==null||slot==null||slot.isBlank()||slot.length()>64)throw new IllegalArgumentException("Invalid strip action"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
