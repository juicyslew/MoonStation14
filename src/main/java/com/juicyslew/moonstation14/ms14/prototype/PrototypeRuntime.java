package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.radio.ModRadioChannels;
import com.juicyslew.moonstation14.ms14.chat.radio.RadioChannelData;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.ModGasReactions;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.AirAlarmData;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosiveSources;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.construction.tool.ModTools;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionGraphData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.MachineProfile;
import com.juicyslew.moonstation14.ms14.botany.ModBotanyMachineProfiles;
import com.juicyslew.moonstation14.ms14.botany.TrayProfile;
import com.juicyslew.moonstation14.ms14.botany.ModTrayProfiles;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile;
import com.juicyslew.moonstation14.ms14.damage.ModDamageFeedback;
import com.juicyslew.moonstation14.ms14.medical.ModMedicalItems;
import com.juicyslew.moonstation14.ms14.medical.MedicalItemData;
import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;
import com.juicyslew.moonstation14.ms14.medical.ModInjectorModes;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks;
import com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.ms14.equipment.*;
import com.juicyslew.moonstation14.ms14.puddle.PuddleData;
import com.juicyslew.moonstation14.ms14.puddle.ModPuddles;
import com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneData;
import com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes;
import com.juicyslew.moonstation14.ms14.jug.JugData;
import com.juicyslew.moonstation14.ms14.jug.ModJugs;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerData;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterData;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ModChemMasters;
import com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances;
import com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceFrequency;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPrototype;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePortPrototype;
import com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks;
import com.juicyslew.moonstation14.ms14.devicenet.control.ModSignalControls;
import com.juicyslew.moonstation14.ms14.devicenet.control.SignalControlData;
import com.juicyslew.moonstation14.ms14.devicenet.NetworkDeviceItemData;
import com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems;

/** Common access point for the independent server and client prototype runtimes. */
public final class PrototypeRuntime {
    private static final PrototypeManager SERVER_MANAGER = createManager();
    private static final PrototypeManager CLIENT_MANAGER = createManager();

    private PrototypeRuntime() {
    }

    public static PrototypeManager serverManager() {
        return SERVER_MANAGER;
    }

    public static PrototypeManager clientManager() {
        return CLIENT_MANAGER;
    }

    public static PrototypeCatalog<ReagentData> serverReagents() {
        return SERVER_MANAGER.snapshot(ModReagents.REAGENT_TYPE);
    }

    public static PrototypeCatalog<ReagentData> clientReagents() {
        return CLIENT_MANAGER.snapshot(ModReagents.REAGENT_TYPE);
    }

