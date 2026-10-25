package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Visual-only Tesla departure cue; it has no gameplay effect. */
public record TeslaJumpPulsePayload(ResourceLocation dimension,double x,double y,double z) implements CustomPacketPayload {
    public static final Type<TeslaJumpPulsePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","tesla_jump_pulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TeslaJumpPulsePayload> STREAM_CODEC=StreamCodec.of(
            (buf,p)->{buf.writeResourceLocation(p.dimension);buf.writeDouble(p.x);buf.writeDouble(p.y);buf.writeDouble(p.z);},
            buf->new TeslaJumpPulsePayload(buf.readResourceLocation(),buf.readDouble(),buf.readDouble(),buf.readDouble()));
    public TeslaJumpPulsePayload { if(dimension==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid Tesla jump pulse"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
