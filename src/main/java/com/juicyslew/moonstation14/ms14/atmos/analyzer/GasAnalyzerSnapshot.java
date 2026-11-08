package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import java.util.List;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;

/** Bounded data-only server projection; presentation never owns or mutates atmosphere state. */
public record GasAnalyzerSnapshot(UUID session, long generation, long sequence, long controlSequence, long serverTick,
                                  long epoch, UUID body, String sourceToken,
                                  ResourceLocation dimension, BlockPos localCell, BlockPos targetCell,
                                  int requestedDistance, Status status, GasAnalyzerSample local,
                                   GasAnalyzerSample target, boolean closed, GasAnalyzerPipeSamples.Source source,
                                   List<GasAnalyzerPipeSamples.Entry> nodeEntries) implements CustomPacketPayload {
    public enum Status { READY, BLOCKED, UNAVAILABLE, INVALIDATED }
    public static final Type<GasAnalyzerSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_analyzer_snapshot"));
    private static final int MAX_GASES = 9;
    private static final int MAX_NODES = GasAnalyzerPipeSamples.MAX_ENTRIES;
    public GasAnalyzerSnapshot(UUID session,long generation,long sequence,long controlSequence,long serverTick,long epoch,
                               UUID body,String sourceToken,ResourceLocation dimension,BlockPos localCell,BlockPos targetCell,
                               int requestedDistance,Status status,GasAnalyzerSample local,GasAnalyzerSample target,boolean closed) {
        this(session,generation,sequence,controlSequence,serverTick,epoch,body,sourceToken,dimension,localCell,targetCell,
                requestedDistance,status,local,target,closed,null,List.of());
    }
    private static void writeSample(RegistryFriendlyByteBuf b, GasAnalyzerSample sample) {
        b.writeBoolean(sample != null);
        if (sample == null) return;
        b.writeDouble(sample.volumeLitres()); b.writeDouble(sample.pressureKpa()); b.writeDouble(sample.temperatureKelvin());
        b.writeVarInt(sample.gases().size());
        for (var gas : sample.gases()) { b.writeEnum(gas.gas()); b.writeDouble(gas.moles()); b.writeDouble(gas.percentage()); }
    }
    private static GasAnalyzerSample readSample(RegistryFriendlyByteBuf b) {
        if (!b.readBoolean()) return null;
        double volume = b.readDouble(), pressure = b.readDouble(), temperature = b.readDouble();
        int count = b.readVarInt();
        if (count < 0 || count > MAX_GASES) throw new IllegalArgumentException("invalid analyzer gas count");
        var gases = new java.util.ArrayList<GasAnalyzerSample.GasAmount>(count);
        for (int i=0;i<count;i++) gases.add(new GasAnalyzerSample.GasAmount(b.readEnum(GasType.class), b.readDouble(), b.readDouble()));
        return new GasAnalyzerSample(volume, pressure, temperature, gases);
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, GasAnalyzerSnapshot> STREAM_CODEC = StreamCodec.of(
            (b,v) -> { b.writeUUID(v.session); b.writeVarLong(v.generation); b.writeVarLong(v.sequence); b.writeVarLong(v.controlSequence); b.writeVarLong(v.serverTick); b.writeVarLong(v.epoch);
                b.writeUUID(v.body); b.writeUtf(v.sourceToken, com.juicyslew.moonstation14.ms14.hands.ItemToken.MAX_LENGTH); b.writeResourceLocation(v.dimension); b.writeBlockPos(v.localCell);
                b.writeBoolean(v.targetCell != null); if(v.targetCell != null)b.writeBlockPos(v.targetCell);
                 b.writeVarInt(v.requestedDistance); b.writeEnum(v.status); writeSample(b,v.local); writeSample(b,v.target); b.writeBoolean(v.closed);
                 b.writeBoolean(v.source != null); if(v.source != null){ b.writeEnum(v.source); b.writeVarInt(v.nodeEntries.size());
                     for(var e:v.nodeEntries){var s=e.stamp();b.writeBlockPos(s.node().position());b.writeEnum(s.node().layer());b.writeUtf(s.node().portId(),128);b.writeUUID(s.incarnation());b.writeVarLong(s.version());b.writeDouble(s.volumeCubicMeters());b.writeUtf(e.labelKey(),128);writeSample(b,e.sample());}}
             },
            b -> { UUID session=b.readUUID(); long generation=b.readVarLong(),sequence=b.readVarLong(),controlSequence=b.readVarLong(),serverTick=b.readVarLong(),epoch=b.readVarLong(); UUID body=b.readUUID();
                String sourceToken=b.readUtf(com.juicyslew.moonstation14.ms14.hands.ItemToken.MAX_LENGTH);
                var dimension=b.readResourceLocation(); var localCell=b.readBlockPos(); var targetCell=b.readBoolean()?b.readBlockPos():null;
                 int distance=b.readVarInt(); var status=b.readEnum(Status.class); var local=readSample(b); var target=readSample(b); boolean closed=b.readBoolean();
                 GasAnalyzerPipeSamples.Source source=null; var entries=List.<GasAnalyzerPipeSamples.Entry>of();
                 if(b.readBoolean()){source=b.readEnum(GasAnalyzerPipeSamples.Source.class);int n=b.readVarInt();if(n<1||n>MAX_NODES)throw new IllegalArgumentException("invalid analyzer node count");
                      var values=new java.util.ArrayList<GasAnalyzerPipeSamples.Entry>(n);for(int i=0;i<n;i++){var pos=b.readBlockPos();var layer=b.readEnum(PipeTopology.Layer.class);var port=b.readUtf(128);var id=b.readUUID();long version=b.readVarLong();double volume=b.readDouble();var label=b.readUtf(128);var sample=readSample(b);
                          if(sample==null)throw new IllegalArgumentException("missing node sample");values.add(new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(new PipeTopology.NodeKey(pos,layer,port),id,version,volume),label,sample));} entries=List.copyOf(values);}
                 return new GasAnalyzerSnapshot(session,generation,sequence,controlSequence,serverTick,epoch,body,sourceToken,dimension,localCell,targetCell,distance,status,local,target,closed,source,entries); });
    public GasAnalyzerSnapshot {
        if (session == null || generation < 1 || sequence < 0 || controlSequence < 0 || serverTick < 0 || epoch < 1 || body == null
                || sourceToken == null || sourceToken.isBlank() || sourceToken.length() > com.juicyslew.moonstation14.ms14.hands.ItemToken.MAX_LENGTH || dimension == null
                || localCell == null || requestedDistance < 0 || status == null
                 || local != null && local.gases().size() > MAX_GASES || target != null && target.gases().size() > MAX_GASES
                 || source == null && (nodeEntries == null || !nodeEntries.isEmpty())
                  || source != null && (nodeEntries == null || nodeEntries.isEmpty() || nodeEntries.size() > MAX_NODES || target != null || local != null)
                  || source == GasAnalyzerPipeSamples.Source.UNARY_DEVICE && nodeEntries != null && nodeEntries.size()!=1)
            throw new IllegalArgumentException("invalid analyzer snapshot");
        nodeEntries = List.copyOf(nodeEntries);
        if (source != null) {
            if(targetCell==null)throw new IllegalArgumentException("node source requires target block");
            for(var entry:nodeEntries){
                var nodePos=entry.stamp().node().position();
                if(source==GasAnalyzerPipeSamples.Source.UNARY_DEVICE){
                    long distance=Math.abs((long)nodePos.getX()-targetCell.getX())+Math.abs((long)nodePos.getY()-targetCell.getY())
                            +Math.abs((long)nodePos.getZ()-targetCell.getZ());
                    if(distance!=1||entry.stamp().node().devicePort())throw new IllegalArgumentException("unary pipe sample must be one adjacent lane");
                }else if(!nodePos.equals(targetCell))throw new IllegalArgumentException("node sample identity does not match target block");
            }
        }
        localCell = localCell.immutable(); targetCell = targetCell == null ? null : targetCell.immutable();
        if (closed && (local != null || target != null)) throw new IllegalArgumentException("closed analyzer snapshot carries sample");
        if (closed && source != null) throw new IllegalArgumentException("closed analyzer snapshot carries node data");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
