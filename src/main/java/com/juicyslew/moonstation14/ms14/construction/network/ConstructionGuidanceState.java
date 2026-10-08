package com.juicyslew.moonstation14.ms14.construction.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import java.util.function.Consumer;

/** Bounded server-generated advisory for one construction-record incarnation. */
public record ConstructionGuidanceState(long epoch,long sequence,UUID body,BlockPos target,UUID host,String goal,String requirement)
        implements CustomPacketPayload {
    public static final Type<ConstructionGuidanceState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","construction_guidance_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConstructionGuidanceState> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeUUID(v.body);b.writeBlockPos(v.target);b.writeBoolean(v.host!=null);if(v.host!=null)b.writeUUID(v.host);b.writeUtf(v.goal,128);b.writeUtf(v.requirement,256);},
            b->new ConstructionGuidanceState(b.readVarLong(),b.readVarLong(),b.readUUID(),b.readBlockPos(),b.readBoolean()?b.readUUID():null,b.readUtf(128),b.readUtf(256)));
    private static volatile Consumer<ConstructionGuidanceState> clientHandler;
    public ConstructionGuidanceState { if(epoch<=0||sequence<=0||body==null||target==null||goal==null||requirement==null||goal.length()>128||requirement.length()>256)throw new IllegalArgumentException("Invalid guidance state"); target=target.immutable(); }
    public static void setClientHandler(Consumer<ConstructionGuidanceState> handler){clientHandler=handler;}
    public static void deliverClient(ConstructionGuidanceState state){var h=clientHandler;if(h!=null)h.accept(state);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
