package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.radio.ModRadioChannels;
import com.juicyslew.moonstation14.ms14.chat.radio.RadioChannelData;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        var expectedTypes = Set.of(ModReagents.REAGENT_TYPE, ModStatusEffects.STATUS_EFFECT_TYPE,
                ModAlerts.ALERT_TYPE, ModCharacters.CHARACTER_TYPE, ModOrgans.ORGAN_TYPE,
                ModRadioChannels.RADIO_CHANNEL_TYPE);
        assertEquals(expectedTypes, server.registeredTypes());
        assertEquals(expectedTypes, client.registeredTypes());

        server.reload(ModReagents.REAGENT_TYPE, Map.of(id("server"), reagent("server")));
        client.reload(ModReagents.REAGENT_TYPE, Map.of(id("client"), reagent("client")));
        OrganData lungs = new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0)));
        OrganData heart = new OrganData(OrganCategory.HEART, Optional.empty());
        server.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("server-lungs"), lungs));
        client.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("client-heart"), heart));

        assertTrue(PrototypeRuntime.serverReagents().contains(id("server")));
        assertFalse(PrototypeRuntime.serverReagents().contains(id("client")));
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));
        assertFalse(PrototypeRuntime.clientReagents().contains(id("server")));
        assertEquals(lungs, PrototypeRuntime.serverOrgans().get(id("server-lungs")));
        assertFalse(PrototypeRuntime.serverOrgans().contains(id("client-heart")));
        assertEquals(heart, PrototypeRuntime.clientOrgans().get(id("client-heart")));
        assertFalse(PrototypeRuntime.clientOrgans().contains(id("server-lungs")));

        server.clearPublishedCatalogs();

        assertEquals(expectedTypes, server.registeredTypes());
        assertEquals(expectedTypes, client.registeredTypes());
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));
        assertTrue(PrototypeRuntime.serverOrgans().asMap().isEmpty());
        assertEquals(heart, PrototypeRuntime.clientOrgans().get(id("client-heart")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    @Test
    void runtimeExportImportsAllSixFamiliesIncludingRadioAndOrganWithoutSharingState() {
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
                java.util.List.of(), java.util.List.of(
                        new com.juicyslew.moonstation14.ms14.character.components.StunnableComponent(),
                        new com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent(
                                java.util.List.of(com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent.ReactiveGroup.ACIDIC),
                                java.util.List.of(com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent.ReactiveMethod.TOUCH))));
        server.publishDecoded(ModCharacters.CHARACTER_TYPE, Map.of(id("human-test"), character));
        RadioChannelData radio = new RadioChannelData("radio-channel.moonstation14.common", 'h', 0x98C5E3);
        server.publishDecoded(ModRadioChannels.RADIO_CHANNEL_TYPE, Map.of(id("common"), radio));
        OrganData lungs = new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0)));
        server.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("lungs-test"), lungs));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();
        assertEquals(Set.of(ModReagents.REAGENT_TYPE.typeId(), ModStatusEffects.STATUS_EFFECT_TYPE.typeId(),
                ModAlerts.ALERT_TYPE.typeId(), ModCharacters.CHARACTER_TYPE.typeId(), ModOrgans.ORGAN_TYPE.typeId(),
                ModRadioChannels.RADIO_CHANNEL_TYPE.typeId()),
                exported.keySet());
        assertTrue(exported.get(ModOrgans.ORGAN_TYPE.typeId()).containsKey(id("lungs-test")));
        assertTrue(exported.get(ModRadioChannels.RADIO_CHANNEL_TYPE.typeId()).containsKey(id("common")));

        client.publishEncodedCatalogs(exported);
        assertEquals(status, PrototypeRuntime.clientStatusEffects().get(id("status-test")));
        assertEquals(character, PrototypeRuntime.clientCharacters().get(id("human-test")));
        assertEquals(radio, PrototypeRuntime.clientRadioChannels().get(id("common")));
        assertEquals(lungs, PrototypeRuntime.clientOrgans().get(id("lungs-test")));
        assertEquals(lungs, PrototypeRuntime.serverOrgans().get(id("lungs-test")));
        assertTrue(PrototypeRuntime.clientCharacters().contains(id("human-test")));
        assertFalse(PrototypeRuntime.clientCharacters().contains(id("missing")));
        assertTrue(PrototypeRuntime.clientReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        server.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("heart-test"),
                new OrganData(OrganCategory.HEART, Optional.empty())));
        assertEquals(lungs, PrototypeRuntime.clientOrgans().get(id("lungs-test")));
        assertFalse(PrototypeRuntime.clientOrgans().contains(id("heart-test")));

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
