package com.juicyslew.moonstation14.ms14.botany.ui;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GrinderSnapshotPayload(GrinderSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<GrinderSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "grinder_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GrinderSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> GrinderWireCodec.writeSnapshot(b, p.snapshot()), b -> new GrinderSnapshotPayload(GrinderWireCodec.readSnapshot(b)));
    public GrinderSnapshotPayload { if (snapshot == null) throw new IllegalArgumentException("missing grinder snapshot"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
