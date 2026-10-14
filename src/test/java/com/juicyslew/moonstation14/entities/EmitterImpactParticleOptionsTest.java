package com.juicyslew.moonstation14.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmitterImpactParticleOptionsTest {
    @Test void lifetimeSurvivesNativeParticleWireCodec() {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var expected = new EmitterImpactParticleOptions(5);
            EmitterImpactParticleOptions.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, EmitterImpactParticleOptions.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void lifetimeIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new EmitterImpactParticleOptions(0));
        assertThrows(IllegalArgumentException.class, () -> new EmitterImpactParticleOptions(1201));
    }
}
