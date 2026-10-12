package com.juicyslew.moonstation14.ms14.ingestion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyOralIngestionRequestTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }

    @Test void worldItemAndPuddlePayloadsRoundTrip() {
        var body = UUID.randomUUID(); var item = UUID.randomUUID();
        var world = new BodyOralIngestionRequest(body, 8, 3, BodyOralIngestionRequest.Source.WORLD_ITEM, item, null);
        assertEquals(world, roundTrip(world));
        var puddle = new BodyOralIngestionRequest(body, 9, 4, BodyOralIngestionRequest.Source.PUDDLE, null, new BlockPos(1, 2, 3));
        assertEquals(puddle, roundTrip(puddle));
    }

    @Test void rejectsInvalidAuthorityFieldsAndWrongSourcePayload() {
        UUID body = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new BodyOralIngestionRequest(body, 0, 1,
                BodyOralIngestionRequest.Source.WORLD_ITEM, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> new BodyOralIngestionRequest(body, 1, 0,
                BodyOralIngestionRequest.Source.PUDDLE, null, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BodyOralIngestionRequest(body, 1, 1,
                BodyOralIngestionRequest.Source.PUDDLE, UUID.randomUUID(), BlockPos.ZERO));
        var malformed = buffer(); malformed.writeUUID(body); malformed.writeVarLong(1); malformed.writeVarLong(1);
        malformed.writeEnum(BodyOralIngestionRequest.Source.WORLD_ITEM); malformed.writeUUID(UUID.randomUUID()); malformed.writeBlockPos(BlockPos.ZERO);
        assertThrows(IllegalArgumentException.class, () -> BodyOralIngestionRequest.STREAM_CODEC.decode(malformed));
    }

    private static BodyOralIngestionRequest roundTrip(BodyOralIngestionRequest request) {
        var buf = buffer(); BodyOralIngestionRequest.STREAM_CODEC.encode(buf, request);
        return BodyOralIngestionRequest.STREAM_CODEC.decode(buf);
    }
}
