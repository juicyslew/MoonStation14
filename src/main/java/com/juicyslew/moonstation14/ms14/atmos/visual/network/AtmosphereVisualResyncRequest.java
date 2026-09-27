package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests a fresh visual snapshot for one chunk the sender currently watches. */
public record AtmosphereVisualResyncRequest(int chunkX, int chunkZ) implements CustomPacketPayload {
    public static final int MAX_CHUNK_COORDINATE = 1_875_000;
    public static final Type<AtmosphereVisualResyncRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "atmosphere_visual_resync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphereVisualResyncRequest> STREAM_CODEC =
            StreamCodec.of(AtmosphereVisualResyncRequest::write, AtmosphereVisualResyncRequest::read);

    public AtmosphereVisualResyncRequest {
        if (!validChunkCoordinate(chunkX) || !validChunkCoordinate(chunkZ))
            throw new IllegalArgumentException("chunk coordinate outside supported world bounds");
    }

    static boolean validChunkCoordinate(int coordinate) {
        return coordinate >= -MAX_CHUNK_COORDINATE && coordinate <= MAX_CHUNK_COORDINATE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, AtmosphereVisualResyncRequest value) {
        buf.writeInt(value.chunkX);
        buf.writeInt(value.chunkZ);
    }

    private static AtmosphereVisualResyncRequest read(RegistryFriendlyByteBuf buf) {
        try {
            int x = buf.readInt();
            int z = buf.readInt();
            if (buf.isReadable()) throw new IllegalArgumentException("trailing bytes in visual resync request");
            return new AtmosphereVisualResyncRequest(x, z);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed visual resync request", exception);
        }
    }
}
