package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototypeRuntimeTest {
    @Test
    void serverAndClientCatalogsAreIndependentAndServerCleanupKeepsRegistrations() {
        PrototypeManager server = PrototypeRuntime.serverManager();
        PrototypeManager client = PrototypeRuntime.clientManager();
        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();

        assertNotSame(server, client);
        assertEquals(4, server.registeredTypes().size());
        assertEquals(4, client.registeredTypes().size());

        server.reload(ModReagents.REAGENT_TYPE, Map.of(id("server"), reagent("server")));
        client.reload(ModReagents.REAGENT_TYPE, Map.of(id("client"), reagent("client")));

        assertTrue(PrototypeRuntime.serverReagents().contains(id("server")));
        assertFalse(PrototypeRuntime.serverReagents().contains(id("client")));
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));
        assertFalse(PrototypeRuntime.clientReagents().contains(id("server")));

        server.clearPublishedCatalogs();

        assertTrue(server.registeredTypes().contains(ModReagents.REAGENT_TYPE));
        assertTrue(server.registeredTypes().contains(com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.STATUS_EFFECT_TYPE));
        assertTrue(server.registeredTypes().contains(ModAlerts.ALERT_TYPE));
        assertTrue(server.registeredTypes().contains(ModCharacters.CHARACTER_TYPE));
        assertTrue(client.registeredTypes().contains(ModReagents.REAGENT_TYPE));
        assertTrue(client.registeredTypes().contains(com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.STATUS_EFFECT_TYPE));
        assertTrue(client.registeredTypes().contains(ModAlerts.ALERT_TYPE));
        assertTrue(client.registeredTypes().contains(ModCharacters.CHARACTER_TYPE));
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    @Test
    void runtimeExportImportsAllFourFamiliesIncludingCharacterWithoutSharingState() {
        PrototypeManager server = PrototypeRuntime.serverManager();
        PrototypeManager client = PrototypeRuntime.clientManager();
        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();

        StatusEffectData status = new StatusEffectData("status.test", false, 0x123456,
                java.util.List.of(StatusEffectBehavior.MARKER),
                java.util.List.of(StatusEffectEligibility.LIVING_ENTITY));
        server.publishDecoded(ModStatusEffects.STATUS_EFFECT_TYPE,
                Map.of(id("status-test"), status));
        var character = new com.juicyslew.moonstation14.component.codec.json.CharacterData(
                new com.juicyslew.moonstation14.component.codec.json.CharacterData.SlipTargetData(
                        true, false, true, true,
                        java.util.List.of(com.juicyslew.moonstation14.component.codec.json.CharacterData.ReactiveGroup.ACIDIC),
                        java.util.List.of(com.juicyslew.moonstation14.component.codec.json.CharacterData.ReactiveMethod.TOUCH)));
        server.publishDecoded(ModCharacters.CHARACTER_TYPE, Map.of(id("human-test"), character));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();
        assertEquals(4, exported.size());
        assertTrue(exported.containsKey(ModReagents.REAGENT_TYPE.typeId()));
        assertTrue(exported.containsKey(ModStatusEffects.STATUS_EFFECT_TYPE.typeId()));
        assertTrue(exported.containsKey(ModAlerts.ALERT_TYPE.typeId()));
        assertTrue(exported.containsKey(ModCharacters.CHARACTER_TYPE.typeId()));

        client.publishEncodedCatalogs(exported);
        assertEquals(status, PrototypeRuntime.clientStatusEffects().get(id("status-test")));
        assertEquals(character, PrototypeRuntime.clientCharacters().get(id("human-test")));
        assertTrue(PrototypeRuntime.clientCharacters().contains(id("human-test")));
        assertFalse(PrototypeRuntime.clientCharacters().contains(id("missing")));
        assertTrue(PrototypeRuntime.clientReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    private static JsonObject reagent(String id) {
        JsonObject reagent = new JsonObject();
        reagent.addProperty("id", id);
        return reagent;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
