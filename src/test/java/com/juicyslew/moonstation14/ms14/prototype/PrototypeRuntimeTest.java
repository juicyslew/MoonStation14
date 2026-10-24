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
import com.juicyslew.moonstation14.ms14.puddle.ModPuddles;
import com.juicyslew.moonstation14.ms14.botany.ModWeightedRandomFillSolutions;
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
        PrototypeManager globalServer = PrototypeRuntime.serverManager();
        PrototypeManager globalClient = PrototypeRuntime.clientManager();
        var globalServerBefore = globalServer.snapshot();
        var globalClientBefore = globalClient.snapshot();
        PrototypeManager server = registeredManager(globalServer);
        PrototypeManager client = registeredManager(globalClient);
        server.clearPublishedCatalogs();


        assertNotSame(server, client);

        var expectedTypes = new java.util.HashSet<>(Set.of(ModReagents.REAGENT_TYPE, ModStatusEffects.STATUS_EFFECT_TYPE,
                ModAlerts.ALERT_TYPE, ModCharacters.CHARACTER_TYPE, ModOrgans.ORGAN_TYPE,
                ModGasReactions.GAS_REACTION_TYPE, ModRadioChannels.RADIO_CHANNEL_TYPE,
                ModVentilationDevices.VENTILATION_DEVICE_TYPE, ModAirAlarms.AIR_ALARM_TYPE,
                ModExplosions.EXPLOSION_TYPE, ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE,
                ModDisposals.DISPOSAL_TYPE, ModTools.TOOL_TYPE, ModLooseObjectProfiles.PROFILE_TYPE, ModItemFootprints.TYPE));
        expectedTypes.add(ModIngestionProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.combat.ModMeleeWeaponProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.combat.ModCartridgeProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.combat.ModProjectileProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.botany.ModBotanyMachineProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.botany.ModTrayProfiles.TYPE);
        expectedTypes.add(ModDamageFeedback.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.medical.ModInjectorModes.TYPE);
        expectedTypes.add(ModAirlocks.TYPE);
        expectedTypes.add(ModToolProfiles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE);
        expectedTypes.add(ModPuddles.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.jug.ModJugs.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ModChemMasters.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.FREQUENCY_TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.NETWORK_TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.PORT_TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.TYPE);
        expectedTypes.add(ModWeightedRandomFillSolutions.TYPE);
        expectedTypes.add(com.juicyslew.moonstation14.ms14.botany.ModPlantMutations.TYPE);
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

        assertTrue(server.snapshot(ModReagents.REAGENT_TYPE).contains(id("server")));
        assertFalse(server.snapshot(ModReagents.REAGENT_TYPE).contains(id("client")));
        assertTrue(client.snapshot(ModReagents.REAGENT_TYPE).contains(id("client")));
        assertFalse(client.snapshot(ModReagents.REAGENT_TYPE).contains(id("server")));
        assertEquals(lungs, server.snapshot(ModOrgans.ORGAN_TYPE).get(id("server-lungs")));
        assertFalse(server.snapshot(ModOrgans.ORGAN_TYPE).contains(id("client-heart")));
        assertEquals(heart, client.snapshot(ModOrgans.ORGAN_TYPE).get(id("client-heart")));
        assertFalse(client.snapshot(ModOrgans.ORGAN_TYPE).contains(id("server-lungs")));
        assertEquals(serverReaction, server.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("server-reaction")));
        assertFalse(server.snapshot(ModGasReactions.GAS_REACTION_TYPE).contains(id("client-reaction")));
        assertEquals(clientReaction, client.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("client-reaction")));
        assertFalse(client.snapshot(ModGasReactions.GAS_REACTION_TYPE).contains(id("server-reaction")));
        assertEquals(serverExplosion, server.snapshot(ModExplosions.EXPLOSION_TYPE).get(id("server-explosion")));
        assertFalse(server.snapshot(ModExplosions.EXPLOSION_TYPE).contains(id("client-explosion")));
        assertEquals(clientExplosion, client.snapshot(ModExplosions.EXPLOSION_TYPE).get(id("client-explosion")));
        assertFalse(client.snapshot(ModExplosions.EXPLOSION_TYPE).contains(id("server-explosion")));
        assertTrue(server.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).contains(id("server-source")));
        assertFalse(server.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).contains(id("client-source")));
        assertTrue(client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).contains(id("client-source")));
        assertFalse(client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).contains(id("server-source")));

        server.clearPublishedCatalogs();

        assertEquals(expectedTypes, server.registeredTypes());
        assertEquals(expectedTypes, client.registeredTypes());
        assertTrue(server.snapshot(ModReagents.REAGENT_TYPE).asMap().isEmpty());
        assertTrue(client.snapshot(ModReagents.REAGENT_TYPE).contains(id("client")));
        assertTrue(server.snapshot(ModOrgans.ORGAN_TYPE).asMap().isEmpty());
        assertEquals(heart, client.snapshot(ModOrgans.ORGAN_TYPE).get(id("client-heart")));
        assertTrue(server.snapshot(ModGasReactions.GAS_REACTION_TYPE).asMap().isEmpty());
        assertEquals(clientReaction, client.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("client-reaction")));
        assertTrue(server.snapshot(ModExplosions.EXPLOSION_TYPE).asMap().isEmpty());
        assertTrue(server.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).asMap().isEmpty());
        assertEquals(clientExplosion, client.snapshot(ModExplosions.EXPLOSION_TYPE).get(id("client-explosion")));

        assertEquals(globalServerBefore, globalServer.snapshot());
        assertEquals(globalClientBefore, globalClient.snapshot());
    }

    @Test
    void runtimeExportImportsAllRegisteredFamiliesIncludingConstructionAirlocksAndMedicalWithoutSharingState() {
        PrototypeManager globalServer = PrototypeRuntime.serverManager();
        PrototypeManager globalClient = PrototypeRuntime.clientManager();
        var globalServerBefore = globalServer.snapshot();
        var globalClientBefore = globalClient.snapshot();
        PrototypeManager server = registeredManager(globalServer);
        PrototypeManager client = registeredManager(globalClient);
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
        server.reload(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE,
                Map.of(id("girder"), fixture("construction_graph", "girder")));
        server.reload(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE,
                constructionFixtures("construction_recipe"));
        server.reload(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE,
                constructionFixtures("construction_entity"));
        server.reload(ModAirlocks.TYPE, Map.of(ModAirlocks.STANDARD,
                fixture("airlock", "standard_airlock"), id("base_airlock"), fixture("airlock", "base_airlock")));
        server.reload(ModToolProfiles.TYPE, Map.of(id("crowbar"), fixture("tool_profile", "crowbar"),
                id("wirecutter"), fixture("tool_profile", "wirecutter"), id("screwdriver"), fixture("tool_profile", "screwdriver"),
                id("multitool"), fixture("tool_profile", "multitool"), id("base_item"), fixture("tool_profile", "base_item"),
                id("base_crowbar"), fixture("tool_profile", "base_crowbar"),
                id("kitchen_knife"), fixture("tool_profile", "kitchen_knife")));
        ExplosionData explosion = explosion(2f);
        server.publishDecoded(ModExplosions.EXPLOSION_TYPE, Map.of(id("explosion-test"), explosion));
        LooseObjectProfile profile = new LooseObjectProfile(1, .5f, .1f, .55f, 0, "sphere", "generic",
                false, 0, 8, .1f, 10, .5f, .5f,
                new LooseObjectProfile.VisualTuning(.5f, .5f, .5f, .5f, 1, 0));
        server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, Map.of(id("profile-test"), profile));
        var fiber = net.minecraft.resources.ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id("fiber"));
        var extractable = new com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile(null,
                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(Map.of(fiber, 3f)));
        server.publishDecoded(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE,
                Map.of(id("cotton_material"), extractable));
        var itemFoodSolution = new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(250,
                Map.of(id("nutriment"), 200L));
        server.publishDecoded(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE,
                Map.of(id("food-only"), itemFoodSolution));
        var seedItemProfile = new com.juicyslew.moonstation14.ms14.botany.SeedItemProfile(id("wheat"));
        server.publishDecoded(com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles.TYPE,
                Map.of(id("wheat_seeds"), seedItemProfile));
        DamageFeedbackProfile damageFeedback = DamageFeedbackProfile.PHYSICAL;
        server.publishDecoded(ModDamageFeedback.TYPE, Map.of(id("damage-feedback-test"), damageFeedback));
        var armorPrototype = new com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData(
                new com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(), java.util.List.of(),
                Optional.of(new com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.Armor(Map.of("blunt", .95f))));
        server.publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE,
                Map.of(id("armor-sync-test"), armorPrototype));
        server.publishDecoded(com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.TYPE,
                Map.of(id("medical-test"), new com.juicyslew.moonstation14.ms14.medical.MedicalItemData(
                        new com.juicyslew.moonstation14.ms14.medical.MedicalItemData.Item("Small"),
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), java.util.Optional.empty())));
        var appearanceShape = new com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData.Shape(0,0,0,16,16,16);
        server.publishDecoded(com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances.TYPE, Map.of(
                com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances.SOURCE,
                new com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData("hv_source","unpowered","fixed_on","charging","full","lack",0,appearanceShape),
                com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances.SUBSTATION,
                new com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData("hv_mv_substation","dead","on","charging","full","lack",0,appearanceShape),
                com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances.APC,
                new com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData("apc","off","on","charging","full","lack",5,appearanceShape)));
        server.reload(com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.TYPE,
                Map.of(
                        com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.BASE_WALLMOUNT, fixture("signal_control", "base_wallmount"),
                        com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.BASE_WALLMOUNT_METALLIC, fixture("signal_control", "base_wallmount_metallic"),
                        com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.SIGNAL_BUTTON, fixture("signal_control", "signal_button"),
                        com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.SIGNAL_SWITCH, fixture("signal_control", "signal_switch")));
        server.reload(com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.TYPE,
                Map.of(com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.id("localbase"), fixture("network_device_item", "localbase"),
                        com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.id("standardwireless15"), fixture("network_device_item", "standardwireless15"),
                        com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.id("configurator"), fixture("network_device_item", "configurator"),
                        com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.id("advancedwireless50"), fixture("network_device_item", "advancedwireless50")));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> exported = server.encodePublishedCatalogs();

        assertEquals(Set.of(ModReagents.REAGENT_TYPE.typeId(), ModStatusEffects.STATUS_EFFECT_TYPE.typeId(),
                ModAlerts.ALERT_TYPE.typeId(), ModCharacters.CHARACTER_TYPE.typeId(), ModOrgans.ORGAN_TYPE.typeId(),
                ModGasReactions.GAS_REACTION_TYPE.typeId(), ModRadioChannels.RADIO_CHANNEL_TYPE.typeId(),
                ModVentilationDevices.VENTILATION_DEVICE_TYPE.typeId(), ModAirAlarms.AIR_ALARM_TYPE.typeId(),
                ModExplosions.EXPLOSION_TYPE.typeId(), ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE.typeId(),
                ModDisposals.DISPOSAL_TYPE.typeId(), ModTools.TOOL_TYPE.typeId(),
                ModLooseObjectProfiles.PROFILE_TYPE.typeId(), ModItemFootprints.TYPE.typeId(),
                 com.juicyslew.moonstation14.ms14.combat.ModMeleeWeaponProfiles.TYPE.typeId(), com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.TYPE.typeId(), com.juicyslew.moonstation14.ms14.combat.ModCartridgeProfiles.TYPE.typeId(), com.juicyslew.moonstation14.ms14.combat.ModProjectileProfiles.TYPE.typeId(), com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.TYPE.typeId(),
                 ModIngestionProfiles.TYPE.typeId(), com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE.typeId(), com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.TYPE.typeId(),
                  com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles.TYPE.typeId(),
                  com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles.TYPE.typeId(),
                  com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE.typeId(),
                  com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE.typeId(),
                 com.juicyslew.moonstation14.ms14.botany.ModBotanyMachineProfiles.TYPE.typeId(),
                 com.juicyslew.moonstation14.ms14.botany.ModTrayProfiles.TYPE.typeId(), ModDamageFeedback.TYPE.typeId(),
                ModAirlocks.TYPE.typeId(), ModToolProfiles.TYPE.typeId(), ModPuddles.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.jug.ModJugs.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.medical.ModInjectorModes.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE.typeId(),
                 com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers.TYPE.typeId(),
                 com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ModChemMasters.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.FREQUENCY_TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.NETWORK_TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.PORT_TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.TYPE.typeId(),
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.TYPE.typeId(),
                 com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE.typeId(),
                 ModWeightedRandomFillSolutions.TYPE.typeId(), com.juicyslew.moonstation14.ms14.botany.ModPlantMutations.TYPE.typeId()), exported.keySet());
        assertTrue(exported.get(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE.typeId()).size() > 0);
        assertTrue(exported.get(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE.typeId()).size() > 0);
        assertTrue(exported.get(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE.typeId()).size() > 0);
        assertEquals("Steel Wall", exported.get(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE.typeId())
                .get(id("steel_wall")).get("name").getAsString(), "Recipe export includes resolved recipe metadata");
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
        assertTrue(exported.get(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE.typeId())
                .containsKey(id("cotton_material")));
        assertEquals(200L, exported.get(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE.typeId())
                .get(id("food-only")).getAsJsonObject().getAsJsonObject("contents_cents").get(id("nutriment").toString()).getAsLong());
        assertEquals(id("wheat").toString(), exported.get(com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles.TYPE.typeId())
                .get(id("wheat_seeds")).get("seed_id").getAsString());
        assertTrue(exported.get(ModDamageFeedback.TYPE.typeId()).containsKey(id("damage-feedback-test")));
        assertEquals(.95f, exported.get(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE.typeId())
                .get(id("armor-sync-test")).getAsJsonObject().getAsJsonObject("armor").getAsJsonObject("coefficients").get("blunt").getAsFloat());
        ExplosiveSourceData source = new ExplosiveSourceData(380, 3, 10, id("default"));
        server.publishDecoded(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(id("source-test"), source));
        exported = server.encodePublishedCatalogs();
        assertTrue(exported.get(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE.typeId()).containsKey(id("source-test")));

        client.publishEncodedCatalogs(exported);
        assertEquals(armorPrototype, client.snapshot(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE).get(id("armor-sync-test")));
        assertNotSame(armorPrototype, client.snapshot(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE).get(id("armor-sync-test")));
        var serverWelder = server.snapshot(ModTools.TOOL_TYPE).get(ModTools.WELDER);
        assertEquals(serverWelder, client.snapshot(ModTools.TOOL_TYPE).get(ModTools.WELDER));
        assertNotSame(serverWelder, client.snapshot(ModTools.TOOL_TYPE).get(ModTools.WELDER));
        assertTrue(ModTools.hasQuality(server.snapshot(ModTools.TOOL_TYPE), ModTools.WRENCH,
                com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData.Quality.Anchoring));
        assertEquals(serverWelder.speedModifier(), client.snapshot(ModTools.TOOL_TYPE).get(ModTools.WELDER).speedModifier());
        assertEquals(server.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit(),
                client.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit());
        assertEquals(1000, client.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit().powerWatts());
        assertEquals(server.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.HOLDER).holder(),
                client.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.HOLDER).holder());
        assertNotSame(server.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT),
                client.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT));
        assertEquals(status, client.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE).get(id("status-test")));
        assertEquals(character, client.snapshot(ModCharacters.CHARACTER_TYPE).get(id("human-test")));
        assertEquals(radio, client.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE).get(id("common")));
        assertEquals(lungs, client.snapshot(ModOrgans.ORGAN_TYPE).get(id("lungs-test")));
        assertEquals(lungs, server.snapshot(ModOrgans.ORGAN_TYPE).get(id("lungs-test")));
        assertEquals(reaction, client.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("reaction-test")));
        assertEquals(explosion, client.snapshot(ModExplosions.EXPLOSION_TYPE).get(id("explosion-test")));
        assertEquals(source, client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).get(id("source-test")));
        assertEquals(profile, client.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(id("profile-test")));
        assertEquals(profile, server.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(id("profile-test")));
        assertEquals(extractable, client.snapshot(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE).get(id("cotton_material")));
        assertEquals(extractable, server.snapshot(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE).get(id("cotton_material")));
        assertEquals(itemFoodSolution, client.snapshot(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE).get(id("food-only")));
        assertEquals(itemFoodSolution, server.snapshot(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE).get(id("food-only")));
        assertEquals(seedItemProfile, client.snapshot(com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles.TYPE).get(id("wheat_seeds")));
        assertEquals(seedItemProfile, server.snapshot(com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles.TYPE).get(id("wheat_seeds")));
        assertNotSame(extractable, client.snapshot(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE).get(id("cotton_material")));
        assertEquals(damageFeedback, client.snapshot(ModDamageFeedback.TYPE).get(id("damage-feedback-test")));
        assertEquals(damageFeedback, server.snapshot(ModDamageFeedback.TYPE).get(id("damage-feedback-test")));
        assertEquals(explosion, ModExplosions.EXPLOSION_TYPE.codec().parse(JsonOps.INSTANCE,
                exported.get(ModExplosions.EXPLOSION_TYPE.typeId()).get(id("explosion-test"))).getOrThrow());
        assertEquals(reaction, server.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("reaction-test")));
        assertEquals(server.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap(), client.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap());
        assertEquals(server.snapshot(ModAirAlarms.AIR_ALARM_TYPE).asMap(), client.snapshot(ModAirAlarms.AIR_ALARM_TYPE).asMap());
        assertEquals(1, client.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE).asMap().size());
        assertEquals(exported.get(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE.typeId()).size(),
                client.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE).asMap().size());
        assertEquals(exported.get(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE.typeId()).size(),
                client.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE).asMap().size());
        assertEquals(server.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE).asMap(), client.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.GRAPH_TYPE).asMap());
        assertEquals(server.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE).asMap(), client.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.RECIPE_TYPE).asMap());
        assertEquals(server.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE).asMap(), client.snapshot(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.ENTITY_TYPE).asMap());
        assertEquals(explosion, server.snapshot(ModExplosions.EXPLOSION_TYPE).get(id("explosion-test")));
        assertTrue(client.snapshot(ModCharacters.CHARACTER_TYPE).contains(id("human-test")));
        assertFalse(client.snapshot(ModCharacters.CHARACTER_TYPE).contains(id("missing")));
        assertTrue(client.snapshot(ModReagents.REAGENT_TYPE).asMap().isEmpty());
        assertTrue(server.snapshot(ModReagents.REAGENT_TYPE).asMap().isEmpty());
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> missingReactionType = new HashMap<>(exported);
        missingReactionType.remove(ModGasReactions.GAS_REACTION_TYPE.typeId());
        assertThrows(IllegalArgumentException.class, () -> client.publishEncodedCatalogs(missingReactionType));
        assertEquals(reaction, client.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("reaction-test")));
        server.publishDecoded(ModOrgans.ORGAN_TYPE, Map.of(id("heart-test"),
                new OrganData(OrganCategory.HEART, Optional.empty())));
        server.publishDecoded(ModGasReactions.GAS_REACTION_TYPE,
                reactions(id("new-reaction"), reaction(GasType.TRITIUM)));
        server.publishDecoded(ModLooseObjectProfiles.PROFILE_TYPE, Map.of(id("profile-server-only"), profile));
        assertEquals(lungs, client.snapshot(ModOrgans.ORGAN_TYPE).get(id("lungs-test")));
        assertFalse(client.snapshot(ModOrgans.ORGAN_TYPE).contains(id("heart-test")));
        assertEquals(reaction, client.snapshot(ModGasReactions.GAS_REACTION_TYPE).get(id("reaction-test")));
        assertFalse(client.snapshot(ModGasReactions.GAS_REACTION_TYPE).contains(id("new-reaction")));
        assertFalse(client.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).contains(id("profile-server-only")));

        assertEquals(globalServerBefore, globalServer.snapshot());
        assertEquals(globalClientBefore, globalClient.snapshot());
    }

    private static PrototypeManager registeredManager(PrototypeManager source) {
        var manager = new PrototypeManager();
        source.registeredTypes().forEach(manager::register);
        return manager;
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
        for (String name : java.util.List.of("base_tool", "wrench", "screwdriver", "welder", "crowbar"))
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

    private static Map<ResourceLocation, JsonObject> constructionFixtures(String family) {
        Map<ResourceLocation, JsonObject> values = new LinkedHashMap<>();
        try (var paths = java.nio.file.Files.list(java.nio.file.Path.of(
                java.net.URI.create(PrototypeRuntimeTest.class
                        .getResource("/data/moonstation14/moonstation14/" + family).toExternalForm())))) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                String name = path.getFileName().toString().replaceFirst("\\.json$", "");
                values.put(com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.id(name), fixture(family, name));
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




