package com.juicyslew.moonstation14.ms14.npc;

import com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeleeAttackComponentTest {
    @Test void rangeUsesBoundingBoxEdgeSeparation() {
        // 2.25 center distance minus half-widths (.3 + .2) leaves exactly 1.75 edge reach.
        assertEquals(1.75, NpcRetaliationSystem.edgeDistance(2.25, .6, .4), 1e-9);
        assertTrue(NpcRetaliationSystem.edgeDistance(2.25, .6, .4) <= 1.75);
        assertFalse(NpcRetaliationSystem.edgeDistance(2.2501, .6, .4) <= 1.75);
    }

    @Test void resolvesUpstreamStyleDamageAndAttackRateToPerMobCooldown() {
        var json = com.google.gson.JsonParser.parseString("""
                {"type":"MeleeAttack","damage":{"types":{"blunt":20}},"attackRate":2,"range":1.5}
                """);
        var melee = MeleeAttackComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(20f, melee.damage().types().get("blunt"));
        assertEquals(10, melee.cooldownTicks());
        assertEquals(20, new MeleeAttackComponent(melee.damage(), 1f, 1.5f).cooldownTicks());
    }

    @Test void zeroDamageIsValidAndInvalidReachOrRateAreRejected() {
        var zero = MeleeAttackComponent.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"MeleeAttack\",\"damage\":{\"types\":{\"piercing\":0}}}")).getOrThrow();
        assertTrue(zero.damage().types().isEmpty(), "DamageSpecifierData normalizes zero-valued types");
        assertTrue(MeleeAttackComponent.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"MeleeAttack\",\"damage\":{\"types\":{\"blunt\":5}},\"attackRate\":0}"))
                .error().isPresent());
        assertTrue(MeleeAttackComponent.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"MeleeAttack\",\"damage\":{\"types\":{\"blunt\":5}},\"range\":0}"))
                .error().isPresent());
    }

    @Test void omittedEmptyDamageTypesSurviveResolvedSnapshotSchemaAudit() {
        var resolved = MeleeAttackComponent.CODEC.encodeStart(JsonOps.INSTANCE,
                new MeleeAttackComponent(new com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData(java.util.Map.of()),
                        1f, 1.5f)).getOrThrow().getAsJsonObject();

        assertFalse(resolved.getAsJsonObject("damage").has("types"));
        com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(resolved, "$.components[7]");
        var decoded = MeleeAttackComponent.CODEC.parse(JsonOps.INSTANCE, resolved).getOrThrow();
        assertTrue(decoded.damage().types().isEmpty());
    }

    @Test void malformedPresentDamageTypesRemainRejected() {
        var missingMap = com.google.gson.JsonParser.parseString(
                "{\"type\":\"MeleeAttack\",\"damage\":{\"types\":null}}").getAsJsonObject();
        var unknownKey = com.google.gson.JsonParser.parseString(
                "{\"type\":\"MeleeAttack\",\"damage\":{\"types\":{\"made_up\":1}}}").getAsJsonObject();

        assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(missingMap, "$.components[7]"));
        assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(unknownKey, "$.components[7]"));
    }
}
