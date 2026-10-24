package com.juicyslew.moonstation14.ms14.chat.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.character.components.ParrotListenerComponent;
import com.juicyslew.moonstation14.ms14.character.components.ParrotMemoryComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParrotCapabilityCodecTest {
    @Test void resolvedPrototypeDefaultsMatchPinnedListenerMemoryAndVocalizerDefaults() {
        var listener = ParrotListenerComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"ParrotListener\"}")).getOrThrow();
        var memory = ParrotMemoryComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"ParrotMemory\"}")).getOrThrow();
        assertEquals(10, listener.hearingRange());
        assertEquals(.6, memory.learnChance());
        assertEquals(1200, memory.learnCooldownTicks());
        assertEquals(50, memory.maxEntries());
        assertEquals(4, memory.minEntryLength());
        assertEquals(50, memory.maxEntryLength());
        assertEquals(2400, memory.minimumReplayIntervalTicks());
        assertEquals(7200, memory.maximumReplayIntervalTicks());
    }

    @Test void invalidMemoryBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParrotMemoryComponent(1.1, 1, 50, 4, 50, 10, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new ParrotListenerComponent(10.1));
    }
}
