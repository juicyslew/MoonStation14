package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeSoundPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:the_nether");

    @Test
    void registeredCodecPreservesNormalAndSmallNearAndFarDirectives() {
        assertRoundTrip(GrenadeSoundPayload.near(DIMENSION, 12.25, 64.5, -8.75),
                GrenadeSoundPayload.Mode.NEAR, GrenadeSoundPayload.SoundSet.GRENADE, .5f, 12.25, 64.5, -8.75);
        assertRoundTrip(GrenadeSoundPayload.far(DIMENSION, .25f),
                GrenadeSoundPayload.Mode.FAR, GrenadeSoundPayload.SoundSet.GRENADE, .25f, 0, 0, 0);
        assertRoundTrip(GrenadeSoundPayload.smallNear(DIMENSION, 12.25, 64.5, -8.75),
                GrenadeSoundPayload.Mode.NEAR, GrenadeSoundPayload.SoundSet.SMALL, 1f, 12.25, 64.5, -8.75);
        assertRoundTrip(GrenadeSoundPayload.smallFar(DIMENSION),
                GrenadeSoundPayload.Mode.FAR, GrenadeSoundPayload.SoundSet.SMALL, 1.258925f, 0, 0, 0);
    }

    private static void assertRoundTrip(GrenadeSoundPayload original, GrenadeSoundPayload.Mode mode,
                                        GrenadeSoundPayload.SoundSet set, float gain,
                                        double x, double y, double z) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            GrenadeSoundPayload.STREAM_CODEC.encode(buffer, original);
            var decoded = GrenadeSoundPayload.STREAM_CODEC.decode(buffer);
            assertEquals(DIMENSION, decoded.dimension());
            assertEquals(mode, decoded.mode());
            assertEquals(set, decoded.soundSet());
            assertEquals(gain, decoded.gain());
            assertEquals(x, decoded.x());
            assertEquals(y, decoded.y());
            assertEquals(z, decoded.z());
        } finally {
            buffer.release();
        }
    }
}
