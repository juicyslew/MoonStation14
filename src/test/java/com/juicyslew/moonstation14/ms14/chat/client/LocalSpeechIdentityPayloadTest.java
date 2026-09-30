package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalCharacterIdentityPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechIdentityPayloadTest {
    @Test
    void savedIdentityEnablesMentionsBeforeSelfSpeech() {
        var identity = new LocalCharacterIdentityPayload("Alice Smith", 0xffffff,
                ResourceLocation.parse("minecraft:the_nether"));
        assertTrue(LocalSpeechTranscript.mentions("Alice, watch out!", identity.name()));
        assertFalse(LocalSpeechTranscript.mentions("malice", identity.name()));
        assertFalse(LocalSpeechTranscript.mentions("Alice, watch out!", null));
    }

    @Test
    void identityCodecRetainsDimensionAndRejectsStaleWorld() {
        var dimension = ResourceLocation.parse("minecraft:the_nether");
        var sent = new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, dimension);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalCharacterIdentityPayload.STREAM_CODEC.encode(buffer, sent);
            var received = LocalCharacterIdentityPayload.STREAM_CODEC.decode(buffer);
            assertEquals(sent, received);
            assertTrue(received.inDimension(dimension));
            assertFalse(received.inDimension(ResourceLocation.parse("minecraft:overworld")));
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidIdentitiesCannotBeSent() {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Player123", 0xffffff, dimension));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xff000000, dimension));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0x000000, dimension));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, null));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xffffff,
                ResourceLocation.fromNamespaceAndPath("test", "a".repeat(252))));
    }
}
