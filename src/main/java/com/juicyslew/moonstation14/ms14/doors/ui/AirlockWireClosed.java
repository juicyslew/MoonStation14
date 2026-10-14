package com.juicyslew.moonstation14.ms14.doors.ui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Server notification that an authenticated wire session has ended. */
public record AirlockWireClosed(int windowId, UUID session) implements CustomPacketPayload {
    public static final Type<AirlockWireClosed> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "airlock_wire_closed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirlockWireClosed> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.windowId); buffer.writeUUID(value.session); },
            buffer -> new AirlockWireClosed(buffer.readVarInt(), buffer.readUUID()));

    public AirlockWireClosed {
        if (windowId < 0 || session == null) throw new IllegalArgumentException("invalid wire window");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

