package com.juicyslew.moonstation14.ms14.botany.ui;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GrinderControlPayload(GrinderControl control) implements CustomPacketPayload {
    public static final Type<GrinderControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "grinder_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GrinderControlPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> GrinderWireCodec.writeControl(b, p.control()), b -> new GrinderControlPayload(GrinderWireCodec.readControl(b)));
    public GrinderControlPayload { if (control == null) throw new IllegalArgumentException("missing grinder control"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
