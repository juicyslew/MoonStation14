package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedMobFactoryTest {
    @Test
    void hostResolutionRejectsUnmappedKillerTomatoUntilCharacterCompositionExists() {
        var catalog = new PrototypeCatalog<com.juicyslew.moonstation14.component.codec.json.CharacterData>(Map.of());
        ResourceLocation tomato = ResourceLocation.fromNamespaceAndPath("moonstation14", "mob_tomato_killer");
        assertTrue(ModCharacters.characterForHost(catalog, tomato).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(catalog,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "mob_tomato_killer"), tomato).isEmpty());
    }
}
