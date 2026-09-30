package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.ModGasReactions;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.radio.ModRadioChannels;
import com.juicyslew.moonstation14.ms14.chat.radio.RadioChannelData;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                ModGasReactions.GAS_REACTION_TYPE, ModRadioChannels.RADIO_CHANNEL_TYPE);
        assertEquals(expectedTypes, server.registeredTypes());
        assertEquals(expectedTypes, client.registeredTypes());

        server.reload(ModReagents.REAGENT_TYPE, Map.of(id("server"), reagent("server")));
        client.reload(ModReagents.REAGENT_TYPE, Map.of(id("client"), reagent("client")));
        OrganData lungs = new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0)));
        OrganData heart = new OrganData(OrganCategory.HEART, Optional.empty());
        server.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("server-lungs"), lungs));
        client.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("client-heart"), heart));
        GasReactionData serverReaction = reaction(GasType.PLASMA);
        GasReactionData clientReaction = reaction(GasType.TRITIUM);
        server.publishDecoded(ModGasReactions.GAS_REACTION_TYPE, reactions(id("server-reaction"), serverReaction));
        client.publishDecoded(ModGasReactions.GAS_REACTION_TYPE, reactions(id("client-reaction"), clientReaction));

        assertTrue(PrototypeRuntime.serverReagents().contains(id("server")));
        assertFalse(PrototypeRuntime.serverReagents().contains(id("client")));
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));
        assertFalse(PrototypeRuntime.clientReagents().contains(id("server")));
        assertEquals(lungs, PrototypeRuntime.serverOrgans().get(id("server-lungs")));
        assertFalse(PrototypeRuntime.serverOrgans().contains(id("client-heart")));
        assertEquals(heart, PrototypeRuntime.clientOrgans().get(id("client-heart")));
        assertFalse(PrototypeRuntime.clientOrgans().contains(id("server-lungs")));
        assertEquals(serverReaction, PrototypeRuntime.serverGasReactions().get(id("server-reaction")));
        assertFalse(PrototypeRuntime.serverGasReactions().contains(id("client-reaction")));
        assertEquals(clientReaction, PrototypeRuntime.clientGasReactions().get(id("client-reaction")));
        assertFalse(PrototypeRuntime.clientGasReactions().contains(id("server-reaction")));

        server.clearPublishedCatalogs();

        assertEquals(expectedTypes, server.registeredTypes());
        assertEquals(expectedTypes, client.registeredTypes());
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));
        assertTrue(PrototypeRuntime.serverOrgans().asMap().isEmpty());
        assertEquals(heart, PrototypeRuntime.clientOrgans().get(id("client-heart")));
        assertTrue(PrototypeRuntime.serverGasReactions().asMap().isEmpty());
        assertEquals(clientReaction, PrototypeRuntime.clientGasReactions().get(id("client-reaction")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    @Test
    void runtimeExportImportsAllSevenFamiliesIncludingRadioOrganAndReactionsWithoutSharingState() {
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
        GasReactionData reaction = reaction(GasType.PLASMA);
        server.publishDecoded(ModGasReactions.GAS_REACTION_TYPE, reactions(id("reaction-test"), reaction));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();
        assertEquals(Set.of(ModReagents.REAGENT_TYPE.typeId(), ModStatusEffects.STATUS_EFFECT_TYPE.typeId(),
                ModAlerts.ALERT_TYPE.typeId(), ModCharacters.CHARACTER_TYPE.typeId(), ModOrgans.ORGAN_TYPE.typeId(),
                ModGasReactions.GAS_REACTION_TYPE.typeId(), ModRadioChannels.RADIO_CHANNEL_TYPE.typeId()),
                exported.keySet());
        assertTrue(exported.get(ModOrgans.ORGAN_TYPE.typeId()).containsKey(id("lungs-test")));
        assertTrue(exported.get(ModGasReactions.GAS_REACTION_TYPE.typeId()).containsKey(id("reaction-test")));
        assertTrue(exported.get(ModRadioChannels.RADIO_CHANNEL_TYPE.typeId()).containsKey(id("common")));

        client.publishEncodedCatalogs(exported);
        assertEquals(status, PrototypeRuntime.clientStatusEffects().get(id("status-test")));
        assertEquals(character, PrototypeRuntime.clientCharacters().get(id("human-test")));
        assertEquals(radio, PrototypeRuntime.clientRadioChannels().get(id("common")));
        assertEquals(lungs, PrototypeRuntime.clientOrgans().get(id("lungs-test")));
        assertEquals(lungs, PrototypeRuntime.serverOrgans().get(id("lungs-test")));
        assertEquals(reaction, PrototypeRuntime.clientGasReactions().get(id("reaction-test")));
        assertEquals(reaction, PrototypeRuntime.serverGasReactions().get(id("reaction-test")));
        assertTrue(PrototypeRuntime.clientCharacters().contains(id("human-test")));
        assertFalse(PrototypeRuntime.clientCharacters().contains(id("missing")));
        assertTrue(PrototypeRuntime.clientReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> missingReactionType = new HashMap<>(exported);
        missingReactionType.remove(ModGasReactions.GAS_REACTION_TYPE.typeId());
        assertThrows(IllegalArgumentException.class, () -> client.publishEncodedCatalogs(missingReactionType));
        assertEquals(reaction, PrototypeRuntime.clientGasReactions().get(id("reaction-test")));
        server.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("heart-test"),
                new OrganData(OrganCategory.HEART, Optional.empty())));
        server.publishDecoded(ModGasReactions.GAS_REACTION_TYPE,
                reactions(id("new-reaction"), reaction(GasType.TRITIUM)));
        assertEquals(lungs, PrototypeRuntime.clientOrgans().get(id("lungs-test")));
        assertFalse(PrototypeRuntime.clientOrgans().contains(id("heart-test")));
        assertEquals(reaction, PrototypeRuntime.clientGasReactions().get(id("reaction-test")));
        assertFalse(PrototypeRuntime.clientGasReactions().contains(id("new-reaction")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    private static GasReactionData reaction(GasType gas) {
        return new GasReactionData(Map.of(gas, 0.01), 2.7, 1000, 0, 1,
                java.util.List.of(new GasReactionData.Effect(GasReactionData.EffectType.PLASMA_FIRE)));
    }

    private static Map<ResourceLocation, GasReactionData> reactions(ResourceLocation extraId, GasReactionData extra) {
        Map<ResourceLocation, GasReactionData> values = new LinkedHashMap<>();
        for (String name : java.util.List.of("frezon_production", "ammonia_oxygen", "frezon_coolant",
                "n2o_decomposition", "tritium_fire", "plasma_fire")) {
            String path = "/data/moonstation14/moonstation14/gas_reaction/" + name + ".json";
            try (var stream = PrototypeRuntimeTest.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing test resource " + path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                values.put(id(name), GasReactionData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }
        values.put(extraId, extra);
        return values;
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
