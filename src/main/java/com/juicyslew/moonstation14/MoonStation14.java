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
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundNetworking;
import com.juicyslew.moonstation14.ms14.chat.server.LocalSpeechServerHooks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereEventHooks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualNetworking;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualServerHooks;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualNetworking;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualServerHooks;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.ui.ApcNetworking;
import com.juicyslew.moonstation14.ms14.station.debug.StarterStationService;
import com.juicyslew.moonstation14.ms14.status_effect.prototype.StatusEffectReferenceValidator;
import com.juicyslew.moonstation14.ms14.alert.prototype.AlertReferenceValidator;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadeHeldFuseService;
import com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeAnchorUse;
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
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.disposal.fixture.DisposalFixtureCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.fixture.ChemMasterFixtureCommands::registerCommands);
        LocalSpeechServerHooks.register(NeoForge.EVENT_BUS);
        AtmosphereEventHooks.register(NeoForge.EVENT_BUS);
        AtmosphereVisualServerHooks.register(NeoForge.EVENT_BUS);
        CableVisualServerHooks.register(NeoForge.EVENT_BUS);
        PowerGraphService.register(NeoForge.EVENT_BUS);
        PowerRuntime.register(NeoForge.EVENT_BUS);
        StarterStationService.register(NeoForge.EVENT_BUS);
        BodyGrenadeHeldFuseService.register(NeoForge.EVENT_BUS);
        BodyDisposalTubeAnchorUse.register(NeoForge.EVENT_BUS);
        com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeJunctionUse.register(NeoForge.EVENT_BUS);
        com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeWelderUse.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildMenuService::playerTick);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenuService::serverTick);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenuService::typedDamageApplied);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalRideDamageDiagnostics::onTypedDamage);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService::serverStopped);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService::serverTick);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService::logout);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpService::logout);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpService::serverStopped);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpService::serverTick);
        com.juicyslew.moonstation14.entities.LooseMotionDiagnostics.register(NeoForge.EVENT_BUS);
        com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService.register(NeoForge.EVENT_BUS);
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
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.ghost_roles.server.GhostRoleNetworking::registerPayloadHandlers);
        com.juicyslew.moonstation14.ms14.ghost_roles.server.GhostRoleNetworking.installDefaultServerHandler();
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.damage.DamageFeedbackNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.combat.CombatModeNetworking::registerPayloadHandlers);
        modEventBus.addListener(LocalSpeechNetworking::registerPayloadHandlers);
        modEventBus.addListener(GrenadeSoundNetworking::registerPayloadHandlers);
        modEventBus.addListener(AtmosphereVisualNetworking::registerPayloadHandlers);
        modEventBus.addListener(CableVisualNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.disposal.network.DisposalTubeVisualNetworking::registerPayloadHandlers);
        modEventBus.addListener(ApcNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.botany.ui.GrinderNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.botany.GrinderProcessingAudioNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.medical.MedicalNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.ingestion.IngestionNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.examine.ExamineNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.stripping.StripNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.medical_observer.ObserverNetworking::registerPayloadHandlers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.standing.PostureNetworking::registerPayloadHandlers);
        com.juicyslew.moonstation14.ms14.disposal.network.DisposalTubeVisualServerHooks.install();
        NeoForge.EVENT_BUS.register(com.juicyslew.moonstation14.ms14.disposal.network.DisposalTubeVisualServerHooks.class);
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
            event.accept(ModItems.WRENCH);
            event.accept(ModItems.SCREWDRIVER);
            event.accept(ModItems.WELDER);
            event.accept(ModItems.WAFFLE);
            event.accept(ModItems.CHEESE_SLICE);
            event.accept(ModItems.BOTTLE);
            event.accept(ModItems.ATMOSPHERE_ANALYZER);
        }

        if(event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS){
            event.accept(ModBlocks.DISPOSAL_UNIT);
            event.accept(ModBlocks.STEEL_WALL_BLOCK);
            event.accept(ModBlocks.STEEL_WALL_GIRDER_BLOCK);
            event.accept(ModBlocks.MAGIC_BLOCK);
            event.accept(ModItems.JUG);
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
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        AtmosphereService.INSTANCE.configureAtServerStart(
                Config.parseAtmosphereVacuumDimensions(Config.ATMOSPHERE_VACUUM_DIMENSIONS.get()),
                Config.parseAtmosphereSpaceDimensions(Config.ATMOSPHERE_SPACE_DIMENSIONS.get()));
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Initial datapack loading completes before ServerStartedEvent. This
        // also safely commits an initial candidate if no all-player sync event
        // was emitted during startup.
        if (PrototypeRuntime.serverManager().commitStagedReload())
            AtmosphereService.INSTANCE.onReactionCatalogReload(event.getServer().getAllLevels());
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
        com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.clearAll();
        com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.clearAll();
        com.juicyslew.moonstation14.ms14.devicenet.item.RemoteSignallerServer.serverStopped(event);
        com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireMenuService.onServerStopped();
        com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenuService.onServerStopped();
        com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.closeAll();
        AtmosphereService.INSTANCE.onServerStopped();
        com.juicyslew.moonstation14.ms14.explosion.server.QueuedExplosionSystem.onServerStopped(event);
        PrototypeRuntime.serverManager().clearPublishedCatalogs();
    }

    private void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer actor)
        {
            com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.clear(actor);
            com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.clear(actor);
            com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenuService.playerLoggedOut(actor);
        }
    }

    @SubscribeEvent
    public void onLevelTickPost(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).tick();
            com.juicyslew.moonstation14.ms14.explosion.server.QueuedExplosionSystem.tick(level);
            com.juicyslew.moonstation14.ms14.disposal.transport.DisposalPendingAirSavedData.retryLoaded(level);
            com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tick(level);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.closeLevel(level);
        com.juicyslew.moonstation14.ms14.explosion.server.QueuedExplosionSystem.onLevelUnload(event);
    }
}
