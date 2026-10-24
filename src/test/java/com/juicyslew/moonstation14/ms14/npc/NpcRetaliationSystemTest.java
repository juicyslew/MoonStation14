package com.juicyslew.moonstation14.ms14.npc;

import com.juicyslew.moonstation14.ms14.character.components.NpcRetaliationComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.UUID;

class NpcRetaliationSystemTest {
    @Test void policyIsOptInAndHasUpstreamMemoryDefault() {
        assertEquals(0, NpcRetaliationComponent.DEFAULT.attackMemorySeconds());
        assertTrue(NpcRetaliationComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\"}")).result().isPresent());
        assertThrows(IllegalArgumentException.class, () -> NpcRetaliationComponent.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"attackMemorySeconds\":-1}")));
    }

    @Test void onlyProvocationCreatesMemoryAndFriendlyTargetsAreIgnored() {
        var memories = new HashMap<UUID, NpcRetaliationSystem.Memory>();
        UUID victim = UUID.randomUUID(), attacker = UUID.randomUUID(), friendly = UUID.randomUUID();
        assertFalse(NpcRetaliationSystem.remember(memories, victim, attacker, 10, 5, true));
        assertNull(NpcRetaliationSystem.rememberedTarget(memories, victim, 10));
        assertTrue(NpcRetaliationSystem.remember(memories, victim, attacker, 10, 5, false));
        assertEquals(attacker, NpcRetaliationSystem.rememberedTarget(memories, victim, 109));
        assertFalse(NpcRetaliationSystem.remember(memories, victim, friendly, 20, 5, true));
        assertEquals(attacker, NpcRetaliationSystem.rememberedTarget(memories, victim, 20));
        assertNull(NpcRetaliationSystem.rememberedTarget(memories, victim, 110));
        assertTrue(memories.isEmpty());
    }

    @Test void zeroMemoryMatchesUnsetUpstreamTimer() {
        var memories = new HashMap<UUID, NpcRetaliationSystem.Memory>();
        UUID victim = UUID.randomUUID(), attacker = UUID.randomUUID();
        assertTrue(NpcRetaliationSystem.remember(memories, victim, attacker, 4, 0, false));
        assertEquals(attacker, NpcRetaliationSystem.rememberedTarget(memories, victim, Long.MAX_VALUE - 1));
    }

    @Test void attackCooldownIsPerNpcAndRateDerived() {
        var attacks = new HashMap<UUID, Long>();
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        assertTrue(NpcRetaliationSystem.acquireAttackCooldown(attacks, one, 10, 20));
        assertFalse(NpcRetaliationSystem.acquireAttackCooldown(attacks, one, 29, 20));
        assertTrue(NpcRetaliationSystem.acquireAttackCooldown(attacks, two, 29, 20));
        assertTrue(NpcRetaliationSystem.acquireAttackCooldown(attacks, one, 30, 20));
    }
}
