package com.juicyslew.moonstation14.ms14.actions;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.VocalComponent;
import com.juicyslew.moonstation14.ms14.effect.VocalScreamDelivery;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionVocalTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void codecUsesDefaultsAndRejectsInvalidValues() {
        var codec = VocalComponent.CODEC;
        var decoded = codec.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"Vocal\"}")).result().orElseThrow();
        assertEquals("moonstation14:scream", decoded.screamAction());
        assertEquals("scream", decoded.screamEmote());
        assertEquals(id("vocal_human_scream"), decoded.screamSound());
        assertEquals(.125f, decoded.screamPitchVariation());
        assertEquals(.0002f, decoded.wilhelmProbability());

        for (String json : new String[]{
                "{\"type\":\"Vocal\",\"screamSound\":\"not valid id\"}",
                "{\"type\":\"Vocal\",\"screamPitchVariation\":\"NaN\"}",
                "{\"type\":\"Vocal\",\"screamPitchVariation\":1.1}",
                "{\"type\":\"Vocal\",\"wilhelmProbability\":-0.1}",
                "{\"type\":\"Vocal\",\"wilhelmProbability\":\"Infinity\"}"})
            assertTrue(codec.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        assertThrows(IllegalArgumentException.class, () -> new VocalComponent("a", "b", id("sound"), Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new VocalComponent("a", "b", id("sound"), 0, 1.1f));
    }

    @Test void resolvedProductionCompositionsProvideExpectedVocalCapabilities() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, BodyActionDiscoveryTest.resources("character"));
        var characters = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertVocal(characters.get(id("human")), "vocal_human_scream", .0002f);
        assertVocal(characters.get(id("kobold")), "vocal_reptilian_scream", .0002f);
        assertVocal(characters.get(id("scurret")), "vocal_scurret_scream", .01f);
        for (String species : java.util.List.of("bat", "bee", "monkey"))
            assertTrue(characters.get(id(species)).component(VocalComponent.class).isEmpty(), species);
    }

    private static void assertVocal(com.juicyslew.moonstation14.component.codec.json.CharacterData data, String sound, float wilhelm) {
        VocalComponent vocal = data.component(VocalComponent.class).orElseThrow();
        assertEquals(id(sound), vocal.screamSound());
        assertEquals(wilhelm, vocal.wilhelmProbability());
        assertEquals("scream", vocal.screamEmote());
        assertEquals("moonstation14:scream", vocal.screamAction());
    }

    @Test void selectionUsesStrictProbabilityBoundaryAndRejectsInvalidRandom() {
        VocalComponent vocal = new VocalComponent("a", "scream", id("sound"), .125f, .25f);
        assertEquals(VocalScreamDelivery.WILHELM, VocalScreamDelivery.select(vocal, .249999));
        assertEquals(id("sound"), VocalScreamDelivery.select(vocal, .25));
        assertEquals(id("sound"), VocalScreamDelivery.select(new VocalComponent("a", "scream", id("sound"), 0, 0), 0));
        assertEquals(VocalScreamDelivery.WILHELM, VocalScreamDelivery.select(new VocalComponent("a", "scream", id("sound"), 0, 1), .999));
        for (double invalid : new double[]{-1, 1, Double.NaN, Double.POSITIVE_INFINITY})
            assertNull(VocalScreamDelivery.select(vocal, invalid));
    }
}
