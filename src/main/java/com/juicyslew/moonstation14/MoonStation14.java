package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.eventhooks.ModEventHooks;
import com.juicyslew.moonstation14.item.ModCreativeModeTabs;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeReloadListener;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogNetworking;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechNetworking;
import com.juicyslew.moonstation14.ms14.chat.server.LocalSpeechServerHooks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereEventHooks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualNetworking;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualServerHooks;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualNetworking;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualServerHooks;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.PowerSimulationGate;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.ui.ApcNetworking;
import com.juicyslew.moonstation14.ms14.station.debug.StarterStationService;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementNetworking;
import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import com.juicyslew.moonstation14.ms14.status_effect.prototype.StatusEffectReferenceValidator;
import com.juicyslew.moonstation14.ms14.alert.prototype.AlertReferenceValidator;
import com.juicyslew.moonstation14.recipe.ModRecipes;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MoonStation14.MOD_ID)
public class MoonStation14 {
    public static final String MOD_ID = "moonstation14";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public MoonStation14(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (MoonStation14) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        LocalSpeechServerHooks.register(NeoForge.EVENT_BUS);
        AtmosphereEventHooks.register(NeoForge.EVENT_BUS);
        AtmosphereVisualServerHooks.register(NeoForge.EVENT_BUS);
        CableVisualServerHooks.register(NeoForge.EVENT_BUS);
        PowerGraphService.register(NeoForge.EVENT_BUS);
        PowerRuntime.register(NeoForge.EVENT_BUS);
        StarterStationService.register(NeoForge.EVENT_BUS);

        ModCreativeModeTabs.register(modEventBus);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModSounds.register(modEventBus);
        ModDataComponents.register_component(modEventBus);
        ModDataAttachments.register(modEventBus);
        ModEventHooks.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModEntities.register(modEventBus);
        modEventBus.addListener(PrototypeCatalogNetworking::registerPayloadHandlers);
        modEventBus.addListener(LocalSpeechNetworking::registerPayloadHandlers);
        modEventBus.addListener(AtmosphereVisualNetworking::registerPayloadHandlers);
        modEventBus.addListener(CableVisualNetworking::registerPayloadHandlers);
        modEventBus.addListener(MovementNetworking::registerPayloadHandlers);
        modEventBus.addListener(ApcNetworking::registerPayloadHandlers);
        MovementServerController.install();
        //ModFluids.register(modEventBus);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS){
            event.accept(ModItems.STEEL);
            event.accept(ModItems.PLASTIC);
            event.accept(ModItems.GLASS);
            event.accept(ModItems.PAPER);
            event.accept(ModItems.CLOTH);
            event.accept(ModItems.WOOD);
            event.accept(ModItems.CROWBAR);
            event.accept(ModItems.WAFFLE);
            event.accept(ModItems.BOTTLE);
            event.accept(ModItems.ATMOSPHERE_ANALYZER);
        }

        if(event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS){
            event.accept(ModBlocks.STEEL_WALL_BLOCK);
            event.accept(ModBlocks.STEEL_WALL_GIRDER_BLOCK);
            event.accept(ModBlocks.MAGIC_BLOCK);
            event.accept(ModBlocks.JUG);
            event.accept(ModBlocks.ATMOS_AIR_PRODUCER);
            event.accept(ModBlocks.ATMOS_OXYGEN_PRODUCER);
            event.accept(ModBlocks.ATMOS_NITROGEN_PRODUCER);
            event.accept(ModBlocks.ATMOS_CARBON_DIOXIDE_PRODUCER);
            event.accept(ModBlocks.ATMOS_PLASMA_PRODUCER);
            event.accept(ModBlocks.ATMOS_TRITIUM_PRODUCER);
            event.accept(ModBlocks.ATMOS_WATER_VAPOR_PRODUCER);
            event.accept(ModBlocks.ATMOS_AMMONIA_PRODUCER);
            event.accept(ModBlocks.ATMOS_NITROUS_OXIDE_PRODUCER);
            event.accept(ModBlocks.ATMOS_FREZON_PRODUCER);
            event.accept(ModBlocks.ATMOS_GAS_SINK);
            event.accept(ModBlocks.ATMOS_HEATER);
            event.accept(ModBlocks.ATMOS_COOLER);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MovementStartupGate.onServerStarting(Config.EXPERIMENTAL_VERTICAL_SLICE_MOVEMENT.get());
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        boolean powerEnabled = Config.ENABLE_POWER_SIMULATION.get();
        PowerSimulationGate.configureAtServerStart(powerEnabled);
        if (!powerEnabled) {
            PowerGraphService.onSimulationDisabled();
            PowerRuntime.onSimulationDisabled();
        }
        AtmosphereService.INSTANCE.configureAtServerStart(Config.ENABLE_ATMOSPHERICS.get(),
                Config.parseAtmosphereVacuumDimensions(Config.ATMOSPHERE_VACUUM_DIMENSIONS.get()));
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Initial datapack loading completes before ServerStartedEvent. This
        // also safely commits an initial candidate if no all-player sync event
        // was emitted during startup.
        PrototypeRuntime.serverManager().commitStagedReload();
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PrototypeReloadListener(PrototypeRuntime.serverManager(),
                encodedCatalogs -> {
                    StatusEffectReferenceValidator.validate(encodedCatalogs);
                    AlertReferenceValidator.validate(encodedCatalogs);
                    PrototypeCatalogNetworking.validateSyncableCatalogs(encodedCatalogs);
                }));
    }

    @SubscribeEvent
    public void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        PrototypeCatalogNetworking.onDatapackSync(event);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        AtmosphereService.INSTANCE.onServerStopped();
        PowerSimulationGate.onServerStopped();
        MovementServerController.onServerStopped();
        MovementStartupGate.onServerStopped();
        PrototypeRuntime.serverManager().clearPublishedCatalogs();
    }
}
