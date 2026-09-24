package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
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
        assertEquals(3, server.registeredTypes().size());
        assertEquals(3, client.registeredTypes().size());

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
        assertTrue(client.registeredTypes().contains(ModReagents.REAGENT_TYPE));
        assertTrue(client.registeredTypes().contains(com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.STATUS_EFFECT_TYPE));
        assertTrue(client.registeredTypes().contains(ModAlerts.ALERT_TYPE));
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    @Test
    void completeTwoTypeRuntimeExportImportsStatusCatalogWithoutSharingState() {
        PrototypeManager server = PrototypeRuntime.serverManager();
        PrototypeManager client = PrototypeRuntime.clientManager();
        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();

        StatusEffectData status = new StatusEffectData("status.test", false, 0x123456,
                java.util.List.of(StatusEffectBehavior.MARKER),
                java.util.List.of(StatusEffectEligibility.LIVING_ENTITY));
        server.publishDecoded(ModStatusEffects.STATUS_EFFECT_TYPE,
                Map.of(id("status-test"), status));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();
        assertEquals(3, exported.size());
        assertTrue(exported.containsKey(ModReagents.REAGENT_TYPE.typeId()));
        assertTrue(exported.containsKey(ModStatusEffects.STATUS_EFFECT_TYPE.typeId()));
        assertTrue(exported.containsKey(ModAlerts.ALERT_TYPE.typeId()));

        client.publishEncodedCatalogs(exported);
        assertEquals(status, PrototypeRuntime.clientStatusEffects().get(id("status-test")));
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
