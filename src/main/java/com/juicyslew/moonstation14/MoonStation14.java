package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.block.ModBlockEntities;
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

        ModCreativeModeTabs.register(modEventBus);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModDataComponents.register_component(modEventBus);
        ModDataAttachments.register(modEventBus);
        ModEventHooks.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModEntities.register(modEventBus);
        modEventBus.addListener(PrototypeCatalogNetworking::registerPayloadHandlers);
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
        }

        if(event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS){
            event.accept(ModBlocks.STEEL_WALL_BLOCK);
            event.accept(ModBlocks.STEEL_WALL_GIRDER_BLOCK);
            event.accept(ModBlocks.MAGIC_BLOCK);
            event.accept(ModBlocks.JUG);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
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
        PrototypeRuntime.serverManager().clearPublishedCatalogs();
    }
}
