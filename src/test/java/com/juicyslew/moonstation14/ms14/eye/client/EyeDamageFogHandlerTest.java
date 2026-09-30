package com.juicyslew.moonstation14.ms14.eye.client;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.eye.EyeDamageVision;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyeDamageFogHandlerTest {
    private static final ResourceLocation ID = ResourceLocation.parse("moonstation14:fog_test");
    private static final ResourceLocation HOST = ResourceLocation.parse("minecraft:pig");
    private static final ResourceLocation OTHER_HOST = ResourceLocation.parse("minecraft:cow");

    @Test
    void boundBlindableHostProjectsFogOnlyWhenRawStateIsBlind() {
        var catalog = catalog("{\"host_entity_types\":[\"minecraft:pig\"],\"components\":[{\"type\":\"Blindable\"}]}");
        assertTrue(EyeDamageFogHandler.eligibleBlind(catalog, ID, HOST, true));
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog, ID, HOST, false));
        assertTrue(EyeDamageVision.projectFog(2f, 20f,
                EyeDamageFogHandler.eligibleBlind(catalog, ID, HOST, true), false).changed());
        var noChange = EyeDamageVision.projectFog(2f, 20f,
                EyeDamageFogHandler.eligibleBlind(catalog, ID, HOST, false), true);
        assertFalse(noChange.changed());
        assertTrue(noChange.canceled());
    }

    @Test
    void retainedBlindStateDoesNotProjectWithoutCurrentMarker() {
        var previous = catalog("{\"host_entity_types\":[\"minecraft:pig\"],\"components\":[{\"type\":\"Blindable\"}]}");
        var catalog = catalog("{\"host_entity_types\":[\"minecraft:pig\"]}");
        assertTrue(EyeDamageFogHandler.eligibleBlind(previous, ID, HOST, true));
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog, ID, HOST, true));
        var projection = EyeDamageVision.projectFog(2f, 20f,
                EyeDamageFogHandler.eligibleBlind(catalog, ID, HOST, true), false);
        assertFalse(projection.changed());
        assertFalse(projection.canceled());
    }

    @Test
    void retainedBlindStateDoesNotProjectOnHostMismatchOrDanglingBinding() {
        var catalog = catalog("{\"host_entity_types\":[\"minecraft:pig\"],\"components\":[{\"type\":\"Blindable\"}]}");
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog, ID, OTHER_HOST, true));
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog,
                ResourceLocation.parse("moonstation14:missing"), HOST, true));
    }

    @Test
    void missingClientCatalogOrUnboundIdentityFailsClosed() {
        var catalog = catalog("{\"host_entity_types\":[\"minecraft:pig\"],\"components\":[{\"type\":\"Blindable\"}]}");
        assertFalse(EyeDamageFogHandler.eligibleBlind(null, ID, HOST, true));
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog, null, HOST, true));
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog, ID, null, true));
    }

    @Test
    void entityProjectionMayPresentBlindnessWithoutHostOwnership() {
        var catalog = catalog("{\"host_entity_types\":[\"minecraft:pig\"],\"components\":[{\"type\":\"Blindable\"}]}");
        assertFalse(EyeDamageFogHandler.eligibleBlind(catalog, ID, OTHER_HOST, true));
        assertTrue(EyeDamageFogHandler.eligibleBlind(Optional.of(catalog.get(ID)), true));
        assertFalse(EyeDamageFogHandler.eligibleBlind(Optional.of(catalog.get(ID)), false));
        assertFalse(EyeDamageFogHandler.eligibleBlind(Optional.empty(), true));
    }

    private static PrototypeCatalog<CharacterData> catalog(String json) {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ID, JsonParser.parseString(json).getAsJsonObject()));
        return manager.snapshot(ModCharacters.CHARACTER_TYPE);
    }
}
