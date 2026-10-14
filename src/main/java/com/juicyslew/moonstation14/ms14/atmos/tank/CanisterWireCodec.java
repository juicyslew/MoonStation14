package com.juicyslew.moonstation14.ms14.atmos.tank;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

final class CanisterWireCodec {
    private static final int MAX_GASES = 64;
    private CanisterWireCodec() { }
    static void writeRequest(RegistryFriendlyByteBuf b, CanisterControlRequest r) {
        b.writeUUID(r.sessionId()); b.writeUUID(r.machineId()); b.writeUUID(r.bodyId()); b.writeVarLong(r.authorityEpoch()); b.writeResourceLocation(r.dimension());
        b.writeDouble(r.x()); b.writeDouble(r.y()); b.writeDouble(r.z()); b.writeVarLong(r.sequence()); b.writeVarLong(r.stateRevision()); b.writeVarLong(r.slotRevision()); b.writeVarLong(r.ledgerRevision()); b.writeVarLong(r.handRevision());
        b.writeBoolean(r.handId()!=null); if(r.handId()!=null)b.writeUtf(r.handId(),64); b.writeBoolean(r.handToken()!=null); if(r.handToken()!=null)b.writeUtf(r.handToken(),128);
        b.writeVarInt(r.operation().ordinal()); b.writeBoolean(r.valveClosed()); b.writeDouble(r.pressureKpa());
    }
    static CanisterControlRequest readRequest(RegistryFriendlyByteBuf b) {
        var session=b.readUUID(); var machine=b.readUUID(); var body=b.readUUID(); long epoch=b.readVarLong(); var dim=b.readResourceLocation(); double x=b.readDouble(),y=b.readDouble(),z=b.readDouble(); long seq=b.readVarLong(),state=b.readVarLong(),slot=b.readVarLong(),ledger=b.readVarLong(),handRev=b.readVarLong();
        String hand=b.readBoolean()?b.readUtf(64):null,token=b.readBoolean()?b.readUtf(128):null; int op=b.readVarInt(); if(op<0||op>=CanisterControlRequest.Operation.values().length)throw new IllegalArgumentException("invalid canister operation");
        return new CanisterControlRequest(session,machine,body,epoch,dim,x,y,z,seq,state,slot,ledger,handRev,hand,token,CanisterControlRequest.Operation.values()[op],b.readBoolean(),b.readDouble());
    }
    static void writeSnapshot(RegistryFriendlyByteBuf b, CanisterSessionSnapshot s) {
        b.writeUUID(s.sessionId());b.writeUUID(s.machineId());b.writeUUID(s.bodyId());b.writeVarLong(s.authorityEpoch());b.writeResourceLocation(s.dimension());b.writeDouble(s.x());b.writeDouble(s.y());b.writeDouble(s.z());
        b.writeVarLong(s.stateRevision());b.writeVarLong(s.slotRevision());b.writeVarLong(s.ledgerRevision());b.writeDouble(s.pressureKpa());b.writeDouble(s.minimumPressureKpa());b.writeDouble(s.maximumPressureKpa());b.writeBoolean(s.valveClosed());
        if(s.gasMoles().size()>MAX_GASES)throw new IllegalArgumentException("too many gas species"); b.writeVarInt(s.gasMoles().size());s.gasMoles().forEach((k,v)->{b.writeUtf(k,128);b.writeDouble(v);});b.writeDouble(s.temperatureKelvin());ItemStack.OPTIONAL_STREAM_CODEC.encode(b,s.activeTank());b.writeBoolean(s.accepted());
    }
    static CanisterSessionSnapshot readSnapshot(RegistryFriendlyByteBuf b) {
        var session=b.readUUID();var machine=b.readUUID();var body=b.readUUID();long epoch=b.readVarLong();ResourceLocation dim=b.readResourceLocation();double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();long state=b.readVarLong(),slot=b.readVarLong(),ledger=b.readVarLong();double pressure=b.readDouble(),min=b.readDouble(),max=b.readDouble();boolean valve=b.readBoolean();
        int n=b.readVarInt();if(n<0||n>MAX_GASES)throw new IllegalArgumentException("invalid gas count");Map<String,Double> gas=new LinkedHashMap<>();for(int i=0;i<n;i++)if(gas.put(b.readUtf(128),b.readDouble())!=null)throw new IllegalArgumentException("duplicate gas");double temp=b.readDouble();ItemStack item=ItemStack.OPTIONAL_STREAM_CODEC.decode(b);boolean accepted=b.readBoolean();
        return new CanisterSessionSnapshot(session,machine,body,epoch,dim,x,y,z,state,slot,ledger,pressure,min,max,valve,gas,temp,item,accepted);
    }
}

