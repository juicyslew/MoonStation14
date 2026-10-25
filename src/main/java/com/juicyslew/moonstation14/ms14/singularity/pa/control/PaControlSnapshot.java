package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Resolved authoritative PA state for a body-bound operator session. */
public record PaControlSnapshot(UUID sessionId, long requestId, UUID machineId, UUID bodyId, long authorityEpoch,
        ResourceLocation dimension, int x, int y, int z, ResourceLocation profileId, long revision,
        int maximumStrength, int strength, boolean enabled, boolean assembled, boolean apcAvailable,
        double powerDrawWatts, double receivedWatts, java.util.List<String> partRoles, boolean[] present, boolean accepted, boolean closed)
        implements CustomPacketPayload {
    public static final Type<PaControlSnapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"pa_control_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PaControlSnapshot> STREAM_CODEC=StreamCodec.of(PaControlSnapshot::write,PaControlSnapshot::read);
    public PaControlSnapshot { present=present.clone(); partRoles=java.util.List.copyOf(partRoles);if(partRoles.size()!=present.length)throw new IllegalArgumentException("PA role/presence mismatch"); }
    @Override public boolean[] present(){return present.clone();}
    private static void write(RegistryFriendlyByteBuf b,PaControlSnapshot s){
        b.writeUUID(s.sessionId);b.writeLong(s.requestId);b.writeUUID(s.machineId);b.writeUUID(s.bodyId);b.writeLong(s.authorityEpoch);b.writeResourceLocation(s.dimension);
        b.writeVarInt(s.x);b.writeVarInt(s.y);b.writeVarInt(s.z);b.writeResourceLocation(s.profileId);b.writeLong(s.revision);b.writeVarInt(s.maximumStrength);b.writeVarInt(s.strength);
        b.writeBoolean(s.enabled);b.writeBoolean(s.assembled);b.writeBoolean(s.apcAvailable);b.writeDouble(s.powerDrawWatts);b.writeDouble(s.receivedWatts);
        b.writeVarInt(s.present.length);for(int i=0;i<s.present.length;i++){b.writeUtf(s.partRoles.get(i),64);b.writeBoolean(s.present[i]);}b.writeBoolean(s.accepted);b.writeBoolean(s.closed);
    }
    private static PaControlSnapshot read(RegistryFriendlyByteBuf b){
        UUID session=b.readUUID();long request=b.readLong();UUID machine=b.readUUID(),body=b.readUUID();long epoch=b.readLong();ResourceLocation dimension=b.readResourceLocation();
        int x=b.readVarInt(),y=b.readVarInt(),z=b.readVarInt();ResourceLocation profile=b.readResourceLocation();long rev=b.readLong();int max=b.readVarInt(),strength=b.readVarInt();
        boolean enabled=b.readBoolean(),assembled=b.readBoolean(),apc=b.readBoolean();double draw=b.readDouble(),received=b.readDouble();int n=b.readVarInt();if(n<0||n>32)throw new IllegalArgumentException("Invalid PA parts count");java.util.List<String> roles=new java.util.ArrayList<>(n);boolean[] parts=new boolean[n];for(int i=0;i<n;i++){roles.add(b.readUtf(64));parts[i]=b.readBoolean();}
        return new PaControlSnapshot(session,request,machine,body,epoch,dimension,x,y,z,profile,rev,max,strength,enabled,assembled,apc,draw,received,roles,parts,b.readBoolean(),b.readBoolean());
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
