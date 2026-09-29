package com.juicyslew.moonstation14.ms14.power.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Bounded desired-state intent only: no device position is accepted. */
public record ApcToggleRequest(int containerId, UUID session, long expectedRevision,
                               long requestId, boolean desiredClosed)
        implements CustomPacketPayload {
    public static final Type<ApcToggleRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            "moonstation14", "apc_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApcToggleRequest> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeVarInt(value.containerId);
                buf.writeUUID(value.session);
                buf.writeVarLong(value.expectedRevision);
                buf.writeVarLong(value.requestId);
                buf.writeBoolean(value.desiredClosed);
            }, buf -> new ApcToggleRequest(buf.readVarInt(), buf.readUUID(), buf.readVarLong(),
                    buf.readVarLong(), buf.readBoolean()));

    public ApcToggleRequest {
        if (containerId < 0 || expectedRevision < 0 || requestId < 0)
            throw new IllegalArgumentException("Invalid APC action");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
