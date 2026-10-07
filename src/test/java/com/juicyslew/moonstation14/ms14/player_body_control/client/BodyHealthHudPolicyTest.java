package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyHealthHudPolicyTest {
    private static final UUID CARRIER = new UUID(0, 1);
    private static final UUID BODY = new UUID(0, 2);
    private final Object level = new Object();

    @Test
    void clientHudConsumesResolvedMedicalThresholdsWithoutServerActorLookup() throws Exception {
        String resource = "data/moonstation14/moonstation14/character/human.json";
        var stream = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream);
        com.juicyslew.moonstation14.component.codec.json.CharacterData human;
        try (stream) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            human = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        }
        var policy = com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy.from(human).orElseThrow();
        float critical = (float) policy.thresholds().thresholds().entrySet().stream()
                .filter(entry -> entry.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
        float dead = (float) policy.thresholds().thresholds().entrySet().stream()
                .filter(entry -> entry.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
        assertTrue(BodyHealthHudPolicy.medical(critical, critical, dead).debugLabel().contains("Critical 100.0/200.0 damage · 100.0 to death"));
    }

    @Test
    void medicalHudNormalizesSpeciesThresholdsAndPreservesCriticalProgressUnits() {
        assertEquals("BODY HEALTH 100.0/100", BodyHealthHudPolicy.medical(0, 100, 200).label());
        assertTrue(BodyHealthHudPolicy.medical(100, 100, 200).debugLabel().contains("Critical 100.0/200.0 damage · 100.0 to death"));
        assertTrue(BodyHealthHudPolicy.medical(150, 100, 200).debugLabel().contains("Critical 150.0/200.0 damage · 50.0 to death"));
        assertEquals("BODY HEALTH DEAD", BodyHealthHudPolicy.medical(200, 100, 200).label());
        assertEquals("BODY HEALTH 100.0/100", BodyHealthHudPolicy.medical(0, 10, 20).label());
        assertEquals("BODY HEALTH 50.0/100", BodyHealthHudPolicy.medical(5, 10, 20).label());
        assertEquals("BODY HEALTH 100.0/100", BodyHealthHudPolicy.medical(0, Float.NaN, 20).label());
        assertEquals("BODY HEALTH DEAD", BodyHealthHudPolicy.medical(0, 10, 20,
                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD).label());
        assertTrue(BodyHealthHudPolicy.medical(0, 10, 20,
                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL).debugLabel().contains("Critical 0.0/20.0 damage · 20.0 to death"));
        assertTrue(BodyHealthHudPolicy.medical(200, 100, 200,
                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL).debugLabel().contains("Critical 200.0/200.0 damage · 0.0 to death"));
        assertEquals("BODY HEALTH 0.0/100", BodyHealthHudPolicy.medical(200, 100, 200,
                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE).label());
        assertEquals("BODY HEALTH 0.0/100", BodyHealthHudPolicy.medical(100, 100, 200,
                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE).label());
    }

    private BodyHealthHudPolicy.Key key(long epoch) {
        return new BodyHealthHudPolicy.Key(CARRIER, epoch, level, BODY, 42);
    }

    @Test
    void displaysDecimalProgressAndZeroWithoutPretendingCarrierHealthIsBodyHealth() {
        var hud = new BodyHealthHudPolicy();
        assertEquals("BODY HEALTH 20.0/20.0", hud.observe(key(1), 20f, 20f, 100).label());
        var damaged = hud.observe(key(1), 19.8f, 20f, 101);
        assertEquals("BODY HEALTH 19.8/20.0 - DAMAGE", damaged.label());
        assertTrue(damaged.damageFlash());
        assertEquals("BODY HEALTH 19.99/20.0 - DAMAGE", hud.observe(key(1), 19.99f, 20f, 102).label());
        assertEquals("BODY HEALTH 0.0/20.0 - DAMAGE", hud.observe(key(1), 0f, 20f, 103).label());
        assertFalse(hud.observe(key(1), 0f, 20f, 115).damageFlash());
    }

    @Test
    void rejectsInvalidObservationsAndDoesNotInventDamageOrAuthority() {
        var hud = new BodyHealthHudPolicy();
        assertNull(hud.observe(null, 19f, 20f, 1));
        assertNull(hud.observe(key(1), Float.NaN, 20f, 2));
        assertNull(hud.observe(key(1), 19f, Float.POSITIVE_INFINITY, 3));
        assertNull(hud.observe(key(1), 19f, 0f, 4));
        assertFalse(hud.observe(key(1), 4f, 20f, 5).damageFlash());
        // No packet/health change => no feedback, even if an external sound plays.
        assertFalse(hud.observe(key(1), 4f, 20f, 6).damageFlash());
        hud.reset();
        assertFalse(hud.observe(key(1), 0f, 20f, 7).damageFlash());
    }

    @Test
    void flashIsBoundToExactCarrierEpochLevelAndBodyAndResetsOnCameraLoss() {
        var hud = new BodyHealthHudPolicy();
        hud.observe(key(1), 20f, 20f, 1);
        assertTrue(hud.observe(key(1), 19f, 20f, 2).damageFlash());
        assertFalse(hud.observe(key(2), 18f, 20f, 3).damageFlash());
        assertFalse(hud.observe(new BodyHealthHudPolicy.Key(CARRIER, 2, new Object(), BODY, 42),
                17f, 20f, 4).damageFlash());
        assertFalse(hud.observe(new BodyHealthHudPolicy.Key(CARRIER, 2, level, UUID.randomUUID(), 43),
                16f, 20f, 5).damageFlash());
        assertFalse(hud.observe(new BodyHealthHudPolicy.Key(UUID.randomUUID(), 2, level, BODY, 42),
                15f, 20f, 6).damageFlash());
        assertNull(hud.observe(null, 0f, 20f, 7));
        assertFalse(hud.observe(key(1), 1f, 20f, 8).damageFlash());
    }
}
