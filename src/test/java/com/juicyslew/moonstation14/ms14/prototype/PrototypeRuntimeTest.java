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
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.radio.ModRadioChannels;
import com.juicyslew.moonstation14.ms14.chat.radio.RadioChannelData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosiveSources;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile;
import com.juicyslew.moonstation14.ms14.damage.ModDamageFeedback;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.construction.tool.ModTools;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
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

        var expectedTypes = new java.util.HashSet<>(Set.of(ModReagents.REAGENT_TYPE, ModStatusEffects.STATUS_EFFECT_TYPE,
                ModAlerts.ALERT_TYPE, ModCharacters.CHARACTER_TYPE, ModOrgans.ORGAN_TYPE,
                ModGasReactions.GAS_REACTION_TYPE, ModRadioChannels.RADIO_CHANNEL_TYPE,
                ModVentilationDevices.VENTILATION_DEVICE_TYPE, ModAirAlarms.AIR_ALARM_TYPE,
                ModExplosions.EXPLOSION_TYPE, ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE,
                ModDisposals.DISPOSAL_TYPE, ModTools.TOOL_TYPE, ModLooseObjectProfiles.PROFILE_TYPE, ModItemFootprints.TYPE));
        expectedTypes.add(ModIngestionProfiles.TYPE);
        expectedTypes.add(ModDamageFeedback.TYPE);
        expectedTypes.add(ModAirlocks.TYPE);
        expectedTypes.add(ModToolProfiles.TYPE);
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
        ExplosionData serverExplosion = explosion(2f);
        ExplosionData clientExplosion = explosion(3f);
        server.publishDecoded(ModExplosions.EXPLOSION_TYPE, Map.of(id("server-explosion"), serverExplosion));
        client.publishDecoded(ModExplosions.EXPLOSION_TYPE, Map.of(id("client-explosion"), clientExplosion));
        server.publishDecoded(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(id("server-source"), new ExplosiveSourceData(380, 3, 10, id("default"))));
        client.publishDecoded(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(id("client-source"), new ExplosiveSourceData(120, 3, 10, id("default"))));

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
        assertEquals(serverExplosion, PrototypeRuntime.serverExplosions().get(id("server-explosion")));
        assertFalse(PrototypeRuntime.serverExplosions().contains(id("client-explosion")));
        assertEquals(clientExplosion, PrototypeRuntime.clientExplosions().get(id("client-explosion")));
        assertFalse(PrototypeRuntime.clientExplosions().contains(id("server-explosion")));
        assertTrue(PrototypeRuntime.serverExplosiveSources().contains(id("server-source")));
        assertFalse(PrototypeRuntime.serverExplosiveSources().contains(id("client-source")));
        assertTrue(PrototypeRuntime.clientExplosiveSources().contains(id("client-source")));
        assertFalse(PrototypeRuntime.clientExplosiveSources().contains(id("server-source")));

        server.clearPublishedCatalogs();

        assertEquals(expectedTypes, server.registeredTypes());
        assertEquals(expectedTypes, client.registeredTypes());
        assertTrue(PrototypeRuntime.serverReagents().asMap().isEmpty());
        assertTrue(PrototypeRuntime.clientReagents().contains(id("client")));
        assertTrue(PrototypeRuntime.serverOrgans().asMap().isEmpty());
        assertEquals(heart, PrototypeRuntime.clientOrgans().get(id("client-heart")));
        assertTrue(PrototypeRuntime.serverGasReactions().asMap().isEmpty());
        assertEquals(clientReaction, PrototypeRuntime.clientGasReactions().get(id("client-reaction")));
        assertTrue(PrototypeRuntime.serverExplosions().asMap().isEmpty());
        assertTrue(PrototypeRuntime.serverExplosiveSources().asMap().isEmpty());
        assertEquals(clientExplosion, PrototypeRuntime.clientExplosions().get(id("client-explosion")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    @Test
    void runtimeExportImportsAllFifteenFamiliesIncludingDamageFeedbackWithoutSharingState() {
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
        server.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, Map.of(
                ModVentilationDevices.BASE, ventilation("gas_unary_base"),
                ModVentilationDevices.VENT, ventilation("gas_vent"),
                 ModVentilationDevices.SCRUBBER, ventilation("gas_scrubber")));
        server.reload(ModAirAlarms.AIR_ALARM_TYPE, Map.of(
                ModAirAlarms.WALLMOUNT, alarm("base_wallmount"),
                ModAirAlarms.METALLIC, alarm("base_wallmount_metallic"),
                ModAirAlarms.MACHINE, alarm("base_wallmount_machine"),
                 ModAirAlarms.AIR_ALARM, alarm("air_alarm")));
        server.reload(ModTools.TOOL_TYPE, toolFixtures());
        server.reload(ModDisposals.DISPOSAL_TYPE, disposalFixtures());
        server.reload(ModAirlocks.TYPE, Map.of(ModAirlocks.STANDARD,
                fixture("airlock", "standard_airlock"), id("base_airlock"), fixture("airlock", "base_airlock")));
        server.reload(ModToolProfiles.TYPE, Map.of(id("crowbar"), fixture("tool_profile", "crowbar"),
                id("wirecutter"), fixture("tool_profile", "wirecutter"), id("screwdriver"), fixture("tool_profile", "screwdriver"),
                id("multitool"), fixture("tool_profile", "multitool"), id("airlock_screwdriver"), fixture("tool_profile", "airlock_screwdriver"), id("base_item"), fixture("tool_profile", "base_item"),
                id("base_crowbar"), fixture("tool_profile", "base_crowbar")));
        ExplosionData explosion = explosion(2f);
        server.publishDecoded(ModExplosions.EXPLOSION_TYPE, Map.of(id("explosion-test"), explosion));
        LooseObjectProfile profile = new LooseObjectProfile(1, .5f, .1f, .55f, 0, "sphere", "generic",
                false, 0, 8, .1f, 10, .5f, .5f,
                new LooseObjectProfile.VisualTuning(.5f, .5f, .5f, .5f, 1, 0));
        server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, Map.of(id("profile-test"), profile));
        DamageFeedbackProfile damageFeedback = DamageFeedbackProfile.PHYSICAL;
        server.publishDecoded(ModDamageFeedback.TYPE, Map.of(id("damage-feedback-test"), damageFeedback));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();

        assertEquals(Set.of(ModReagents.REAGENT_TYPE.typeId(), ModStatusEffects.STATUS_EFFECT_TYPE.typeId(),
                ModAlerts.ALERT_TYPE.typeId(), ModCharacters.CHARACTER_TYPE.typeId(), ModOrgans.ORGAN_TYPE.typeId(),
                ModGasReactions.GAS_REACTION_TYPE.typeId(), ModRadioChannels.RADIO_CHANNEL_TYPE.typeId(),
                ModVentilationDevices.VENTILATION_DEVICE_TYPE.typeId(), ModAirAlarms.AIR_ALARM_TYPE.typeId(),
                ModExplosions.EXPLOSION_TYPE.typeId(), ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE.typeId(),
                ModDisposals.DISPOSAL_TYPE.typeId(), ModTools.TOOL_TYPE.typeId(),
                ModLooseObjectProfiles.PROFILE_TYPE.typeId(), ModItemFootprints.TYPE.typeId(),
                ModIngestionProfiles.TYPE.typeId(), ModDamageFeedback.TYPE.typeId(),
                ModAirlocks.TYPE.typeId(), ModToolProfiles.TYPE.typeId()), exported.keySet());
        assertTrue(exported.get(ModTools.TOOL_TYPE.typeId()).keySet().containsAll(Set.of(
                ModTools.WRENCH, ModTools.SCREWDRIVER, ModTools.WELDER)));
        assertTrue(exported.get(ModDisposals.DISPOSAL_TYPE.typeId()).keySet().containsAll(Set.of(
                ModDisposals.UNIT, ModDisposals.HOLDER, ModDisposals.id("disposal_pipe"))));
        assertTrue(exported.get(ModOrgans.ORGAN_TYPE.typeId()).containsKey(id("lungs-test")));
        assertTrue(exported.get(ModGasReactions.GAS_REACTION_TYPE.typeId()).containsKey(id("reaction-test")));
        assertTrue(exported.get(ModRadioChannels.RADIO_CHANNEL_TYPE.typeId()).containsKey(id("common")));
        assertEquals(Set.of(ModVentilationDevices.VENT, ModVentilationDevices.SCRUBBER),
                 exported.get(ModVentilationDevices.VENTILATION_DEVICE_TYPE.typeId()).keySet());
        assertEquals(Set.of(ModAirAlarms.AIR_ALARM), exported.get(ModAirAlarms.AIR_ALARM_TYPE.typeId()).keySet());
        assertTrue(exported.get(ModExplosions.EXPLOSION_TYPE.typeId()).containsKey(id("explosion-test")));
        assertTrue(exported.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).containsKey(id("profile-test")));
        assertTrue(exported.get(ModDamageFeedback.TYPE.typeId()).containsKey(id("damage-feedback-test")));
        ExplosiveSourceData source = new ExplosiveSourceData(380, 3, 10, id("default"));
        server.publishDecoded(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(id("source-test"), source));
        exported = server.encodePublishedCatalogs();
        assertTrue(exported.get(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE.typeId()).containsKey(id("source-test")));

        client.publishEncodedCatalogs(exported);
        var serverWelder = PrototypeRuntime.serverTools().get(ModTools.WELDER);
        assertEquals(serverWelder, PrototypeRuntime.clientTools().get(ModTools.WELDER));
        assertNotSame(serverWelder, PrototypeRuntime.clientTools().get(ModTools.WELDER));
        assertTrue(ModTools.hasQuality(PrototypeRuntime.serverTools(), ModTools.WRENCH,
                com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData.Quality.Anchoring));
        assertEquals(serverWelder.speedModifier(), PrototypeRuntime.clientTools().get(ModTools.WELDER).speedModifier());
        assertEquals(PrototypeRuntime.serverDisposals().get(ModDisposals.UNIT).unit(),
                PrototypeRuntime.clientDisposals().get(ModDisposals.UNIT).unit());
        assertEquals(1000, PrototypeRuntime.clientDisposals().get(ModDisposals.UNIT).unit().powerWatts());
        assertEquals(PrototypeRuntime.serverDisposals().get(ModDisposals.HOLDER).holder(),
                PrototypeRuntime.clientDisposals().get(ModDisposals.HOLDER).holder());
        assertNotSame(PrototypeRuntime.serverDisposals().get(ModDisposals.UNIT),
                PrototypeRuntime.clientDisposals().get(ModDisposals.UNIT));
        assertEquals(status, PrototypeRuntime.clientStatusEffects().get(id("status-test")));
        assertEquals(character, PrototypeRuntime.clientCharacters().get(id("human-test")));
        assertEquals(radio, PrototypeRuntime.clientRadioChannels().get(id("common")));
        assertEquals(lungs, PrototypeRuntime.clientOrgans().get(id("lungs-test")));
        assertEquals(lungs, PrototypeRuntime.serverOrgans().get(id("lungs-test")));
        assertEquals(reaction, PrototypeRuntime.clientGasReactions().get(id("reaction-test")));
        assertEquals(explosion, PrototypeRuntime.clientExplosions().get(id("explosion-test")));
        assertEquals(source, PrototypeRuntime.clientExplosiveSources().get(id("source-test")));
        assertEquals(profile, PrototypeRuntime.clientLooseObjectProfiles().get(id("profile-test")));
        assertEquals(profile, PrototypeRuntime.serverLooseObjectProfiles().get(id("profile-test")));
        assertEquals(damageFeedback, PrototypeRuntime.clientDamageFeedback().get(id("damage-feedback-test")));
        assertEquals(damageFeedback, PrototypeRuntime.serverDamageFeedback().get(id("damage-feedback-test")));
        assertEquals(explosion, ModExplosions.EXPLOSION_TYPE.codec().parse(JsonOps.INSTANCE,
                exported.get(ModExplosions.EXPLOSION_TYPE.typeId()).get(id("explosion-test"))).getOrThrow());
        assertEquals(reaction, PrototypeRuntime.serverGasReactions().get(id("reaction-test")));
        assertEquals(PrototypeRuntime.serverVentilationDevices().asMap(), PrototypeRuntime.clientVentilationDevices().asMap());
        assertEquals(PrototypeRuntime.serverAirAlarms().asMap(), PrototypeRuntime.clientAirAlarms().asMap());
        assertEquals(explosion, PrototypeRuntime.serverExplosions().get(id("explosion-test")));
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
        server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, Map.of(id("profile-server-only"), profile));
        assertEquals(lungs, PrototypeRuntime.clientOrgans().get(id("lungs-test")));
        assertFalse(PrototypeRuntime.clientOrgans().contains(id("heart-test")));
        assertEquals(reaction, PrototypeRuntime.clientGasReactions().get(id("reaction-test")));
        assertFalse(PrototypeRuntime.clientGasReactions().contains(id("new-reaction")));
        assertFalse(PrototypeRuntime.clientLooseObjectProfiles().contains(id("profile-server-only")));

        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
    }

    private static GasReactionData reaction(GasType gas) {
        return new GasReactionData(Map.of(gas, 0.01), 2.7, 1000, 0, 1,
                java.util.List.of(new GasReactionData.Effect(GasReactionData.EffectType.PLASMA_FIRE)));
    }

    private static JsonObject ventilation(String name) {
        String path = "/data/moonstation14/moonstation14/ventilation_device/" + name + ".json";
        try (var stream = PrototypeRuntimeTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static JsonObject alarm(String name) {
        String path = "/data/moonstation14/moonstation14/air_alarm/" + name + ".json";
        try (var stream = PrototypeRuntimeTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Map<ResourceLocation, JsonObject> toolFixtures() {
        Map<ResourceLocation, JsonObject> values = new LinkedHashMap<>();
        for (String name : java.util.List.of("base_tool", "wrench", "screwdriver", "welder"))
            values.put(ModTools.id(name), fixture("construction_tool", name));
        return values;
    }

    private static Map<ResourceLocation, JsonObject> disposalFixtures() {
        Map<ResourceLocation, JsonObject> values = new LinkedHashMap<>();
        try (var paths = java.nio.file.Files.list(java.nio.file.Path.of(
                java.net.URI.create(PrototypeRuntimeTest.class
                        .getResource("/data/moonstation14/moonstation14/disposal_entity").toExternalForm())))) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                String name = path.getFileName().toString().replaceFirst("\\.json$", "");
                values.put(ModDisposals.id(name), fixture("disposal_entity", name));
            });
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
        return values;
    }

    private static JsonObject fixture(String family, String name) {
        String path = "/data/moonstation14/moonstation14/" + family + "/" + name + ".json";
        try (var stream = PrototypeRuntimeTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    private static ExplosionData explosion(float damage) {
        return new ExplosionData(Map.of("blunt", damage), java.util.List.of(0f, 1f),
                java.util.List.of(0f, 1f), 0.5f);
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
