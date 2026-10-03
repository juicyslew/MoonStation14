package com.juicyslew.moonstation14.ms14.explosion.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExplosionPrototypeTest {
    @Test
    void bundledDefaultRoundTripsAndSyncsBetweenIndependentManagers() throws Exception {
        String path = "data/moonstation14/moonstation14/explosion/default.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            PrototypeManager server = manager();
            server.reload(ModExplosions.EXPLOSION_TYPE, Map.of(ModExplosions.DEFAULT_ID, json));
            ExplosionData data = server.snapshot(ModExplosions.EXPLOSION_TYPE).get(ModExplosions.DEFAULT_ID);
            assertEquals(Map.of(DamageKeys.HEAT, 5f, DamageKeys.BLUNT, 5f, DamageKeys.PIERCING, 5f), data.damagePerIntensity());
            assertEquals(java.util.List.of(0f, 10f, 30f), data.breakIntensities());
            assertEquals(java.util.List.of(0f, .5f, 1f), data.breakChances());
            assertEquals(20f, data.rerollReduction());
            assertEquals(Optional.of(800f), data.temperatureKelvin());
            assertEquals(Optional.empty(), data.fireStacks());
            PrototypeManager client = manager();
            client.publishEncodedCatalogs(server.encodePublishedCatalogs());
            assertEquals(data, client.snapshot(ModExplosions.EXPLOSION_TYPE).get(ModExplosions.DEFAULT_ID));
            assertThrows(IllegalArgumentException.class, () -> ModExplosions.require(client.snapshot(ModExplosions.EXPLOSION_TYPE), id("missing")));
        }
    }

    @Test
    void codecRejectsUnknownOrMissingFieldsAndConstructorRejectsInvalidBounds() {
        JsonObject valid = new JsonObject();
        valid.add("damage_per_intensity", JsonParser.parseString("{\"blunt\":5}") );
        valid.add("break_intensities", JsonParser.parseString("[0,10]"));
        valid.add("break_chances", JsonParser.parseString("[0,1]"));
        valid.addProperty("reroll_reduction", 20);
        assertTrue(ExplosionData.CODEC.parse(JsonOps.INSTANCE, valid).result().isPresent());
        ExplosionData absent = ExplosionData.CODEC.parse(JsonOps.INSTANCE, valid).result().orElseThrow();
        JsonObject absentRoundTrip = ExplosionData.CODEC.encodeStart(JsonOps.INSTANCE, absent).getOrThrow().getAsJsonObject();
        assertFalse(absentRoundTrip.has("temperature_kelvin"));
        assertFalse(absentRoundTrip.has("fire_stacks"));
        JsonObject present = valid.deepCopy(); present.addProperty("temperature_kelvin", 800); present.addProperty("fire_stacks", 2);
        ExplosionData presentData = ExplosionData.CODEC.parse(JsonOps.INSTANCE, present).result().orElseThrow();
        assertEquals(Optional.of(800f), presentData.temperatureKelvin());
        assertEquals(Optional.of(2), presentData.fireStacks());
        assertEquals(presentData, ExplosionData.CODEC.parse(JsonOps.INSTANCE,
                ExplosionData.CODEC.encodeStart(JsonOps.INSTANCE, presentData).getOrThrow()).result().orElseThrow());
        for (String invalid : new String[]{"-1", "1e999", "null", "\"800\""}) {
            JsonObject bad = valid.deepCopy(); bad.add("temperature_kelvin", JsonParser.parseString(invalid));
            assertTrue(ExplosionData.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent());
        }
        for (String invalid : new String[]{"-1", "1.5", "null", "\"2\"", "2147483648"}) {
            JsonObject bad = valid.deepCopy(); bad.add("fire_stacks", JsonParser.parseString(invalid));
            assertTrue(ExplosionData.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent());
        }
        JsonObject zero = valid.deepCopy(); zero.addProperty("temperature_kelvin", 0); zero.addProperty("fire_stacks", 0);
        ExplosionData zeroData = ExplosionData.CODEC.parse(JsonOps.INSTANCE, zero).result().orElseThrow();
        assertEquals(Optional.of(0f), zeroData.temperatureKelvin());
        assertEquals(Optional.of(0), zeroData.fireStacks());
        JsonObject extra = valid.deepCopy(); extra.addProperty("sound", "danger");
        assertTrue(ExplosionData.CODEC.parse(JsonOps.INSTANCE, extra).error().isPresent());
        JsonObject missing = valid.deepCopy(); missing.remove("reroll_reduction");
        assertTrue(ExplosionData.CODEC.parse(JsonOps.INSTANCE, missing).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new ExplosionData(Map.of("unknown", 1f), java.util.List.of(0f, 1f), java.util.List.of(0f, 1f), 1f));
        assertThrows(IllegalArgumentException.class, () -> new ExplosionData(Map.of("blunt", 1f), java.util.List.of(0f, 0f), java.util.List.of(0f, 1f), 1f));
        assertThrows(IllegalArgumentException.class, () -> new ExplosionData(Map.of("blunt", 1f), java.util.List.of(0f, 1f), java.util.List.of(.8f, .2f), 1f));
        assertThrows(IllegalArgumentException.class, () -> new ExplosionData(Map.of("blunt", 1f), java.util.List.of(0f, 1f), java.util.List.of(0f, 1f), 0f));
    }

    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModExplosions.EXPLOSION_TYPE);
        return manager;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
