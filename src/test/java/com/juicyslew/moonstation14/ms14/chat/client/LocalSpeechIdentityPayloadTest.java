package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalCharacterIdentityPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class LocalSpeechIdentityPayloadTest {
    @Test void identityRequiresCurrentBodyAndEpochEvenWithinSameDimension() {
        var dim = ResourceLocation.parse("minecraft:overworld");
        var bodyA = UUID.randomUUID();
        var bodyB = UUID.randomUUID();
        var identityA = new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, dim, bodyA, 3);
        LocalSpeechClient.clearLiveIdentity();
        LocalSpeechClient.receiveIdentity(identityA, dim, bodyA, 3);
        assertEquals("Alice Smith", LocalSpeechClient.localName());
        LocalSpeechClient.clearLiveIdentity(); // A -> ghost/context transfer retains transcript, clears live identity.
        assertNull(LocalSpeechClient.localName());
        LocalSpeechClient.receiveIdentity(identityA, dim, bodyB, 4); // delayed A packet cannot repopulate in same world
        assertNull(LocalSpeechClient.localName());
        var identityB = new LocalCharacterIdentityPayload("Bobby Jones", 0xffffff, dim, bodyB, 4);
        LocalSpeechClient.receiveIdentity(identityB, dim, bodyB, 4);
        assertEquals("Bobby Jones", LocalSpeechClient.localName());
        LocalSpeechClient.receiveIdentity(identityA, dim, bodyB, 4);
        assertEquals("Bobby Jones", LocalSpeechClient.localName());
    }
    @Test
    void savedIdentityEnablesMentionsBeforeSelfSpeech() {
        var identity = new LocalCharacterIdentityPayload("Alice Smith", 0xffffff,
                ResourceLocation.parse("minecraft:the_nether"), UUID.randomUUID(), 7);
        assertTrue(LocalSpeechTranscript.mentions("Alice, watch out!", identity.name()));
        assertFalse(LocalSpeechTranscript.mentions("malice", identity.name()));
        assertFalse(LocalSpeechTranscript.mentions("Alice, watch out!", null));
    }

    @Test
    void identityCodecRetainsDimensionAndRejectsStaleWorld() {
        var dimension = ResourceLocation.parse("minecraft:the_nether");
        var sent = new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, dimension, UUID.randomUUID(), 41);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalCharacterIdentityPayload.STREAM_CODEC.encode(buffer, sent);
            var received = LocalCharacterIdentityPayload.STREAM_CODEC.decode(buffer);
            assertEquals(sent, received);
            assertEquals(sent.bodyUuid(), received.bodyUuid());
            assertEquals(41, received.epoch());
            assertTrue(received.inDimension(dimension));
            assertFalse(received.inDimension(ResourceLocation.parse("minecraft:overworld")));
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidIdentitiesCannotBeSent() {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var body = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("admin", 0xffffff, dimension, body, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xff000000, dimension, body, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0x000000, dimension, body, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, null, body, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xffffff,
                ResourceLocation.fromNamespaceAndPath("test", "a".repeat(252)), body, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, dimension, body, 0));
        assertThrows(IllegalArgumentException.class, () -> new LocalCharacterIdentityPayload("Alice Smith", 0xffffff, dimension, null, 1));
    }
}
