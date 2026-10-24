package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.character.components.UdderComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UdderComponentTest {
    @Test void componentUsesUpstreamDefaultsAndRoundTripsThroughRegistry() {
        var defaults = UdderComponent.DEFAULT;
        assertEquals(25f, defaults.quantity());
        assertEquals(10f, defaults.hungerUsage());
        assertEquals(60f, defaults.growthDelaySeconds());
        assertEquals(250f, defaults.solutionCapacity());
        assertEquals("udder", defaults.solutionName());
        var parsed = CharacterComponentRegistry.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"Udder\",\"growth_delay_seconds\":30,\"solution_capacity\":250}"))
                .result().orElseThrow();
        assertInstanceOf(UdderComponent.class, parsed);
        assertEquals(30f, ((UdderComponent) parsed).growthDelaySeconds());
    }
}
