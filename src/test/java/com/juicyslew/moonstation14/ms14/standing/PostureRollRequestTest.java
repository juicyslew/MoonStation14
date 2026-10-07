package com.juicyslew.moonstation14.ms14.standing;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureRollRequestTest {
    @Test void selfAndTargetedRollRequestsRoundTrip() {
        for (PostureRollRequest request : new PostureRollRequest[]{new PostureRollRequest(null),
                new PostureRollRequest(UUID.randomUUID())}) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                PostureRollRequest.STREAM_CODEC.encode(buffer, request);
                assertEquals(request, PostureRollRequest.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }
}