    public static PrototypeCatalog<StatusEffectData> serverStatusEffects() {
        return SERVER_MANAGER.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE);
    }

    public static PrototypeCatalog<StatusEffectData> clientStatusEffects() {
        return CLIENT_MANAGER.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE);
    }

    public static PrototypeCatalog<AlertData> serverAlerts() {
        return SERVER_MANAGER.snapshot(ModAlerts.ALERT_TYPE);
    }

    public static PrototypeCatalog<AlertData> clientAlerts() {
        return CLIENT_MANAGER.snapshot(ModAlerts.ALERT_TYPE);
    }

    public static PrototypeCatalog<CharacterData> serverCharacters() {
        return SERVER_MANAGER.snapshot(ModCharacters.CHARACTER_TYPE);
    }

    public static PrototypeCatalog<CharacterData> clientCharacters() {
        return CLIENT_MANAGER.snapshot(ModCharacters.CHARACTER_TYPE);
    }

    public static PrototypeCatalog<RadioChannelData> serverRadioChannels() {
        return SERVER_MANAGER.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE);
    }

    public static PrototypeCatalog<RadioChannelData> clientRadioChannels() {
        return CLIENT_MANAGER.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE);
    }

    public static PrototypeCatalog<OrganData> serverOrgans() {
        return SERVER_MANAGER.snapshot(ModOrgans.ORGAN_TYPE);
    }

    public static PrototypeCatalog<OrganData> clientOrgans() {
        return CLIENT_MANAGER.snapshot(ModOrgans.ORGAN_TYPE);
    }

    public static PrototypeCatalog<GasReactionData> serverGasReactions() {
        return SERVER_MANAGER.snapshot(ModGasReactions.GAS_REACTION_TYPE);
    }

    public static PrototypeCatalog<GasReactionData> clientGasReactions() {
        return CLIENT_MANAGER.snapshot(ModGasReactions.GAS_REACTION_TYPE);
    }

    public static PrototypeCatalog<VentilationDeviceData> serverVentilationDevices() {
        return SERVER_MANAGER.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
    }

    public static PrototypeCatalog<VentilationDeviceData> clientVentilationDevices() {
        return CLIENT_MANAGER.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
    }

    public static PrototypeCatalog<AirAlarmData> serverAirAlarms() {
        return SERVER_MANAGER.snapshot(ModAirAlarms.AIR_ALARM_TYPE);
    }

    public static PrototypeCatalog<AirAlarmData> clientAirAlarms() {
        return CLIENT_MANAGER.snapshot(ModAirAlarms.AIR_ALARM_TYPE);
    }
    public static PrototypeCatalog<ExplosionData> serverExplosions() { return SERVER_MANAGER.snapshot(ModExplosions.EXPLOSION_TYPE); }
    public static PrototypeCatalog<ExplosionData> clientExplosions() { return CLIENT_MANAGER.snapshot(ModExplosions.EXPLOSION_TYPE); }

    public static PrototypeCatalog<ExplosiveSourceData> serverExplosiveSources() { return SERVER_MANAGER.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE); }
    public static PrototypeCatalog<ExplosiveSourceData> clientExplosiveSources() { return CLIENT_MANAGER.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE); }
    public static PrototypeCatalog<DisposalEntityData> serverDisposals() { return SERVER_MANAGER.snapshot(ModDisposals.DISPOSAL_TYPE); }
    public static PrototypeCatalog<DisposalEntityData> clientDisposals() { return CLIENT_MANAGER.snapshot(ModDisposals.DISPOSAL_TYPE); }
    public static PrototypeCatalog<ToolPrototypeData> serverTools() { return SERVER_MANAGER.snapshot(ModTools.TOOL_TYPE); }
    public static PrototypeCatalog<ToolPrototypeData> clientTools() { return CLIENT_MANAGER.snapshot(ModTools.TOOL_TYPE); }
    public static PrototypeCatalog<ConstructionGraphData> serverConstructionGraphs() { return SERVER_MANAGER.snapshot(ModConstruction.GRAPH_TYPE); }
    public static PrototypeCatalog<ConstructionRecipeData> serverConstructionRecipes() { return SERVER_MANAGER.snapshot(ModConstruction.RECIPE_TYPE); }
    public static PrototypeCatalog<ConstructionGraphData> clientConstructionGraphs() { return CLIENT_MANAGER.snapshot(ModConstruction.GRAPH_TYPE); }
    public static PrototypeCatalog<ConstructionRecipeData> clientConstructionRecipes() { return CLIENT_MANAGER.snapshot(ModConstruction.RECIPE_TYPE); }
    public static ConstructionCatalog serverConstructionCatalog() { return ModConstruction.catalog(serverConstructionGraphs(),serverConstructionRecipes()); }
    public static ConstructionCatalog clientConstructionCatalog() { return ModConstruction.catalog(clientConstructionGraphs(),clientConstructionRecipes()); }
    public static PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> serverConstructionEntities() { return SERVER_MANAGER.snapshot(ModConstruction.ENTITY_TYPE); }
    public static PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> clientConstructionEntities() { return CLIENT_MANAGER.snapshot(ModConstruction.ENTITY_TYPE); }
    public static PrototypeCatalog<LooseObjectProfile> serverLooseObjectProfiles() { return SERVER_MANAGER.snapshot(ModLooseObjectProfiles.PROFILE_TYPE); }
    public static PrototypeCatalog<LooseObjectProfile> clientLooseObjectProfiles() { return CLIENT_MANAGER.snapshot(ModLooseObjectProfiles.PROFILE_TYPE); }
    public static PrototypeCatalog<ItemFootprintProfile> serverItemFootprints() { return SERVER_MANAGER.snapshot(ModItemFootprints.TYPE); }
    public static PrototypeCatalog<ItemFootprintProfile> clientItemFootprints() { return CLIENT_MANAGER.snapshot(ModItemFootprints.TYPE); }
    public static PrototypeCatalog<IngestionProfile> serverIngestionProfiles() { return SERVER_MANAGER.snapshot(ModIngestionProfiles.TYPE); }
    public static PrototypeCatalog<IngestionProfile> clientIngestionProfiles() { return CLIENT_MANAGER.snapshot(ModIngestionProfiles.TYPE); }
    public static PrototypeCatalog<SeedProfile> serverSeedProfiles() { return SERVER_MANAGER.snapshot(ModSeedProfiles.TYPE); }
    public static PrototypeCatalog<SeedProfile> clientSeedProfiles() { return CLIENT_MANAGER.snapshot(ModSeedProfiles.TYPE); }
    public static PrototypeCatalog<ProduceProfile> serverProduceProfiles() { return SERVER_MANAGER.snapshot(ModProduceProfiles.TYPE); }
    public static PrototypeCatalog<ProduceProfile> clientProduceProfiles() { return CLIENT_MANAGER.snapshot(ModProduceProfiles.TYPE); }
    public static PrototypeCatalog<MachineProfile> serverBotanyMachineProfiles() { return SERVER_MANAGER.snapshot(ModBotanyMachineProfiles.TYPE); }
    public static PrototypeCatalog<MachineProfile> clientBotanyMachineProfiles() { return CLIENT_MANAGER.snapshot(ModBotanyMachineProfiles.TYPE); }
    public static PrototypeCatalog<TrayProfile> serverTrayProfiles() { return SERVER_MANAGER.snapshot(ModTrayProfiles.TYPE); }
    public static PrototypeCatalog<TrayProfile> clientTrayProfiles() { return CLIENT_MANAGER.snapshot(ModTrayProfiles.TYPE); }
    public static PrototypeCatalog<DamageFeedbackProfile> serverDamageFeedback() { return SERVER_MANAGER.snapshot(ModDamageFeedback.TYPE); }
    public static PrototypeCatalog<DamageFeedbackProfile> clientDamageFeedback() { return CLIENT_MANAGER.snapshot(ModDamageFeedback.TYPE); }
    public static PrototypeCatalog<MedicalItemData> serverMedicalItems() { return SERVER_MANAGER.snapshot(ModMedicalItems.TYPE); }
    public static PrototypeCatalog<MedicalItemData> clientMedicalItems() { return CLIENT_MANAGER.snapshot(ModMedicalItems.TYPE); }
    public static PrototypeCatalog<InjectorModeData> serverInjectorModes() { return SERVER_MANAGER.snapshot(ModInjectorModes.TYPE); }
    public static PrototypeCatalog<InjectorModeData> clientInjectorModes() { return CLIENT_MANAGER.snapshot(ModInjectorModes.TYPE); }
    public static PrototypeCatalog<AirlockData> serverAirlocks() { return SERVER_MANAGER.snapshot(ModAirlocks.TYPE); }
    public static PrototypeCatalog<AirlockData> clientAirlocks() { return CLIENT_MANAGER.snapshot(ModAirlocks.TYPE); }
    public static PrototypeCatalog<InventoryTemplateData> serverInventoryTemplates() { return SERVER_MANAGER.snapshot(ModInventoryTemplates.TYPE); }
    public static PrototypeCatalog<InventoryTemplateData> clientInventoryTemplates() { return CLIENT_MANAGER.snapshot(ModInventoryTemplates.TYPE); }
    public static PrototypeCatalog<EquipmentItemData> serverEquipmentItems() { return SERVER_MANAGER.snapshot(ModEquipmentItems.TYPE); }
    public static PrototypeCatalog<EquipmentItemData> clientEquipmentItems() { return CLIENT_MANAGER.snapshot(ModEquipmentItems.TYPE); }
    public static PrototypeCatalog<PuddleData> serverPuddles() { return SERVER_MANAGER.snapshot(ModPuddles.TYPE); }
    public static PrototypeCatalog<PuddleData> clientPuddles() { return CLIENT_MANAGER.snapshot(ModPuddles.TYPE); }
    public static PrototypeCatalog<CelestialSceneData> serverCelestialScenes() { return SERVER_MANAGER.snapshot(ModCelestialScenes.TYPE); }
    public static PrototypeCatalog<CelestialSceneData> clientCelestialScenes() { return CLIENT_MANAGER.snapshot(ModCelestialScenes.TYPE); }
    public static PrototypeCatalog<JugData> serverJugs() { return SERVER_MANAGER.snapshot(ModJugs.TYPE); }
    public static PrototypeCatalog<JugData> clientJugs() { return CLIENT_MANAGER.snapshot(ModJugs.TYPE); }
    public static PrototypeCatalog<BeakerData> serverBeakers() { return SERVER_MANAGER.snapshot(ModBeakers.TYPE); }
    public static PrototypeCatalog<BeakerData> clientBeakers() { return CLIENT_MANAGER.snapshot(ModBeakers.TYPE); }
    public static PrototypeCatalog<com.juicyslew.moonstation14.ms14.chemistry.output.ChemContainerData> serverChemContainers() { return SERVER_MANAGER.snapshot(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.TYPE); }
    public static PrototypeCatalog<com.juicyslew.moonstation14.ms14.chemistry.output.ChemContainerData> clientChemContainers() { return CLIENT_MANAGER.snapshot(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.TYPE); }
    public static PrototypeCatalog<ChemMasterData> serverChemMasters() { return SERVER_MANAGER.snapshot(ModChemMasters.TYPE); }
    public static PrototypeCatalog<ChemMasterData> clientChemMasters() { return CLIENT_MANAGER.snapshot(ModChemMasters.TYPE); }
    public static PrototypeCatalog<PowerAppearanceData> serverPowerAppearances() { return SERVER_MANAGER.snapshot(ModPowerAppearances.TYPE); }
    public static PrototypeCatalog<PowerAppearanceData> clientPowerAppearances() { return CLIENT_MANAGER.snapshot(ModPowerAppearances.TYPE); }
    public static PrototypeCatalog<DeviceFrequency> serverDeviceFrequencies() { return SERVER_MANAGER.snapshot(ModDeviceNetworks.FREQUENCY_TYPE); }
    public static PrototypeCatalog<DeviceFrequency> clientDeviceFrequencies() { return CLIENT_MANAGER.snapshot(ModDeviceNetworks.FREQUENCY_TYPE); }
    public static PrototypeCatalog<DeviceNetworkPrototype> serverDeviceNetworks() { return SERVER_MANAGER.snapshot(ModDeviceNetworks.NETWORK_TYPE); }
    public static PrototypeCatalog<DeviceNetworkPrototype> clientDeviceNetworks() { return CLIENT_MANAGER.snapshot(ModDeviceNetworks.NETWORK_TYPE); }
    public static PrototypeCatalog<DevicePortPrototype> serverDevicePorts() { return SERVER_MANAGER.snapshot(ModDeviceNetworks.PORT_TYPE); }
    public static PrototypeCatalog<DevicePortPrototype> clientDevicePorts() { return CLIENT_MANAGER.snapshot(ModDeviceNetworks.PORT_TYPE); }
    public static PrototypeCatalog<SignalControlData> serverSignalControls() { return SERVER_MANAGER.snapshot(ModSignalControls.TYPE); }
    public static PrototypeCatalog<SignalControlData> clientSignalControls() { return CLIENT_MANAGER.snapshot(ModSignalControls.TYPE); }
    public static PrototypeCatalog<NetworkDeviceItemData> serverNetworkDeviceItems() { return SERVER_MANAGER.snapshot(ModNetworkDeviceItems.TYPE); }
    public static PrototypeCatalog<NetworkDeviceItemData> clientNetworkDeviceItems() { return CLIENT_MANAGER.snapshot(ModNetworkDeviceItems.TYPE); }

    private static PrototypeManager createManager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.register(ModStatusEffects.STATUS_EFFECT_TYPE);
        manager.register(ModAlerts.ALERT_TYPE);
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModRadioChannels.RADIO_CHANNEL_TYPE);
        manager.register(ModOrgans.ORGAN_TYPE);
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        manager.register(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        manager.register(ModAirAlarms.AIR_ALARM_TYPE);
        manager.register(ModExplosions.EXPLOSION_TYPE);
        manager.register(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE);
        manager.register(ModDisposals.DISPOSAL_TYPE);
        manager.register(ModTools.TOOL_TYPE);
        manager.register(ModConstruction.GRAPH_TYPE);
        manager.register(ModConstruction.RECIPE_TYPE);
        manager.register(ModConstruction.ENTITY_TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModItemFootprints.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE);
        manager.register(ModProduceProfiles.TYPE);
        manager.register(ModBotanyMachineProfiles.TYPE);
        manager.register(ModTrayProfiles.TYPE);
        manager.register(ModDamageFeedback.TYPE);
        manager.register(ModMedicalItems.TYPE);
        manager.register(ModInjectorModes.TYPE);
        manager.register(ModAirlocks.TYPE);
        manager.register(ModToolProfiles.TYPE);
        manager.register(ModInventoryTemplates.TYPE);
        manager.register(ModEquipmentItems.TYPE);
        manager.register(ModPuddles.TYPE);
        manager.register(ModCelestialScenes.TYPE);
        manager.register(ModJugs.TYPE);
        manager.register(ModBeakers.TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.TYPE);
        manager.register(ModChemMasters.TYPE);
        manager.register(ModPowerAppearances.TYPE);
        manager.register(ModDeviceNetworks.FREQUENCY_TYPE);
        manager.register(ModDeviceNetworks.NETWORK_TYPE);
        manager.register(ModDeviceNetworks.PORT_TYPE);
        manager.register(ModSignalControls.TYPE);
        manager.register(ModNetworkDeviceItems.TYPE);
        return manager;
    }
}
