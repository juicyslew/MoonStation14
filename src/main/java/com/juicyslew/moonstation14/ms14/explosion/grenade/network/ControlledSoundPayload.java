package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Positional sound routed to listeners by their active server camera rather than carrier position. */
public record ControlledSoundPayload(ResourceLocation dimension, ResourceLocation sound,
                                     double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ControlledSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "controlled_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlledSoundPayload> STREAM_CODEC =
            StreamCodec.of(ControlledSoundPayload::write, ControlledSoundPayload::read);
    public ControlledSoundPayload {
        if (dimension == null || sound == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Invalid controlled sound payload");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf b, ControlledSoundPayload p) {
        b.writeUtf(p.dimension.toString(), 256); b.writeUtf(p.sound.toString(), 256);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
    }
    private static ControlledSoundPayload read(RegistryFriendlyByteBuf b) {
        return new ControlledSoundPayload(ResourceLocation.parse(b.readUtf(256)), ResourceLocation.parse(b.readUtf(256)),
                b.readDouble(), b.readDouble(), b.readDouble());
    }
}
