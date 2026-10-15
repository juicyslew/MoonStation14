package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CanisterSnapshotPayload(CanisterSessionSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<CanisterSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "canister_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CanisterSnapshotPayload> STREAM_CODEC = StreamCodec.of((b,p)->CanisterWireCodec.writeSnapshot(b,p.snapshot()), b->new CanisterSnapshotPayload(CanisterWireCodec.readSnapshot(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

