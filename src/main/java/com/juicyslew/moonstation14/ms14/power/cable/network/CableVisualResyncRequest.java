package com.juicyslew.moonstation14.ms14.power.cable.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests a visual-only snapshot for a chunk already watched by the sender. */
public record CableVisualResyncRequest(int chunkX, int chunkZ) implements CustomPacketPayload {
    public static final int MAX_CHUNK_COORDINATE = 1_875_000;
    public static final Type<CableVisualResyncRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "cable_visual_resync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CableVisualResyncRequest> STREAM_CODEC =
            StreamCodec.of(CableVisualResyncRequest::write, CableVisualResyncRequest::read);

    public CableVisualResyncRequest {
        if (chunkX < -MAX_CHUNK_COORDINATE || chunkX > MAX_CHUNK_COORDINATE
                || chunkZ < -MAX_CHUNK_COORDINATE || chunkZ > MAX_CHUNK_COORDINATE)
            throw new IllegalArgumentException("chunk coordinate outside supported world bounds");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, CableVisualResyncRequest value) {
        buf.writeInt(value.chunkX);
        buf.writeInt(value.chunkZ);
    }

    private static CableVisualResyncRequest read(RegistryFriendlyByteBuf buf) {
        int x = buf.readInt(), z = buf.readInt();
        if (buf.isReadable()) throw new IllegalArgumentException("trailing bytes in cable visual resync request");
        return new CableVisualResyncRequest(x, z);
    }
}
