package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Client intent only: the server derives the ray target from the controlled body's physical view. */
public record GasAnalyzerControl(UUID session, long generation, long epoch, long sequence, int distance, boolean close)
        implements CustomPacketPayload {
    public static final Type<GasAnalyzerControl> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_analyzer_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GasAnalyzerControl> STREAM_CODEC = StreamCodec.of(
            (b,v)->{b.writeUUID(v.session);b.writeVarLong(v.generation);b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeVarInt(v.distance);b.writeBoolean(v.close);},
            b->new GasAnalyzerControl(b.readUUID(),b.readVarLong(),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readBoolean()));
    public GasAnalyzerControl { if(session==null||generation<1||epoch<1||sequence<1||distance<0||distance>64)throw new IllegalArgumentException("invalid analyzer control"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
