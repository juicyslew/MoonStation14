package com.juicyslew.moonstation14.ms14.disposal.network;

import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Complete bounded replacement for one watched chunk; empty records clear it. */
public record DisposalTubeVisualPayload(ResourceLocation dimension, int chunkX, int chunkZ, boolean reset, List<DisposalTubeRecord> records) implements CustomPacketPayload {
    public static final int MAX_RECORDS=256;
    public static final Type<DisposalTubeVisualPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","disposal_tube_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisposalTubeVisualPayload> STREAM_CODEC=StreamCodec.of(DisposalTubeVisualPayload::write,DisposalTubeVisualPayload::read);
    public DisposalTubeVisualPayload {
        Objects.requireNonNull(dimension); Objects.requireNonNull(records);
        if(records.size()>MAX_RECORDS) throw new IllegalArgumentException("too many disposal records");
        records=List.copyOf(records);
        for(var record:records) if((record.pos().getX()>>4)!=chunkX||(record.pos().getZ()>>4)!=chunkZ) throw new IllegalArgumentException("record outside chunk");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private static void write(RegistryFriendlyByteBuf b, DisposalTubeVisualPayload p){
        b.writeResourceLocation(p.dimension);b.writeInt(p.chunkX);b.writeInt(p.chunkZ);b.writeBoolean(p.reset);b.writeVarInt(p.records.size());
        for(var r:p.records){b.writeUUID(r.id());b.writeLong(r.pos().asLong());b.writeUtf(r.prototypeId(),128);b.writeByte(r.frame().forward().ordinal());b.writeByte(r.frame().up().ordinal());b.writeBoolean(r.anchored());b.writeUtf(Objects.requireNonNullElse(r.routingTag(),""),256);b.writeUtf(Objects.requireNonNullElse(r.routingFilter(),""),256);b.writeVarLong(r.revision());b.writeByte(r.hostKind().ordinal());b.writeUtf(r.sealedHostId(),256);b.writeBoolean(r.hostGeneration()!=null);if(r.hostGeneration()!=null)b.writeUUID(r.hostGeneration());}
    }
    private static DisposalTubeVisualPayload read(RegistryFriendlyByteBuf b){
        var dimension=b.readResourceLocation();int x=b.readInt(),z=b.readInt();boolean reset=b.readBoolean();int n=b.readVarInt();if(n<0||n>MAX_RECORDS)throw new IllegalArgumentException("invalid disposal record count");
        var records=new ArrayList<DisposalTubeRecord>(n);for(int i=0;i<n;i++){var id=b.readUUID();var pos=BlockPos.of(b.readLong());String proto=b.readUtf(128);int d=b.readUnsignedByte(),u=b.readUnsignedByte();if(d>=Direction.values().length||u>=Direction.values().length)throw new IllegalArgumentException("invalid frame direction");var frame=new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.values()[d],Direction.values()[u]);boolean anchored=b.readBoolean();String tag=b.readUtf(256),filter=b.readUtf(256);long revision=b.readVarLong();int kind=b.readUnsignedByte();if(kind>=DisposalTubeRecord.HostKind.values().length)throw new IllegalArgumentException("invalid host kind");String host=b.readUtf(256);var generation=b.readBoolean()?b.readUUID():null;records.add(new DisposalTubeRecord(id,pos,proto,frame,anchored,tag,filter,revision,DisposalTubeRecord.HostKind.values()[kind],host,generation));}
        if(b.isReadable())throw new IllegalArgumentException("trailing disposal snapshot bytes");return new DisposalTubeVisualPayload(dimension,x,z,reset,records);
    }
}

