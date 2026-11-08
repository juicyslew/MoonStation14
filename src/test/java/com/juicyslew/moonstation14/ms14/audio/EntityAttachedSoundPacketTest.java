package com.juicyslew.moonstation14.ms14.audio;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityAttachedSoundPacketTest {
    @Test void nativeEntitySoundPacketRetainsBodyEntityAndAudioParameters() {
        Entity body = mock(Entity.class);
        when(body.getId()).thenReturn(421);
        var sound = SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "voice"));

        var packet = new ClientboundSoundEntityPacket(Holder.direct(sound), SoundSource.VOICE,
                body, 0.73f, 1.21f, 17L);

        assertEquals(421, packet.getId());
        assertEquals(SoundSource.VOICE, packet.getSource());
        assertEquals(0.73f, packet.getVolume());
        assertEquals(1.21f, packet.getPitch());
        assertEquals(sound, packet.getSound().value());
    }
}
