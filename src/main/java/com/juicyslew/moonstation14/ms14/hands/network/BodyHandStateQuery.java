package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Epoch zero requests the initial authoritative binding; all other fields are correlation only. */
public record BodyHandStateQuery(long epoch, long sequence) implements CustomPacketPayload {
    public static final Type<BodyHandStateQuery> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "body_hand_state_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyHandStateQuery> STREAM_CODEC = StreamCodec.of(
            (buf, query) -> { buf.writeVarLong(query.epoch); buf.writeVarLong(query.sequence); },
            buf -> new BodyHandStateQuery(buf.readVarLong(), buf.readVarLong()));

    public BodyHandStateQuery {
        if (epoch < 0 || sequence <= 0) throw new IllegalArgumentException("Invalid hand state query");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
