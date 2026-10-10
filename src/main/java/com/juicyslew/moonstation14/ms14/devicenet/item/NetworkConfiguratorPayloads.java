package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.UUID;

/** Wire payloads for the authoritative network configurator. */
public final class NetworkConfiguratorPayloads {
    private NetworkConfiguratorPayloads() { }
    public record Request(NetworkConfiguratorRequest value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"network_configurator_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> STREAM_CODEC=StreamCodec.of((b,p)->writeRequest(b,p.value),b->new Request(readRequest(b)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(NetworkConfiguratorSnapshot value) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"network_configurator_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> STREAM_CODEC=StreamCodec.of((b,p)->writeSnapshot(b,p.value),b->new Snapshot(readSnapshot(b)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record ListRequest(NetworkConfiguratorListRequest value) implements CustomPacketPayload {
        public static final Type<ListRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"network_configurator_list_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ListRequest> STREAM_CODEC=StreamCodec.of((b,p)->writeListRequest(b,p.value),b->new ListRequest(readListRequest(b)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record ListSnapshot(NetworkConfiguratorListSnapshot value) implements CustomPacketPayload {
        public static final Type<ListSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"network_configurator_list_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ListSnapshot> STREAM_CODEC=StreamCodec.of((b,p)->writeListSnapshot(b,p.value),b->new ListSnapshot(readListSnapshot(b)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static void identity(RegistryFriendlyByteBuf b,DeviceIdentity id){b.writeBoolean(id!=null);if(id!=null)b.writeUUID(id.value());}
    private static DeviceIdentity identity(RegistryFriendlyByteBuf b){return b.readBoolean()?new DeviceIdentity(b.readUUID()):null;}
    private static void writeRequest(RegistryFriendlyByteBuf b,NetworkConfiguratorRequest r){b.writeUUID(r.session());b.writeVarLong(r.requestId());b.writeVarLong(r.epoch());b.writeVarLong(r.revision());b.writeUtf(r.handId(),64);b.writeUtf(r.handToken(),128);b.writeVarInt(r.operation().ordinal());identity(b,r.endpoint());utf(b,r.sourcePort(),128);utf(b,r.sinkPort(),128);}
    private static NetworkConfiguratorRequest readRequest(RegistryFriendlyByteBuf b){UUID s=b.readUUID();long id=b.readVarLong(),e=b.readVarLong(),r=b.readVarLong();String h=b.readUtf(64),t=b.readUtf(128);int op=b.readVarInt();if(op<0||op>=NetworkConfiguratorRequest.Operation.values().length)throw new IllegalArgumentException("invalid configurator operation");return new NetworkConfiguratorRequest(s,id,e,r,h,t,NetworkConfiguratorRequest.Operation.values()[op],identity(b),utf(b,128),utf(b,128));}
    private static void utf(RegistryFriendlyByteBuf b,String s,int max){b.writeBoolean(s!=null);if(s!=null)b.writeUtf(s,max);} private static String utf(RegistryFriendlyByteBuf b,int max){return b.readBoolean()?b.readUtf(max):null;}
    private static void writeSnapshot(RegistryFriendlyByteBuf b,NetworkConfiguratorSnapshot s){b.writeUUID(s.session());b.writeVarLong(s.requestId());b.writeVarLong(s.epoch());b.writeBoolean(s.open());identity(b,s.source());identity(b,s.sink());b.writeUtf(s.sourceAddress(),256);b.writeUtf(s.sinkAddress(),256);ports(b,s.sources());ports(b,s.sinks());b.writeVarInt(s.links().size());s.links().forEach(p->{b.writeUtf(p.sourcePort(),128);b.writeUtf(p.sinkPort(),128);});}
    private static NetworkConfiguratorSnapshot readSnapshot(RegistryFriendlyByteBuf b){UUID id=b.readUUID();long request=b.readVarLong(),epoch=b.readVarLong();boolean open=b.readBoolean();DeviceIdentity src=identity(b),sink=identity(b);String sa=b.readUtf(256),ta=b.readUtf(256);var sources=ports(b);var sinks=ports(b);int n=count(b);var links=new ArrayList<NetworkConfiguratorSnapshot.Pair>(n);for(int i=0;i<n;i++)links.add(new NetworkConfiguratorSnapshot.Pair(b.readUtf(128),b.readUtf(128)));return new NetworkConfiguratorSnapshot(id,request,epoch,open,src,sink,sa,ta,sources,sinks,links);}
    private static void ports(RegistryFriendlyByteBuf b,java.util.List<NetworkConfiguratorSnapshot.Port> p){b.writeVarInt(p.size());p.forEach(x->{b.writeUtf(x.id(),128);b.writeUtf(x.name(),128);b.writeUtf(x.description(),256);});}
    private static java.util.List<NetworkConfiguratorSnapshot.Port> ports(RegistryFriendlyByteBuf b){int n=count(b);var out=new ArrayList<NetworkConfiguratorSnapshot.Port>(n);for(int i=0;i<n;i++)out.add(new NetworkConfiguratorSnapshot.Port(b.readUtf(128),b.readUtf(128),b.readUtf(256)));return out;}
    private static int count(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("invalid configurator list size");return n;}
    private static void writeListRequest(RegistryFriendlyByteBuf b,NetworkConfiguratorListRequest r){b.writeUUID(r.session());b.writeVarLong(r.requestId());b.writeVarLong(r.epoch());b.writeVarLong(r.revision());b.writeUtf(r.handId(),64);b.writeUtf(r.handToken(),128);b.writeVarInt(r.operation().ordinal());b.writeVarInt(r.members().size());r.members().forEach(v->b.writeUtf(v,256));utf(b,r.member(),256);}
    private static NetworkConfiguratorListRequest readListRequest(RegistryFriendlyByteBuf b){UUID session=b.readUUID();long id=b.readVarLong(),epoch=b.readVarLong(),revision=b.readVarLong();String hand=b.readUtf(64),token=b.readUtf(128);int op=b.readVarInt();if(op<0||op>=NetworkConfiguratorListRequest.Operation.values().length)throw new IllegalArgumentException("invalid configurator list operation");int n=count(b);var members=new ArrayList<String>(n);for(int i=0;i<n;i++)members.add(b.readUtf(256));return new NetworkConfiguratorListRequest(session,id,epoch,revision,hand,token,NetworkConfiguratorListRequest.Operation.values()[op],members,utf(b,256));}
     private static void writeListSnapshot(RegistryFriendlyByteBuf b,NetworkConfiguratorListSnapshot s){b.writeUUID(s.session());b.writeVarLong(s.requestId());b.writeVarLong(s.handRevision());b.writeVarLong(s.epoch());b.writeBoolean(s.open());b.writeBoolean(s.show());b.writeUtf(s.targetAddress(),256);b.writeVarInt(s.members().size());s.members().forEach(v->b.writeUtf(v,256));b.writeVarInt(s.staged().size());s.staged().forEach(v->{b.writeUUID(v.identity());b.writeUtf(v.address(),256);b.writeUtf(v.name(),256);});b.writeVarInt(s.memberDisplayRows().size());s.memberDisplayRows().forEach(v->{b.writeUUID(v.identity());b.writeUtf(v.address(),256);b.writeUtf(v.name(),256);});b.writeVarInt(s.positions().size());s.positions().forEach(p->{b.writeUUID(p.identity());b.writeUtf(p.dimension(),256);b.writeInt(p.x());b.writeInt(p.y());b.writeInt(p.z());});}
     private static NetworkConfiguratorListSnapshot readListSnapshot(RegistryFriendlyByteBuf b){UUID session=b.readUUID();long id=b.readVarLong(),revision=b.readVarLong(),epoch=b.readVarLong();boolean open=b.readBoolean(),show=b.readBoolean();String target=b.readUtf(256);int n=count(b);var members=new ArrayList<String>(n);for(int i=0;i<n;i++)members.add(b.readUtf(256));int stagedCount=count(b);var staged=new ArrayList<NetworkConfiguratorStackData.StagedDevice>(stagedCount);for(int i=0;i<stagedCount;i++)staged.add(new NetworkConfiguratorStackData.StagedDevice(b.readUUID(),b.readUtf(256),b.readUtf(256)));int rowCount=count(b);var rows=new ArrayList<NetworkConfiguratorStackData.StagedDevice>(rowCount);for(int i=0;i<rowCount;i++)rows.add(new NetworkConfiguratorStackData.StagedDevice(b.readUUID(),b.readUtf(256),b.readUtf(256)));int pc=count(b);var positions=new ArrayList<NetworkConfiguratorListSnapshot.MemberPosition>(pc);for(int i=0;i<pc;i++)positions.add(new NetworkConfiguratorListSnapshot.MemberPosition(b.readUUID(),b.readUtf(256),b.readInt(),b.readInt(),b.readInt()));return new NetworkConfiguratorListSnapshot(session,id,revision,epoch,open,show,target,members,staged,rows,positions);}
}
