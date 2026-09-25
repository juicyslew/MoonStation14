package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CharacterIdentityTest {
    @Test
    void attachmentCodecRoundTripsOnlyThePrototypeKey() {
        CharacterIdentityComponent component = new CharacterIdentityComponent(ModCharacters.HUMAN_ID);
        var encoded = CharacterIdentityAttachment.CODEC.encodeStart(JsonOps.INSTANCE, component.toAttachment()).getOrThrow();
        assertEquals("\"moonstation14:human\"", encoded.toString());
        assertEquals(component, CharacterIdentityAttachment.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow().toComponent());
        assertEquals(component, CharacterIdentityComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"moonstation14:human\"")).getOrThrow());
    }

    @Test
    void bindingIsIdempotentAndNeverReplacesAnExistingDifferentKey() {
        CharacterIdentityAttachment identity = new CharacterIdentityAttachment();
        identity.bind(ModCharacters.HUMAN_ID);
        identity.bind(ModCharacters.HUMAN_ID);
        assertEquals(ModCharacters.HUMAN_ID, identity.characterId());

        ResourceLocation wrong = ResourceLocation.fromNamespaceAndPath("test", "unknown");
        CharacterIdentityAttachment invalid = new CharacterIdentityAttachment();
        invalid.bind(wrong);
        assertThrows(IllegalStateException.class, () -> invalid.bind(ModCharacters.HUMAN_ID));
        assertEquals(wrong, invalid.characterId());
        assertThrows(IllegalStateException.class, () -> new CharacterIdentityAttachment().toComponent());
    }

    @Test
    void unresolvedWarningIsOncePerCatalogAndMissingId() {
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("test", "missing-warning-test");
        PrototypeCatalog<CharacterData> catalog = new PrototypeCatalog<>(Map.of());
        PrototypeCatalog<CharacterData> reloadedCatalog = new PrototypeCatalog<>(Map.of());

        assertTrue(CharacterIdentitySystem.shouldWarnUnresolved(catalog, missing));
        assertFalse(CharacterIdentitySystem.shouldWarnUnresolved(catalog, missing));
        assertTrue(CharacterIdentitySystem.shouldWarnUnresolved(reloadedCatalog, missing));
    }
}
