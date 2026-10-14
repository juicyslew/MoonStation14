package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-only sound directive: near has an origin, far has a bounded gain and plays at the camera. */
public record GrenadeSoundPayload(ResourceLocation dimension, Mode mode, double x, double y, double z,
                                  float gain) implements CustomPacketPayload {
    private static final int MAX_DIMENSION_LENGTH = 256;
    public enum Mode { NEAR, FAR }
    public static final Type<GrenadeSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "grenade_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GrenadeSoundPayload> STREAM_CODEC =
            StreamCodec.of(GrenadeSoundPayload::write, GrenadeSoundPayload::read);

    public GrenadeSoundPayload {
        if (dimension == null || dimension.toString().length() > MAX_DIMENSION_LENGTH || mode == null
                || !Float.isFinite(gain) || gain <= 0 || gain > GrenadeSoundPolicy.NEAR_GAIN
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || (mode == Mode.NEAR && (gain != GrenadeSoundPolicy.NEAR_GAIN
                        || Math.abs(x) > 30_000_000 || Math.abs(y) > 4096 || Math.abs(z) > 30_000_000))
                || (mode == Mode.FAR && (x != 0 || y != 0 || z != 0)))
            throw new IllegalArgumentException("Invalid grenade sound payload");
    }

    public static GrenadeSoundPayload near(ResourceLocation dimension, double x, double y, double z) {
        return new GrenadeSoundPayload(dimension, Mode.NEAR, x, y, z, GrenadeSoundPolicy.NEAR_GAIN);
    }

    public static GrenadeSoundPayload far(ResourceLocation dimension, float gain) {
        return new GrenadeSoundPayload(dimension, Mode.FAR, 0, 0, 0, gain);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, GrenadeSoundPayload value) {
        buf.writeUtf(value.dimension.toString(), MAX_DIMENSION_LENGTH);
        buf.writeEnum(value.mode);
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
        buf.writeFloat(value.gain);
    }

    private static GrenadeSoundPayload read(RegistryFriendlyByteBuf buf) {
        return new GrenadeSoundPayload(ResourceLocation.parse(buf.readUtf(MAX_DIMENSION_LENGTH)),
                buf.readEnum(Mode.class), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }
}

