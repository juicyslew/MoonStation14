package com.juicyslew.moonstation14.ms14.npc;

import com.juicyslew.moonstation14.ms14.character.components.NpcRetaliationComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.UUID;

class NpcRetaliationSystemTest {
    @Test void policyIsOptInAndHasUpstreamMemoryDefault() {
        assertEquals(0, NpcRetaliationComponent.DEFAULT.attackMemorySeconds());
        assertEquals(.3, NpcRetaliationComponent.DEFAULT.meleeMissChance());
        assertTrue(NpcRetaliationComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\"}")).result().isPresent());
        assertThrows(IllegalArgumentException.class, () -> NpcRetaliationComponent.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"attackMemorySeconds\":-1}")));
        for (String chance : new String[]{"-0.01", "1.01"}) {
            var json = com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"meleeMissChance\":" + chance + "}");
            assertThrows(IllegalArgumentException.class, () -> NpcRetaliationComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json));
        }
        assertEquals(0, NpcRetaliationComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"meleeMissChance\":0}")).result().orElseThrow().meleeMissChance());
        assertEquals(1, NpcRetaliationComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"meleeMissChance\":1}")).result().orElseThrow().meleeMissChance());
        assertDoesNotThrow(() -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"meleeMissChance\":0.3}").getAsJsonObject(), "$.components[0]"));
        assertThrows(IllegalArgumentException.class, () -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(
                com.google.gson.JsonParser.parseString("{\"type\":\"NpcRetaliation\",\"meleeMissChance\":1.1}").getAsJsonObject(), "$.components[0]"));
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

    @Test void movingTargetMissPolicyHasStationaryAndProbabilityBoundaries() {
        assertFalse(NpcRetaliationSystem.movingTargetMiss(0, 1, 0));
        assertFalse(NpcRetaliationSystem.movingTargetMiss(1, 0, 0));
        assertFalse(NpcRetaliationSystem.movingTargetMiss(1, .3, .3));
        assertTrue(NpcRetaliationSystem.movingTargetMiss(1, .3, .299));
        assertTrue(NpcRetaliationSystem.movingTargetMiss(1, 1, .999));
    }

    @Test void missOffsetUsesUniformAngleAndRadiusBoundedInHorizontalPlane() {
        var east = NpcRetaliationSystem.missOffset(0, .5);
        assertEquals(.25, east.x, 1e-12);
        assertEquals(0, east.y, 1e-12);
        assertEquals(0, east.z, 1e-12);
        var north = NpcRetaliationSystem.missOffset(.25, 1);
        assertEquals(0, north.x, 1e-12);
        assertEquals(0, north.y, 1e-12);
        assertEquals(.5, north.z, 1e-12);
        assertEquals(0, NpcRetaliationSystem.missOffset(.8, 0).length());
        for (double angle : new double[]{0, .13, .25, .5, .77, .99})
            for (double radius : new double[]{0, .1, .5, .9, Math.nextDown(1.0)}) {
                var offset = NpcRetaliationSystem.missOffset(angle, radius);
                assertEquals(0, offset.y);
                assertTrue(offset.length() < .5);
            }
    }
}
