package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeslaArcPayloadTest {
    @Test
    void streamCodecRoundTripsWidthAndCoordinates() {
        var original = new TeslaArcPayload(ResourceLocation.withDefaultNamespace("overworld"),
                1.25, 2.5, -3.75, 8.5, 9.25, 10.0, 123456789L, 0.75f, 0.20f);
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            TeslaArcPayload.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, TeslaArcPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
