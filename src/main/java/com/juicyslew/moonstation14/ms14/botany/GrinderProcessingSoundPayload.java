package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.util.UUID;

public record GrinderProcessingSoundPayload(ResourceLocation dimension, BlockPos position, UUID placement,
        long startTick, long endTick, ResourceLocation sound, float volume, float pitch, Operation operation) implements CustomPacketPayload {
    public enum Operation { START, STOP }
    public static final Type<GrinderProcessingSoundPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "grinder_processing_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GrinderProcessingSoundPayload> STREAM_CODEC = StreamCodec.of(GrinderProcessingSoundPayload::write, GrinderProcessingSoundPayload::read);
    public GrinderProcessingSoundPayload {
        if (dimension == null || position == null || placement == null || startTick < 0 || endTick <= startTick || sound == null || !Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0 || operation == null) throw new IllegalArgumentException("invalid grinder sound");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf b, GrinderProcessingSoundPayload p) {
        b.writeUtf(p.dimension.toString(),256); b.writeBlockPos(p.position); b.writeUUID(p.placement); b.writeLong(p.startTick); b.writeLong(p.endTick); b.writeUtf(p.sound.toString(),256); b.writeFloat(p.volume); b.writeFloat(p.pitch); b.writeByte(p.operation.ordinal());
    }
    private static GrinderProcessingSoundPayload read(RegistryFriendlyByteBuf b) {
        var dim=ResourceLocation.parse(b.readUtf(256)); var pos=b.readBlockPos(); var id=b.readUUID(); long start=b.readLong(), end=b.readLong(); var sound=ResourceLocation.parse(b.readUtf(256)); float volume=b.readFloat(), pitch=b.readFloat(); int op=b.readUnsignedByte(); if(op>=Operation.values().length) throw new IllegalArgumentException("invalid sound operation"); return new GrinderProcessingSoundPayload(dim,pos,id,start,end,sound,volume,pitch,Operation.values()[op]);
    }
}
