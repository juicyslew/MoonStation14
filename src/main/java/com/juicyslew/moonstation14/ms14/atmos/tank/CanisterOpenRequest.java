package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record CanisterOpenRequest(UUID machineId) implements CustomPacketPayload {
    public static final Type<CanisterOpenRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "canister_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CanisterOpenRequest> STREAM_CODEC = StreamCodec.of((b,p)->b.writeUUID(p.machineId()), b->new CanisterOpenRequest(b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

