package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EmoteRegistry;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EmoteRegistryTest {
    @Test void registryIsClosedAndCodecRejectsUnknownAndCaseDrift() {
        assertEquals(Set.of("cough", "crying", "hew", "honk", "laugh", "scream", "weh", "whistle", "yawn"), EmoteRegistry.ids());
        assertTrue(EffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"Emote\",\"emote\":\"cough\"}")).result().isPresent());
        assertTrue(EffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"Emote\",\"emote\":\"Sneeze\"}")).error().orElseThrow().message().contains("cough, crying, hew"));
        assertTrue(EmoteRegistry.find("Cough").isEmpty());
        assertTrue(EmoteRegistry.find("anything/arbitrary").isEmpty());
    }

    @Test void guidebookAndForceMetadataRoundTripWithoutChangingRegisteredId() {
        EffectData decoded = EffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\":\"Emote\",\"emote\":\"cough\",\"showinguidebook\":true,\"force\":true}"))
                .getOrThrow();
        EffectData.Emote emote = assertInstanceOf(EffectData.Emote.class, decoded);
        assertTrue(emote.showInGuidebook());
        assertTrue(emote.force());
        assertFalse(emote.showInChat());
        assertEquals("cough", emote.emote());
    }
}
