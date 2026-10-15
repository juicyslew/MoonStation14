package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record CanisterCloseRequest(UUID sessionId) implements CustomPacketPayload {
    public static final Type<CanisterCloseRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "canister_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CanisterCloseRequest> STREAM_CODEC = StreamCodec.of((b,p)->b.writeUUID(p.sessionId()), b->new CanisterCloseRequest(b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

