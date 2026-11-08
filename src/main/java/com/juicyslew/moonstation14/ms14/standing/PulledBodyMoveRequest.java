package com.juicyslew.moonstation14.ms14.standing;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Signal that the authenticated player's active body has a directional input wish. */
public record PulledBodyMoveRequest(boolean hasInput) implements CustomPacketPayload {
    public PulledBodyMoveRequest() { this(true); }
    public static final Type<PulledBodyMoveRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "pulled_body_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PulledBodyMoveRequest> STREAM_CODEC =
            StreamCodec.of((buf, value) -> net.minecraft.network.codec.ByteBufCodecs.BOOL.encode(buf, value.hasInput),
                    buf -> new PulledBodyMoveRequest(net.minecraft.network.codec.ByteBufCodecs.BOOL.decode(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

