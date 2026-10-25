package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Authoritative coordinates and presentation seed only; clients never choose targets or apply damage. */
public record TeslaArcPayload(ResourceLocation dimension, double fromX, double fromY, double fromZ,
                              double toX, double toY, double toZ, long seed, float lifetimeSeconds, float beamHalfWidth)
        implements CustomPacketPayload {
    public static final Type<TeslaArcPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "tesla_arc"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeslaArcPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeResourceLocation(p.dimension); buf.writeDouble(p.fromX); buf.writeDouble(p.fromY); buf.writeDouble(p.fromZ);
                buf.writeDouble(p.toX); buf.writeDouble(p.toY); buf.writeDouble(p.toZ); buf.writeLong(p.seed); buf.writeFloat(p.lifetimeSeconds); buf.writeFloat(p.beamHalfWidth); },
            buf -> new TeslaArcPayload(buf.readResourceLocation(),buf.readDouble(),buf.readDouble(),buf.readDouble(),
                    buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readLong(),buf.readFloat(),buf.readFloat()));
    public TeslaArcPayload {
        if (dimension == null || !finite(fromX,fromY,fromZ,toX,toY,toZ) || !Float.isFinite(lifetimeSeconds)
                || lifetimeSeconds <= 0 || lifetimeSeconds > 30 || !Float.isFinite(beamHalfWidth) || beamHalfWidth < 0) throw new IllegalArgumentException("Invalid Tesla arc");
    }
    private static boolean finite(double... values) { for(double v:values) if(!Double.isFinite(v)) return false; return true; }
    public Vec3 from() { return new Vec3(fromX,fromY,fromZ); }
    public Vec3 to() { return new Vec3(toX,toY,toZ); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
