package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlledSoundPayloadTest {
    @Test void pitchRoundTripsAndLegacyConstructorsDefaultToUnity() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var sound = ResourceLocation.fromNamespaceAndPath("moonstation14", "chemistry/chemmaster/dump");
        var payload = new ControlledSoundPayload(dimension, sound, 1, 2, 3, .5f, 1.2f, net.minecraft.sounds.SoundSource.VOICE);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ControlledSoundPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, ControlledSoundPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        assertEquals(1f, new ControlledSoundPayload(dimension, sound, 1, 2, 3).pitch());
        assertEquals(1f, new ControlledSoundPayload(dimension, sound, 1, 2, 3, .5f).pitch());
        assertEquals(net.minecraft.sounds.SoundSource.PLAYERS, new ControlledSoundPayload(dimension, sound, 1, 2, 3).category());
    }
}
