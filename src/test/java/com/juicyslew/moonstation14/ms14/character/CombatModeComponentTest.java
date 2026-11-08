package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.character.components.DisarmProneComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatModeComponentTest {
    @Test void defaultsAndExplicitOverridesRoundTrip() {
        var defaults = CharacterComponentRegistry.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"CombatMode\"}")).getOrThrow();
        var mode = (CombatModeComponent) defaults;
        assertEquals(.75f, mode.baseDisarmFailChance());
        assertEquals("moonstation14:ss14/effects/thudswoosh", mode.disarmSuccessSound().orElseThrow());
        assertTrue(mode.canDisarm().isEmpty());
        var explicit = CharacterComponentRegistry.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"CombatMode\",\"base_disarm_fail_chance\":0,\"can_disarm\":false}")).getOrThrow();
        assertEquals(0f, ((CombatModeComponent) explicit).baseDisarmFailChance());
        assertEquals(java.util.Optional.of(false), ((CombatModeComponent) explicit).canDisarm());
        assertInstanceOf(DisarmProneComponent.class, CharacterComponentRegistry.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"DisarmProne\"}")).getOrThrow());
    }
}
