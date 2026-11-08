package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Authenticated, idempotent world-host session release; never mutates the item. */
public record BodyWorldGridClose(long epoch, UUID target, UUID session) implements CustomPacketPayload {
    public static final Type<BodyWorldGridClose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_grid_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldGridClose> STREAM_CODEC = StreamCodec.of(
            (b, close) -> { b.writeVarLong(close.epoch); b.writeUUID(close.target); b.writeUUID(close.session); },
            b -> {
                var close = new BodyWorldGridClose(b.readVarLong(), b.readUUID(), b.readUUID());
                if (b.isReadable()) throw new IllegalArgumentException("Trailing world grid close data");
                return close;
            });

    public BodyWorldGridClose {
        if (epoch <= 0 || target == null || session == null) throw new IllegalArgumentException("Invalid world grid close");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

