package com.juicyslew.moonstation14.ms14.standing;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PostureToggleRequest() implements CustomPacketPayload {
    public static final Type<PostureToggleRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "posture_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PostureToggleRequest> STREAM_CODEC = StreamCodec.unit(new PostureToggleRequest());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

