package com.juicyslew.moonstation14.ms14.ingestion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Narrow nomination for an oral-only world ingestion source; never contains item data or a capability. */
public record BodyOralIngestionRequest(UUID bodyId, long epoch, long sequence, Source source,
                                       UUID itemId, BlockPos puddlePos) implements CustomPacketPayload {
    public enum Source { WORLD_ITEM, PUDDLE }
    public static final Type<BodyOralIngestionRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_oral_ingestion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyOralIngestionRequest> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.bodyId); buf.writeVarLong(value.epoch); buf.writeVarLong(value.sequence); buf.writeEnum(value.source);
                if (value.source == Source.WORLD_ITEM) buf.writeUUID(value.itemId); else buf.writeBlockPos(value.puddlePos);
            }, buf -> {
                UUID body = buf.readUUID(); long epoch = buf.readVarLong(), sequence = buf.readVarLong(); Source source = buf.readEnum(Source.class);
                UUID item = source == Source.WORLD_ITEM ? buf.readUUID() : null;
                BlockPos pos = source == Source.PUDDLE ? buf.readBlockPos() : null;
                if (buf.isReadable()) throw new IllegalArgumentException("Unexpected oral ingestion payload");
                return new BodyOralIngestionRequest(body, epoch, sequence, source, item, pos);
            });
    public BodyOralIngestionRequest {
        if (bodyId == null || epoch <= 0 || sequence <= 0 || source == null
                || (source == Source.WORLD_ITEM ? itemId == null || puddlePos != null : puddlePos == null || itemId != null))
            throw new IllegalArgumentException("Invalid oral ingestion intent");
        if (puddlePos != null) puddlePos = puddlePos.immutable();
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
