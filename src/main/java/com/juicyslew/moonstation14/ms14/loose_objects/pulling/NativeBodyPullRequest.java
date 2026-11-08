package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Native-host pull intent; the server owns hand and revision selection. */
public record NativeBodyPullRequest(UUID target) implements CustomPacketPayload {
    public static final Type<NativeBodyPullRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "native_body_pull"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NativeBodyPullRequest> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBoolean(value.target != null); if (value.target != null) buf.writeUUID(value.target); },
            buf -> new NativeBodyPullRequest(buf.readBoolean() ? buf.readUUID() : null));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

