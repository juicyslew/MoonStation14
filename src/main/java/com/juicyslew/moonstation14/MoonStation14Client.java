package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.ms14.loose_objects.client.LooseItemRenderer;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.chat.client.LocalSpeechClient;
import com.juicyslew.moonstation14.ms14.chat.client.DirectionalCueOverlay;
import com.juicyslew.moonstation14.ms14.chat.client.LocalSpeechReviewKey;
import com.juicyslew.moonstation14.ms14.chat.client.LocalSpeechReviewOverlay;
import com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechNetworking;
import com.juicyslew.moonstation14.ms14.explosion.grenade.client.GrenadeSoundClient;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundNetworking;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogNetworking;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogSyncAssembler;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogSyncPayload;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.util.ModSpecialProperties;
import com.juicyslew.moonstation14.ms14.alert.AlertAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualClientCache;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualNetworking;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualPayload;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualPendingSnapshots;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualClientCache;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualRenderer;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualResyncScheduler;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualPendingSnapshots;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualNetworking;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualPayload;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualResyncRequest;
import com.juicyslew.moonstation14.ms14.power.ui.client.ApcScreen;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalBuildScreen;
import com.juicyslew.moonstation14.ms14.disposal.unit.ui.client.DisposalUnitScreen;
import com.juicyslew.moonstation14.ms14.disposal.client.DisposalTubeClientCache;
import com.juicyslew.moonstation14.ms14.disposal.client.DisposalTubeVisualRenderer;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionPreview;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeBlock;
import com.juicyslew.moonstation14.ms14.disposal.network.DisposalTubeVisualNetworking;
import com.juicyslew.moonstation14.ms14.disposal.network.DisposalTubeVisualPayload;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireNetworking;
import com.juicyslew.moonstation14.ms14.ui.client.gallery.MachineUiGalleryCommands;
import com.juicyslew.moonstation14.ms14.power.ui.ApcNetworking;
import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualResyncRequest;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.player_body_control.client.BodyHealthHudPolicy;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.client.BodyBlockOutlineRenderer;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient;
import com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionRenderer;
import com.juicyslew.moonstation14.ms14.atmos.piping.client.VisualPipeBlockEntityRenderer;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmNetworking;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Camera;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MoonStation14.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public class MoonStation14Client {
    private static final BodyHealthHudPolicy BODY_HEALTH_HUD = new BodyHealthHudPolicy();
    private static final AtmosphereVisualClientCache ATMOSPHERE_VISUALS = new AtmosphereVisualClientCache();
    private static final AtmosphereVisualPendingSnapshots ATMOSPHERE_PENDING = new AtmosphereVisualPendingSnapshots();
    private static final CableVisualClientCache CABLE_VISUALS = new CableVisualClientCache();
    private static final CableVisualResyncScheduler CABLE_RESYNC = new CableVisualResyncScheduler();
    private static final CableVisualPendingSnapshots CABLE_PENDING = new CableVisualPendingSnapshots();
    private static final DisposalTubeClientCache DISPOSAL_TUBES = new DisposalTubeClientCache();
    private static net.minecraft.resources.ResourceLocation cableWorldDimension;
    private static net.minecraft.resources.ResourceLocation atmosphereDimension;
    private static transient boolean serverAtmosVisualsActive;
    private static boolean warnedIncompleteAtmosphere;
    private static boolean atmosphereVisualRangeTruncated;
    private static int atmosphereResyncTick;
    private static long cableResyncClock;
    private static final PrototypeCatalogSyncAssembler CATALOG_ASSEMBLER =
            new PrototypeCatalogSyncAssembler(PrototypeRuntime.clientManager()::publishEncodedCatalogs);

    public MoonStation14Client(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(com.juicyslew.moonstation14.entities.client.SingularityAmbientClient::registerReloadListener);
        modEventBus.addListener(com.juicyslew.moonstation14.entities.client.SingularityRenderer::registerShaders);
        var analyzerWorkspace = com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE;
        analyzerWorkspace.closeSender(request -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(request));
        var chemMasterWorkspace = com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace.INSTANCE;
        chemMasterWorkspace.sender(request -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(request));
        com.juicyslew.moonstation14.ms14.botany.ui.GrinderNetworking.setClientConsumer(snapshot -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> com.juicyslew.moonstation14.ms14.botany.client.GrinderWindow.INSTANCE.accept(snapshot));
        });
        com.juicyslew.moonstation14.ms14.botany.GrinderProcessingAudioNetworking.setClientConsumer(com.juicyslew.moonstation14.ms14.botany.client.GrinderProcessingAudio::accept);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.botany.client.GrinderProcessingAudio::tick);
        com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterClientReceiver.install(state -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null || !com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.isCurrentMedicalBody(state.bodyId())) return;
                boolean newWindow=state.open()&&chemMasterWorkspace.get(state.machineId())==null;
                chemMasterWorkspace.accept(state,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                if(newWindow&&chemMasterWorkspace.get(state.machineId())!=null)
                    com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openChemMasterPointerWorkspace();
            });
        });
        com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpClientReceiver.install(result -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> BodyHandClient.dumpResult(result));
        });
        com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerClientReceiver.install(state -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null || !com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient
                        .isCurrentMedicalBody(state.actorId())) return;
                var workspace = com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE;
                boolean newWindow = state.open() && workspace.get(state.scannerId()) == null;
                workspace.accept(state, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                if (newWindow && workspace.get(state.scannerId()) != null && mc.screen == null)
                    com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.medicalPointerMode();
            });
        });
        modEventBus.addListener(MoonStation14Client::onClientSetup);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.equipment.client.AuthoredWearableMeshes::register);
        modEventBus.addListener(MoonStation14Client::registerMoonSky);
        modEventBus.addListener(MoonStation14Client::registerItemColors);
        modEventBus.addListener(MoonStation14Client::registerBlockColors);
        modEventBus.addListener(MoonStation14Client::registerJugModels);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.botany.client.PlantTrayModelRegistration::onModifyBakingResult);
        modEventBus.addListener(MoonStation14Client::registerEmitterImpactParticle);
        modEventBus.addListener(MoonStation14Client::onRegisterRenderers);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.mobs.client.MouseMobRenderer::registerLayer);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters::registerLayers);
        modEventBus.addListener(MoonStation14Client::registerMenuScreens);
        modEventBus.addListener(LocalSpeechReviewKey::register);
        modEventBus.addListener(BodyHandClient::registerKeys);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.actions.client.BodyActionKeys::register);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.standing.client.PostureClient::register);
        modEventBus.addListener(com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient::register);
        modEventBus.addListener(DisposalConstructionClient::registerKeys);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        PrototypeCatalogNetworking.installClientHandler(MoonStation14Client::handleCatalogPayload);
        com.juicyslew.moonstation14.ms14.damage.DamageFeedbackNetworking.installClientHandler(
                com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState::receive);
        AtmosphereVisualNetworking.installClientHandler(MoonStation14Client::handleAtmospherePayload);
        CableVisualNetworking.installClientHandler(MoonStation14Client::handleCableVisualPayload);
        DisposalTubeVisualNetworking.installClientHandler(MoonStation14Client::handleDisposalTubePayload);
        LocalSpeechNetworking.installClientHandler(LocalSpeechClient::receive);
        GrenadeSoundNetworking.installClientHandler(GrenadeSoundClient::receive);
        GrenadeSoundNetworking.installControlledClientHandler(payload -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || !minecraft.level.dimension().location().equals(payload.dimension())) return;
            var camera = minecraft.getCameraEntity();
            if (camera == null || camera.isRemoved() || camera.level() != minecraft.level) return;
            var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(payload.sound()).orElse(null);
            if (sound != null) minecraft.level.playLocalSound(payload.x(), payload.y(), payload.z(), sound,
                    payload.category(), payload.volume(), payload.pitch(), false);
        });
        ApcNetworking.installClientHandler((response, context) -> context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof ApcScreen screen) screen.handleResponse(response);
        }));
        AirAlarmNetworking.installClientHandler((response, context) -> context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof AirAlarmScreen screen) screen.handleResponse(response);
        }));
        AirlockWireNetworking.installClientBridge(
                (open, context) -> context.enqueueWork(() -> com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.open(open.windowId(), open.session())),
                (response, context) -> context.enqueueWork(() -> com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.accept(response)),
                (close, context) -> context.enqueueWork(() -> com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.close(close.windowId(), close.session())));
        com.juicyslew.moonstation14.ms14.atmos.tank.CanisterNetworking.installClientReceiver(
                com.juicyslew.moonstation14.ms14.atmos.tank.client.CanisterClientCache::handle);
        com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlNetworking.installClientReceiver(snapshot ->
                Minecraft.getInstance().execute(() -> com.juicyslew.moonstation14.ms14.singularity.pa.control.client.PaControlWorkspace.INSTANCE.receive(snapshot)));
        com.juicyslew.moonstation14.ms14.radiation.control.RadiationCollectorControlNetworking.installClientReceiver(snapshot ->
                Minecraft.getInstance().execute(() -> com.juicyslew.moonstation14.ms14.radiation.control.client.RadiationCollectorControlClient.receive(snapshot)));
        BodyHandClient.install();
        com.juicyslew.moonstation14.ms14.stripping.client.StripClient.instance().bind();
        TimedInteractionSnapshot.setClientHandler(snapshot -> Minecraft.getInstance().execute(() -> TimedInteractionClient.accept(snapshot)));
        NeoForge.EVENT_BUS.register(MoonStation14ClientNetworkEvents.class);
        NeoForge.EVENT_BUS.addListener(MachineUiGalleryCommands::register);
        NeoForge.EVENT_BUS.addListener(com.juicyslew.moonstation14.ms14.player_body_control.client.MovementTraceClient::register);
        NeoForge.EVENT_BUS.register(com.juicyslew.moonstation14.ms14.player_body_control.client.MovementTraceClient.class);
        com.juicyslew.moonstation14.ms14.ghost_roles.client.GhostRoleNetworking.installDefaultSnapshotRequester();
        com.juicyslew.moonstation14.ms14.ghost_roles.client.GhostRoleNetworking.installClientHandler(
                com.juicyslew.moonstation14.ms14.ghost_roles.client.GhostRoleNetworking::handleClientPayload);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ghostroles")
                        .executes(ctx -> {
                            var credential = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedGhostContext();
                            if (credential == null) {
                                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Only a committed ghost can view available roles."));
                                return 0;
                            }
                            com.juicyslew.moonstation14.ms14.ghost_roles.client.GhostRoleMenuWindow.open(credential);
                            return 1;
                        })));
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        com.juicyslew.moonstation14.ms14.combat.client.ClientFlyByFeedback.install();
        com.juicyslew.moonstation14.ms14.combat.CombatModeNetworking.installMeleeReplyHandler(
                com.juicyslew.moonstation14.ms14.combat.client.MeleeAttackClient::handleReply);
        com.juicyslew.moonstation14.ms14.combat.CombatModeNetworking.installGunShotReplyHandler(
                com.juicyslew.moonstation14.ms14.combat.client.GunShotClient::handleReply);
        com.juicyslew.moonstation14.ms14.combat.CombatModeNetworking.installGunMuzzleFlashHandler(
                com.juicyslew.moonstation14.ms14.combat.client.GunMuzzleFlashClient::receiveObserver);
        com.juicyslew.moonstation14.ms14.combat.CombatModeNetworking.installMeleeArcHandler(
                com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient::receiveObserver);
        event.enqueueWork(com.juicyslew.moonstation14.ms14.medical.ModMedicalItems::bootstrapClientBundled);
        event.enqueueWork(com.juicyslew.moonstation14.ms14.medical.ModInjectorModes::bootstrapClientBundled);
        event.enqueueWork(com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates::bootstrapClientBundled);
        event.enqueueWork(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems::bootstrapClientBundled);
        event.enqueueWork(com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks::bootstrapClientBundled);
        event.enqueueWork(com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals::bootstrapClientBundled);
        ModSpecialProperties.addCustomItemProperties(event);
        DisposalTubeBlock.setRecordProvider((level,pos) -> DISPOSAL_TUBES.get(level.dimension().location(),pos));
        DisposalConstructionClient.register();
        DisposalConstructionClient.installPreviewProviders();
        DisposalConstructionClient.setTubeRecordProvider((pos,dimension) -> DISPOSAL_TUBES.get(dimension,pos));
        DisposalConstructionPreview.register();
        // SET ALL BLOCK RENDERTYPES THAT NEED TO BE TRANSPARENT
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.APC.get(), ApcScreen::new);
        event.register(ModMenus.AIR_ALARM.get(), AirAlarmScreen::new);
        event.register(ModMenus.DISPOSAL_BUILD.get(), DisposalBuildScreen::new);
        event.register(ModMenus.DISPOSAL_UNIT.get(), DisposalUnitScreen::new);
    }

    private static void handleCableVisualPayload(CableVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(payload.dimension())) {
            CABLE_PENDING.stage(payload);
            return;
        }
        CABLE_VISUALS.apply(payload);
    }

    @SubscribeEvent
    public static void renderCableVisuals(RenderLevelStageEvent event) {
        CableVisualRenderer.render(event, CABLE_VISUALS);
    }

    private static void registerEmitterImpactParticle(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.juicyslew.moonstation14.entities.ModEmitterImpactParticles.EMITTER_IMPACT.get(),
                com.juicyslew.moonstation14.entities.client.EmitterImpactParticle.Provider::new);
    }

    @SubscribeEvent
    public static void renderSingularityVisualPreview(RenderLevelStageEvent event) {
        com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.render(event);
    }

    @SubscribeEvent
    public static void registerSingularityPreviewCommand(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ms14_singularity_visual_preview")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, Integer>argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6))
                        .executes(context -> { com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.preview(
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level")); return 1; })));
        event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ms14_singularity_visual_clear")
                .executes(context -> { com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.clear(); return 1; }));
        event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ms14_singularity_visual_reduced_motion")
                .executes(context -> { com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.toggleReducedMotion(); return 1; }));
        event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ms14_singularity_visual_status")
                .executes(context -> {
                    var mc = Minecraft.getInstance();
                    var source = context.getSource();
                    String nativePa = com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.nativePaDiagnostic();
                    source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Singularity visuals: reducedMotion="
                            + com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.reducedMotion()
                            + " " + com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.diagnosticStatus()
                            + " renderDistanceChunks=" + (mc.options == null ? "?" : mc.options.renderDistance().get())
                            + " profileKeys=" + com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager()
                            .snapshot(com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals.TYPE).asMap().keySet()
                            + " " + nativePa), false);
                    if (mc.level == null || mc.player == null) {
                        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("No client level/player"), false);
                        return 1;
                    }
                    var camera = mc.gameRenderer.getMainCamera().getPosition();
                    for (var entity : mc.level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.SingularityEntity.class,
                            mc.player.getBoundingBox().inflate(4096), e -> !e.isRemoved()).stream().limit(5).toList()) {
                        int level = entity.singularityLevel();
                        var profile = com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals.get(level);
                        double cameraDistance = entity.position().distanceTo(camera);
                        double playerDistance = entity.distanceTo(mc.player);
                        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Live " + entity.getUUID()
                                + " level=" + level + " pos=" + entity.position() + " energy=" + entity.energy()
                                + " profile=" + (profile == null ? "MISSING" : "ready/frame=" + profile.frameWidthPixels()
                                + "/sprite=" + com.juicyslew.moonstation14.MoonStation14.MOD_ID + ":textures/singularity/singularity_" + level + ".png")
                                + " cameraDist=" + String.format(java.util.Locale.ROOT, "%.1f", cameraDistance)
                                + " playerDist=" + String.format(java.util.Locale.ROOT, "%.1f", playerDistance)
                                + " " + com.juicyslew.moonstation14.entities.client.SingularityRenderer.diagnosticSnapshot(entity.getUUID())), false);
                        if (profile == null) MoonStation14.LOGGER.warn("No singularity visual profile for live level {} entity {}", level, entity.getUUID());
                    }
                    return 1;
                }));
    }

    @SubscribeEvent public static void renderDisposalTubes(RenderLevelStageEvent event) {
        DisposalTubeVisualRenderer.render(event, DISPOSAL_TUBES);
    }
    private static void handleDisposalTubePayload(DisposalTubeVisualPayload payload) {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null||minecraft.player==null||!minecraft.level.dimension().location().equals(payload.dimension())
                ||!minecraft.level.hasChunk(payload.chunkX(),payload.chunkZ()))return;
        DISPOSAL_TUBES.replace(payload.dimension(),payload.chunkX(),payload.chunkZ(),payload.reset(),payload.records());
    }
    static void clearDisposalTubeVisuals(){DISPOSAL_TUBES.clear();}
    static void unloadDisposalTubeChunk(net.minecraft.resources.ResourceLocation dimension,int x,int z){DISPOSAL_TUBES.remove(dimension,x,z);}

    @SubscribeEvent
    public static void renderBodyBlockOutline(RenderLevelStageEvent event) {
        com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.renderWorld(event);
        BodyBlockOutlineRenderer.render(event);
    }

    @SubscribeEvent
    public static void renderExaminePopup(RenderGuiEvent.Post event) {
        com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.renderGui(event);
    }

    @SubscribeEvent
    public static void tickTimedInteractions(ClientTickEvent.Post event) { TimedInteractionClient.tick(); com.juicyslew.moonstation14.ms14.actions.client.BodyActionClient.tick(event); com.juicyslew.moonstation14.ms14.actions.client.BodyActionKeys.tick(); }

    @SubscribeEvent
    public static void renderTimedInteractions(RenderLevelStageEvent event) { TimedInteractionRenderer.render(event); }

    @SubscribeEvent
    public static void renderLocalSpeech(RenderLevelStageEvent event) {
        DirectionalCueOverlay.capture(event);
        LocalSpeechClient.render(event);
    }

    @SubscribeEvent
    public static void renderSpeechCues(RenderGuiEvent.Post event) {
        com.juicyslew.moonstation14.ms14.actions.client.BodyActionHud.render(event);
        LocalSpeechReviewOverlay.render(event);
        DirectionalCueOverlay.render(event);
        LocalSpeechClient.renderCallouts(event);
    }

    @SubscribeEvent
    public static void renderSpeechCuesOnChat(ScreenEvent.Render.Post event) {
        LocalSpeechReviewOverlay.render(event);
        DirectionalCueOverlay.render(event);
        LocalSpeechClient.renderCallouts(event);
    }

    @SubscribeEvent
    public static void tickLocalSpeech(ClientTickEvent.Post event) {
        LocalSpeechClient.tick();
        LocalSpeechReviewKey.tick();
    }

    private static void handleAtmospherePayload(AtmosphereVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(payload.dimension())
                || !minecraft.level.hasChunk(payload.chunkX(), payload.chunkZ())) {
            ATMOSPHERE_PENDING.stage(payload);
            return;
        }
        ATMOSPHERE_PENDING.retainDimension(payload.dimension());
        ATMOSPHERE_PENDING.flush(payload.dimension(), prior -> prior.chunkX() == payload.chunkX()
                && prior.chunkZ() == payload.chunkZ() && minecraft.level.hasChunk(prior.chunkX(), prior.chunkZ()),
                MoonStation14Client::applyAtmospherePayload);
        applyAtmospherePayload(payload);
    }

    private static void applyAtmospherePayload(AtmosphereVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(payload.dimension())
                || !minecraft.level.hasChunk(payload.chunkX(), payload.chunkZ())) return;
        if (!payload.dimension().equals(atmosphereDimension)) {
            ATMOSPHERE_VISUALS.clear();
            atmosphereDimension = payload.dimension();
            warnedIncompleteAtmosphere = false;
        }
        ATMOSPHERE_PENDING.noteReset(payload);
        if (ATMOSPHERE_VISUALS.apply(payload)) ATMOSPHERE_PENDING.acknowledgeApplied(payload);
        serverAtmosVisualsActive = true;
        if (ATMOSPHERE_VISUALS.isIncomplete() && !warnedIncompleteAtmosphere) {
            warnedIncompleteAtmosphere = true;
            MoonStation14.LOGGER.warn("Atmosphere visual cache reached its limit; some server-authored gas visuals are omitted");
        }
    }

    @SubscribeEvent
    public static void renderAtmosphere(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        var dimension = minecraft.level.dimension().location();
        if (!dimension.equals(atmosphereDimension)) {
            if (atmosphereDimension != null || serverAtmosVisualsActive) clearAtmosphereVisuals();
            return;
        }
        if (!serverAtmosVisualsActive) return;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        var visible = ATMOSPHERE_VISUALS.visibleCells(dimension, cameraPosition.x, cameraPosition.y,
                cameraPosition.z, 48.0, 2001);
        atmosphereVisualRangeTruncated = visible.size() > 2000;
        if (atmosphereVisualRangeTruncated) visible = visible.subList(0, 2000);
        if (visible.isEmpty()) return;

        var poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack != null ? poseStack.last().pose() : event.getModelViewMatrix();
        GasType[] gases = {GasType.PLASMA, GasType.TRITIUM, GasType.WATER_VAPOR, GasType.AMMONIA, GasType.FREZON};
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            for (int gasIndex = 0; gasIndex < gases.length; gasIndex++) {
                boolean gasVisible = false;
                for (var candidate : visible) {
                    if (alphaForGas(candidate.cell(), gasIndex) > 0) {
                        gasVisible = true;
                        break;
                    }
                }
                if (!gasVisible) continue;

                GasVisibility.Rgb tint = GasVisibility.tint(gases[gasIndex]).orElseThrow();
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_COLOR);
                for (var candidate : visible) {
                    AtmosphereVisualPayload.VisualCell cell = candidate.cell();
                    int alpha = alphaForGas(cell, gasIndex);
                    if (alpha == 0) continue;
                    // SS14 uses animated white-alpha gas sprites. This is a texture-free, gently animated
                    // crossed-plane approximation tinted with prototype UI colors, not sprite parity.
                    float pulse = .88F + .12F * (float) Math.sin(minecraft.level.getGameTime() * .08 +
                            candidate.x() * .7 + candidate.z() * .4);
                    int opacity = Math.max(1, Math.min(255,
                            Math.round(GasVisibility.perPlaneAlphaByte(alpha, 3, 4) * pulse)));
                    // Candidate positions are cell centers; convert to camera-relative coordinates once.
                    float cx = (float) (candidate.x() - cameraPosition.x);
                    float cy = (float) (candidate.y() - cameraPosition.y);
                    float cz = (float) (candidate.z() - cameraPosition.z);
                    float radius = GasVisibility.quadRadius(alpha);
                    // Three orthogonal planes remain visible from above and from any side.
                    addGasQuad(builder, matrix, cx - radius, cy - radius, cz, cx + radius, cy - radius,
                            cz, cx + radius, cy + radius, cz, cx - radius, cy + radius, cz, tint, opacity);
                    addGasQuad(builder, matrix, cx - radius, cy, cz - radius, cx + radius, cy,
                            cz - radius, cx + radius, cy, cz + radius, cx - radius, cy, cz + radius, tint, opacity);
                    addGasQuad(builder, matrix, cx, cy - radius, cz - radius, cx, cy - radius,
                            cz + radius, cx, cy + radius, cz + radius, cx, cy + radius, cz - radius, tint, opacity);
                }
                BufferUploader.drawWithShader(builder.buildOrThrow());
            }

            boolean fireVisible = visible.stream().anyMatch(candidate -> candidate.cell().fireIntensity() > 0);
            if (fireVisible) {
                // Resolve after resource reloads; the vanilla atlas sprite keeps its animated UVs. Transparent
                // planes can be clipped/overdrawn by nearby geometry and other translucent effects at this stage.
                TextureAtlasSprite fire = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .apply(ModelBakery.FIRE_0.texture());
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                BufferBuilder fireBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_TEX_COLOR);
                for (var candidate : visible) {
                    int intensity = candidate.cell().fireIntensity();
                    if (intensity <= 0) continue;
                    float cx = (float) (candidate.x() - cameraPosition.x);
                    float cy = (float) (candidate.y() - cameraPosition.y);
                    float cz = (float) (candidate.z() - cameraPosition.z);
                    float strength = intensity / 255.0F;
                    float halfWidth = .22F + .25F * strength;
                    float height = .45F + .5F * strength;
                    int alpha = Math.max(48, Math.min(176, Math.round(48 + 128 * strength)));
                    int color = alpha << 24 | 0x00ffffff;
                    // Two intersecting vertical billboards are legible from the sides without making a solid cube.
                    addFireQuad(fireBuilder, matrix, cx - halfWidth, cy, cz, cx + halfWidth, cy, cz,
                            cx + halfWidth, cy + height, cz, cx - halfWidth, cy + height, cz, fire, color);
                    addFireQuad(fireBuilder, matrix, cx, cy, cz - halfWidth, cx, cy, cz + halfWidth,
                            cx, cy + height, cz + halfWidth, cx, cy + height, cz - halfWidth, fire, color);
                }
                BufferUploader.drawWithShader(fireBuilder.buildOrThrow());
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        }
    }

    private static int alphaForGas(AtmosphereVisualPayload.VisualCell cell, int gasIndex) {
        return switch (gasIndex) {
            case 0 -> cell.plasmaAlpha();
            case 1 -> cell.tritiumAlpha();
            case 2 -> cell.waterVaporAlpha();
            case 3 -> cell.ammoniaAlpha();
            default -> cell.frezonAlpha();
        };
    }

    private static void addGasQuad(BufferBuilder builder, Matrix4f matrix,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float x3, float y3, float z3, float x4, float y4, float z4,
                                   GasVisibility.Rgb color, int alpha) {
        builder.addVertex(matrix, x1, y1, z1).setColor(color.red(), color.green(), color.blue(), alpha);
        builder.addVertex(matrix, x2, y2, z2).setColor(color.red(), color.green(), color.blue(), alpha);
        builder.addVertex(matrix, x3, y3, z3).setColor(color.red(), color.green(), color.blue(), alpha);
        builder.addVertex(matrix, x4, y4, z4).setColor(color.red(), color.green(), color.blue(), alpha);
    }

    private static void addFireQuad(BufferBuilder builder, Matrix4f matrix,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    TextureAtlasSprite sprite, int color) {
        builder.addVertex(matrix, x1, y1, z1).setUv(sprite.getU0(), sprite.getV1()).setColor(color);
        builder.addVertex(matrix, x2, y2, z2).setUv(sprite.getU1(), sprite.getV1()).setColor(color);
        builder.addVertex(matrix, x3, y3, z3).setUv(sprite.getU1(), sprite.getV0()).setColor(color);
        builder.addVertex(matrix, x4, y4, z4).setUv(sprite.getU0(), sprite.getV0()).setColor(color);
    }

    public static void registerMoonSky(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new MoonSkyEffects());
    }

    /** Client-only replacement for the built-in Overworld renderer; all world lighting remains vanilla. */
    private static final class MoonSkyEffects extends DimensionSpecialEffects.OverworldEffects {
        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            if (isMoonSkyActive(Minecraft.getInstance().level)) return Vec3.ZERO;
            return super.getBrightnessDependentFogColor(fogColor, brightness);
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                                 Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            if (!isMoonSkyActive(level)) return false;

            setupFog.run();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Matrix4f matrix = new Matrix4f(modelViewMatrix);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR);
            // Draw a camera-centered cube so the replacement really clears the entire sky framebuffer to black.
            addFace(builder, matrix, -100, -100, -100, 100, -100, -100, 100, 100, -100, -100, 100, -100, 0, 0, 0);
            addFace(builder, matrix, 100, -100, 100, -100, -100, 100, -100, 100, 100, 100, 100, 100, 0, 0, 0);
            addFace(builder, matrix, -100, -100, 100, -100, -100, -100, -100, 100, -100, -100, 100, 100, 0, 0, 0);
            addFace(builder, matrix, 100, -100, -100, 100, -100, 100, 100, 100, 100, 100, 100, -100, 0, 0, 0);
            addFace(builder, matrix, -100, 100, -100, 100, 100, -100, 100, 100, 100, -100, 100, 100, 0, 0, 0);
            addFace(builder, matrix, -100, -100, 100, 100, -100, 100, 100, -100, -100, -100, -100, -100, 0, 0, 0);
            BufferUploader.drawWithShader(builder.buildOrThrow());

            // Critical view suppresses the bespoke ungfogged stars and celestial discs.
            if (isCriticalSkyView()) {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                return true;
            }

            // Deterministic procedural stars, kept deliberately texture-free.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            BufferBuilder stars = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR);
            java.util.Random random = new java.util.Random(0x4d4f4f4eL);
            for (int i = 0; i < 220; i++) {
                float x = random.nextFloat() * 2 - 1;
                float y = random.nextFloat() * 2 - 1;
                float z = random.nextFloat() * 2 - 1;
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                if (length < 0.001F) continue;
                x = x / length * 90;
                y = y / length * 90;
                z = z / length * 90;
                float size = random.nextFloat() * 0.45F + 0.2F;
                int color = random.nextInt(4) == 0 ? 0xffc8ddff : 0xfff1f4ff;
                int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                addStarQuad(stars, matrix, x, y, z, size, 0, red, green, blue);
                addStarQuad(stars, matrix, x, y, z, size, 1, red, green, blue);
                addStarQuad(stars, matrix, x, y, z, size, 2, red, green, blue);
            }
            BufferUploader.drawWithShader(stars.buildOrThrow());

            // Match vanilla's celestial rotation: the sun is at +Y and the opposite moon position is Earth.
            var scenes = com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.catalog(level);
            var sun = scenes.get(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.SUN);
            var earth = scenes.get(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.EARTH);
            boolean criticalView = isCriticalSkyView();
            boolean drawSun = com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.available(level, sun, criticalView);
            boolean drawEarth = com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.available(level, earth, criticalView);
            if (!drawSun && !drawEarth) {
                RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1, 1, 1, 1); return true;
            }
            double timeOfDay = level.getTimeOfDay(partialTick);
            Matrix4f celestialMatrix = new Matrix4f(matrix).mul(
                    com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.transform(timeOfDay));
            BufferBuilder bodies = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_COLOR);
            float sunY = drawSun ? com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.localPlaneY(sun) : 0;
            if (drawSun) addDisc(bodies, celestialMatrix, 0, sunY, 0, (float)sun.radius(), sun.red(), sun.green(), sun.blue());
            float earthY = drawEarth ? com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.localPlaneY(earth) : 0;
            if (drawEarth) addDisc(bodies, celestialMatrix, 0, earthY, 0, (float)earth.radius(), earth.red(), earth.green(), earth.blue());
            // Small low-poly continent and polar ice highlights on the texture-free blue Earth disc.
            float detailY = drawEarth ? com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.cameraFacingArtPlaneY(earth) : 0;
            if (drawEarth) addTriangle(bodies, celestialMatrix, -3.8F, detailY, -1.0F, -1.0F, detailY, -3.6F,
                    2.1F, detailY, -1.7F, 78, 171, 105);
            if (drawEarth) addTriangle(bodies, celestialMatrix, 0.2F, detailY, 1.2F, 3.7F, detailY, 0.8F,
                    2.2F, detailY, 3.5F, 78, 171, 105);
            if (drawEarth) addTriangle(bodies, celestialMatrix, -1.8F, detailY, 4.0F, 0.5F, detailY, 3.4F,
                    1.1F, detailY, 4.8F, 202, 230, 243);
            BufferUploader.drawWithShader(bodies.buildOrThrow());

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            return true;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        private static boolean isMoonSkyActive(ClientLevel level) {
            return level != null && Config.shouldRenderMoonSky(level.dimension());
        }

        private static boolean isCriticalSkyView() {
            var body = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.observedBody();
            return body instanceof net.minecraft.world.entity.LivingEntity living
                    && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(living);
        }

        private static void addFace(BufferBuilder builder, Matrix4f matrix,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    int red, int green, int blue) {
            builder.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, 255);
            builder.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, 255);
            builder.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, 255);
            builder.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, 255);
        }

        private static void addStarQuad(BufferBuilder builder, Matrix4f matrix, float x, float y, float z,
                                        float size, int plane, int red, int green, int blue) {
            if (plane == 0) {
                builder.addVertex(matrix, x - size, y - size, z).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x + size, y - size, z).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x + size, y + size, z).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x - size, y + size, z).setColor(red, green, blue, 255);
            } else if (plane == 1) {
                builder.addVertex(matrix, x - size, y, z - size).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x + size, y, z - size).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x + size, y, z + size).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x - size, y, z + size).setColor(red, green, blue, 255);
            } else {
                builder.addVertex(matrix, x, y - size, z - size).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x, y + size, z - size).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x, y + size, z + size).setColor(red, green, blue, 255);
                builder.addVertex(matrix, x, y - size, z + size).setColor(red, green, blue, 255);
            }
        }

        private static void addDisc(BufferBuilder builder, Matrix4f matrix, float x, float y, float z,
                                    float radius, int red, int green, int blue) {
            int segments = com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.SEGMENTS;
            for (int i = 0; i < segments; i++) {
                double first = Math.PI * 2.0 * i / segments;
                double second = Math.PI * 2.0 * (i + 1) / segments;
                addTriangle(builder, matrix, x, y, z,
                        x + (float) Math.cos(first) * radius, y, z + (float) Math.sin(first) * radius,
                        x + (float) Math.cos(second) * radius, y, z + (float) Math.sin(second) * radius,
                        red, green, blue);
            }
        }

        private static void addTriangle(BufferBuilder builder, Matrix4f matrix,
                                        float x1, float y1, float z1, float x2, float y2, float z2,
                                        float x3, float y3, float z3, int red, int green, int blue) {
            builder.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, 255);
            builder.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, 255);
            builder.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, 255);
        }
    }

    @SubscribeEvent
    public static void renderAlerts(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (GhostControlClient.isLocalGhostControl()) {
            BODY_HEALTH_HUD.reset();
            return;
        }
        var canonicalCharacter = GhostControlClient.ownedCharacterForPresentation();
        var observation = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.current();
        var observedBody = observation == null ? null
                : com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.statusBody();
        var body = GhostControlClient.isLocalMindCarrier()
                ? com.juicyslew.moonstation14.ms14.player_body_control.client.BodyHudSourcePolicy.select(
                        canonicalCharacter, observedBody, GhostControlClient.isLocalCharacterControl(),
                        GhostControlClient.isLocalGhostControl()) : null;
        if (body == null || minecraft.player == null || minecraft.level == null) BODY_HEALTH_HUD.reset();
        if (minecraft.options.hideGui) {
            BODY_HEALTH_HUD.reset();
            return;
        }
        if (minecraft.player == null || minecraft.level == null) return;
        var rows = new java.util.ArrayList<HudStatus>();
        if (serverAtmosVisualsActive && (ATMOSPHERE_VISUALS.isIncomplete() || atmosphereVisualRangeTruncated)) {
            rows.add(new HudStatus("Gas visuals incomplete", 0xffa83232));
        }

        boolean mindCarrier = GhostControlClient.isLocalMindCarrier();
        // During any owned Mind session the carrier is only a camera/transport shell. Never
        // project its physiology when the committed body is a ghost or is not yet available.
        if (mindCarrier && body == null) return;
        net.minecraft.world.entity.LivingEntity source = mindCarrier ? body : minecraft.player;
        if (source == null) return;

        AlertAttachment alerts = source.getExistingDataOrNull(ModDataAttachments.ALERT.get());
        var catalog = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientAlerts();
        var ordered = alerts == null ? java.util.List.<java.util.Map.Entry<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.AlertData>, com.juicyslew.moonstation14.ms14.alert.AlertInstance>>of()
                : alerts.snapshot().entrySet().stream().filter(entry -> catalog.get(entry.getKey().location()) != null)
                .sorted(java.util.Comparator.comparingInt(entry -> catalog.get(entry.getKey().location()).order()))
                .toList();
        for (var entry : ordered) {
            var prototype = catalog.get(entry.getKey().location());
            String label = net.minecraft.network.chat.Component.translatable(prototype.nameTranslationKey()).getString();
            var instance = entry.getValue();
            if (instance.showCooldown() && instance.deadline().isPresent()) {
                long remaining = Math.max(0, instance.deadline().getAsLong() - minecraft.level.getGameTime());
                label += " " + ((remaining + 19) / 20) + "s";
            }
            rows.add(new HudStatus(label, prototype.color()));
        }

        if (CharacterControlSystem.isClientActionBlocked(source)) {
            rows.add(new HudStatus(net.minecraft.network.chat.Component.translatable("moonstation14.hud.movement.stunned").getString(), 0xffa83232));
        }
        if (SlipSystem.isSliding(source)) {
            rows.add(new HudStatus(net.minecraft.network.chat.Component.translatable("moonstation14.hud.movement.slipped").getString(), 0xffa86b16));
        }
        if (CharacterControlSystem.isKnockedDown(source)) {
            rows.add(new HudStatus(net.minecraft.network.chat.Component.translatable("moonstation14.hud.movement.knocked_down").getString(), 0xff67469a));
        }
        if (body != null) {
            var character = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(body);
            var bodyObservation = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.current();
            long epoch = bodyObservation != null && bodyObservation.body().equals(body.getUUID())
                    ? bodyObservation.epoch() : GhostControlClient.ownedCharacterForPresentation() == body
                    ? GhostControlClient.presentationCharacterEpoch() : 0;
            // MobStateSystem.policy is server-authority-only; HUD thresholds use the synced read-only client projection.
            var medicalPolicy = character.flatMap(com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy::from).orElse(null);
            var damageLedger = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            float typedDamage = damageLedger == null ? 0f
                    : (float) damageLedger.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
            float critical = medicalPolicy == null ? Float.NaN : (float) medicalPolicy.thresholds().thresholds().entrySet().stream()
                    .filter(entry -> entry.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL)
                    .mapToDouble(java.util.Map.Entry::getKey).min().orElse(Double.NaN);
            float dead = medicalPolicy == null ? Float.NaN : (float) medicalPolicy.thresholds().thresholds().entrySet().stream()
                    .filter(entry -> entry.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD)
                    .mapToDouble(java.util.Map.Entry::getKey).min().orElse(Double.NaN);
            var observedState = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
            BodyHealthHudPolicy.View healthView = epoch > 0
                    ? BodyHealthHudPolicy.medicalIfAvailable(damageLedger != null, typedDamage, critical, dead,
                    observedState == null ? com.juicyslew.moonstation14.ms14.mobs.MobStateData.DEFAULT.state()
                            : observedState.data().state()) : null;
            boolean incorporeal = character.flatMap(data -> data.component(
                    com.juicyslew.moonstation14.ms14.character.components.IncorporealComponent.class)).isPresent();
            if (healthView == null && epoch > 0 && !incorporeal && Float.isFinite(body.getHealth())
                    && Float.isFinite(body.getMaxHealth()) && body.getMaxHealth() > 0) {
                var carrier = minecraft.player.getUUID();
                var key = new BodyHealthHudPolicy.Key(carrier, epoch, body.level(), body.getUUID(), body.getId());
                healthView = BODY_HEALTH_HUD.observe(key, body.getHealth(), body.getMaxHealth(),
                        body.level().getGameTime());
            }
            if (healthView != null) {
                rows.add(0, new HudStatus(healthView.label(),
                        healthView.damageFlash() ? 0xffef2727 : 0xffa83232));
                if (healthView.debugLabel() != null)
                    rows.add(1, new HudStatus(healthView.debugLabel(), 0xffa83232));
            }

            var hungerPolicy = character.flatMap(data -> data.component(com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent.class)).orElse(null);
            var hunger = hungerPolicy != null ? body.getExistingDataOrNull(ModDataAttachments.HUNGER.get()) : null;
            if (hunger != null && Float.isFinite(hunger.hunger())) {
                var level = com.juicyslew.moonstation14.ms14.hunger.HungerReducer.classify(hunger.hunger(), hungerPolicy);
                rows.add(new HudStatus("BODY HUNGER " + level.displayLabel() + " " + Math.round(hunger.hunger()), 0xffa86b16));
            }

            var thirstPolicy = character.flatMap(data -> data.component(com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent.class)).orElse(null);
            var thirst = thirstPolicy != null ? body.getExistingDataOrNull(ModDataAttachments.THIRST.get()) : null;
            if (thirst != null && Float.isFinite(thirst.thirst())) {
                var level = com.juicyslew.moonstation14.ms14.thirst.ThirstReducer.classify(thirst.thirst(), thirstPolicy);
                rows.add(new HudStatus("BODY THIRST " + level.displayLabel() + " " + Math.round(thirst.thirst()), 0xff3186a8));
            }
        }
        int guiWidth = event.getGuiGraphics().guiWidth();
        int guiHeight = event.getGuiGraphics().guiHeight();
        var panel = TopRightHudLayout.chat(guiWidth, guiHeight);
        int count = Math.min(rows.size(), TopRightHudLayout.statusRows(guiWidth, guiHeight));
        int maxTextWidth = Math.max(0, (int) Math.floor((panel.width() - 8) / TopRightHudLayout.STATUS_FONT_SCALE));
        for (int i = 0; i < count && maxTextWidth > 0; i++) {
            HudStatus row = rows.get(i);
            String label = minecraft.font.plainSubstrByWidth(row.label(), maxTextWidth);
            var bounds = TopRightHudLayout.statusRow(guiWidth, guiHeight, i);
            int textWidth = (int) Math.ceil(minecraft.font.width(label) * TopRightHudLayout.STATUS_FONT_SCALE);
            int x = bounds.right() - textWidth - 4;
            int y = bounds.top() + 1;
            event.getGuiGraphics().fill(x - 3, bounds.top(), bounds.right(), bounds.bottom(),
                    0x99000000 | (row.color() & 0x00ffffff));
            event.getGuiGraphics().pose().pushPose();
            try {
                event.getGuiGraphics().pose().translate(x, y, 0);
                event.getGuiGraphics().pose().scale(TopRightHudLayout.STATUS_FONT_SCALE,
                        TopRightHudLayout.STATUS_FONT_SCALE, 1);
                event.getGuiGraphics().drawString(minecraft.font, label, 0, 0, 0xffffffff, false);
            } finally {
                event.getGuiGraphics().pose().popPose();
            }
        }
    }

    @SubscribeEvent
    public static void renderBodyHands(RenderGuiEvent.Post event) {
        BodyHandClient.render(event);
    }

    @SubscribeEvent
    public static void renderPointerWorkspaceAfterScreen(ScreenEvent.Render.Post event) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        boolean current=mc.screen==event.getScreen();
        boolean allowed=com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(event.getScreen());
        if (!com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.shouldPaintWorkspace(
                mc.screen==null,true,current,allowed)) return;
        BodyHandClient.renderPointerWorkspace(event.getGuiGraphics(),
                event.getPartialTick());
    }

    @SubscribeEvent
    public static void constructionBrowserScreenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.inputBelongsToScreen(event.getScreen())) return;
        var browser = com.juicyslew.moonstation14.ms14.construction.client.ConstructionBrowserWindow.instance();
        if (browser.mouseClicked(event.getMouseX(),event.getMouseY(),event.getButton())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void constructionBrowserScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.inputBelongsToScreen(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.construction.client.ConstructionBrowserWindow.instance()
                .keyPressed(event.getKeyCode(),event.getScanCode(),event.getModifiers())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void constructionBrowserScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient.releaseBrowserClosePress(
                event.getKeyCode(),event.getScanCode());
    }

    @SubscribeEvent
    public static void constructionBrowserScreenCharacter(ScreenEvent.CharacterTyped.Pre event) {
        if (!com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.inputBelongsToScreen(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.construction.client.ConstructionBrowserWindow.instance()
                .charTyped(event.getCodePoint(),event.getModifiers())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void constructionBrowserScreenDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.inputBelongsToScreen(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.drag(event.getMouseX(),event.getMouseY()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void constructionBrowserScreenRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.inputBelongsToScreen(event.getScreen())) return;
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.release(event.getButton()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderSelectedLooseOutline(RenderLevelStageEvent event) {
        com.juicyslew.moonstation14.ms14.hands.client.SelectedLooseOutlineRenderer.render(event);
    }

    @SubscribeEvent
    public static void tickBodyHands(ClientTickEvent.Post event) {
        BodyHandClient.tick(event);
    }

    @SubscribeEvent
    public static void bodyHandMouseInput(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        BodyHandClient.mouseButton(event);
    }

    private record HudStatus(String label, int color) { }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                ReagentComponent data = stack.get(ModDataComponents.REAGENT.get());
                if (data != null){
                    // This is overkill. Technically BlendedData could be retrieved JUST from map data (i.e. read-only)
                    return ReagentComponent.getBlendedColor(data.contents(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default (no tint)
        }, ModItems.BOTTLE.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                ReagentComponent data = stack.get(ModDataComponents.REAGENT.get());
                if (data != null) return ReagentComponent.getBlendedColor(data.contents(), Minecraft.getInstance().level);
            }
            return -1;
        }, ModItems.SYRINGE.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                ReagentComponent data = stack.get(ModDataComponents.REAGENT.get());
                if (data != null){
                    // This is overkill. Technically BlendedData could be retrieved JUST from map data (i.e. read-only)
                    return ReagentComponent.getBlendedColor(data.contents(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default (no tint)
        }, ModItems.JUG.get());
        net.minecraft.client.color.item.ItemColor beakerColor = (stack, tintIndex) -> {
            if (tintIndex != 1 || !(stack.getItem() instanceof com.juicyslew.moonstation14.item.custom.BeakerItem item)) return -1;
            var data = com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers.resolved(Minecraft.getInstance().level, item.prototypeId());
            if (!com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.supported(data)) return -1;
            ReagentComponent reagent = stack.get(ModDataComponents.REAGENT.get());
            return com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.color(reagent, data, Minecraft.getInstance().level);
        };
        event.register(beakerColor, ModItems.BEAKER.get(), ModItems.BEAKER_LARGE.get());
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0){
                if (level != null && pos != null && level.getBlockEntity(pos) instanceof PuddleBlockEntity puddle) {
                    var container = puddle.getData(ModDataAttachments.REAGENT.get());
                    return ReagentComponent.getBlendedColor(container.getMap(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default
        }, ModBlocks.PUDDLE.get());
    }

    public static void registerJugModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (int fill = 0; fill <= 6; fill++) {
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            MoonStation14.MOD_ID, "block/jug_" + fill)));
        }
        com.juicyslew.moonstation14.ms14.doors.client.AirlockBlockEntityRenderer.additionalModels()
                .forEach(event::register);
        com.juicyslew.moonstation14.ms14.atmos.tank.client.GasCanisterRenderer.additionalModels()
                .forEach(event::register);
        com.juicyslew.moonstation14.ms14.botany.client.HydroponicsTrayBlockEntityRenderer.additionalModels()
                .forEach(event::register);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.AIRLOCK.get(), com.juicyslew.moonstation14.ms14.doors.client.AirlockBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HYDROPONICS_TRAY.get(), com.juicyslew.moonstation14.ms14.botany.client.HydroponicsTrayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.REAGENT_GRINDER.get(), com.juicyslew.moonstation14.ms14.botany.client.ReagentGrinderBlockEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.LOOSE_ITEM.get(), LooseItemRenderer::new);
        event.registerEntityRenderer(ModEntities.TYPED_PROJECTILE.get(), com.juicyslew.moonstation14.ms14.combat.client.TypedProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.GAS_CANISTER.get(), com.juicyslew.moonstation14.ms14.atmos.tank.client.GasCanisterRenderer::new);
        event.registerEntityRenderer(ModEntities.MOUSE.get(), com.juicyslew.moonstation14.ms14.mobs.client.MouseMobRenderer::new);
        com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().forEach((modelId, type) ->
                event.registerEntityRenderer(type.get(), context ->
                        new com.juicyslew.moonstation14.ms14.mobs.client.AnimalMobRenderer(context, modelId)));
        // This is the simplest 2D renderer. It just draws the Item's icon.
        event.registerEntityRenderer(ModEntities.THROWN_JUG.get(), ThrownItemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VISUAL_PIPE.get(), VisualPipeBlockEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_GRENADE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.EMITTER_BOLT.get(), com.juicyslew.moonstation14.entities.client.EmitterBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.PA_PARTICLE.get(), com.juicyslew.moonstation14.entities.client.ParticleAcceleratorParticleRenderer::new);
        event.registerEntityRenderer(ModEntities.SINGULARITY.get(), com.juicyslew.moonstation14.entities.client.SingularityRenderer::new);
        event.registerEntityRenderer(ModEntities.TESLA_ENERGY_BALL.get(), com.juicyslew.moonstation14.entities.client.TeslaEnergyBallRenderer::new);
        event.registerEntityRenderer(ModEntities.DISPOSAL_TRANSIT_HOLDER.get(), context -> new net.minecraft.client.renderer.entity.EntityRenderer<>(context) {
            @Override public net.minecraft.resources.ResourceLocation getTextureLocation(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder entity) {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png");
            }
        });
    }

    private static void handleCatalogPayload(PrototypeCatalogSyncPayload payload,
                                             net.neoforged.neoforge.network.handling.IPayloadContext context) {
        try {
            CATALOG_ASSEMBLER.accept(payload);
        } catch (RuntimeException exception) {
            CATALOG_ASSEMBLER.clear();
            MoonStation14.LOGGER.error("Failed to assemble prototype catalog sync", exception);
            throw exception;
        }
    }

    static void clearCatalogSync() {
        CATALOG_ASSEMBLER.clear();
        PrototypeRuntime.clientManager().clearPublishedCatalogs();
    }

    static void clearAtmosphereVisuals() {
        ATMOSPHERE_VISUALS.clear();
        ATMOSPHERE_PENDING.clear();
        atmosphereDimension = null;
        serverAtmosVisualsActive = false;
        warnedIncompleteAtmosphere = false;
        atmosphereVisualRangeTruncated = false;
        atmosphereResyncTick = 0;
    }

    static void resetAtmosphereVisualsForLogin() {
        ATMOSPHERE_VISUALS.clear();
        atmosphereDimension = null;
        serverAtmosVisualsActive = false;
        warnedIncompleteAtmosphere = false;
        atmosphereVisualRangeTruncated = false;
        atmosphereResyncTick = 0;
    }

    static void clearCableVisuals() {
        resetCableVisuals();
        CABLE_PENDING.clear();
    }

    /** Starts a new login's visual stream without losing snapshots already received before world readiness. */
    static void resetCableVisualsForLogin() {
        resetCableVisuals();
    }

    private static void resetCableVisuals() {
        CABLE_VISUALS.clearDimension();
        CABLE_RESYNC.reset();
        cableWorldDimension = null;
        cableResyncClock = 0;
    }

    static void unloadCableChunk(net.minecraft.resources.ResourceLocation dimension, int chunkX, int chunkZ) {
        CABLE_VISUALS.removeChunk(dimension, chunkX, chunkZ);
        CABLE_RESYNC.forgetChunk(dimension, chunkX, chunkZ);
    }

    static void prioritizeCableChunk(net.minecraft.resources.ResourceLocation dimension, int chunkX, int chunkZ) {
        CABLE_RESYNC.prioritize(dimension, new CableVisualResyncScheduler.Chunk(chunkX, chunkZ));
    }

    static void flushPendingCableChunk(ClientLevel level, net.minecraft.world.level.ChunkPos position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null) return;
        CABLE_PENDING.flush(level.dimension().location(), payload -> payload.chunkX() == position.x
                && payload.chunkZ() == position.z && level.hasChunk(payload.chunkX(), payload.chunkZ()), CABLE_VISUALS::apply);
    }

    static void unloadAtmosphereChunk(net.minecraft.resources.ResourceLocation dimension, int chunkX, int chunkZ) {
        ATMOSPHERE_VISUALS.unload(dimension, chunkX, chunkZ);
        ATMOSPHERE_PENDING.unload(dimension, chunkX, chunkZ);
    }

    static void retainAtmosphereDimension(net.minecraft.resources.ResourceLocation dimension) {
        ATMOSPHERE_PENDING.retainDimension(dimension);
    }

    static void flushPendingAtmosphere(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null) return;
        ATMOSPHERE_PENDING.flush(level.dimension().location(), payload ->
                level.hasChunk(payload.chunkX(), payload.chunkZ()), MoonStation14Client::applyAtmospherePayload);
    }

    @SubscribeEvent
    public static void flushPendingAtmosphereOnTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) flushPendingAtmosphere(minecraft.level);
    }

    @SubscribeEvent
    public static void requestAtmosphereResync(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!serverAtmosVisualsActive || minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(atmosphereDimension)) {
            atmosphereResyncTick = 0;
            return;
        }
        if (++atmosphereResyncTick < 100) return;
        atmosphereResyncTick = 0;
        if (ATMOSPHERE_VISUALS.isIncomplete()) for (var key : ATMOSPHERE_VISUALS.resyncCandidates(2)) {
            if (!key.dimension().equals(minecraft.level.dimension().location())) continue;
            if (!minecraft.level.hasChunk(key.x(), key.z())) {
                ATMOSPHERE_VISUALS.unload(key.dimension(), key.x(), key.z());
                continue;
            }
            PacketDistributor.sendToServer(new AtmosphereVisualResyncRequest(key.x(), key.z()));
        }
        ATMOSPHERE_PENDING.resyncCandidates(minecraft.level.dimension().location(), packed -> {
            var pos = new net.minecraft.world.level.ChunkPos(packed);
            return minecraft.level.hasChunk(pos.x, pos.z);
        }, packed -> {
            var pos = new net.minecraft.world.level.ChunkPos(packed);
            PacketDistributor.sendToServer(new AtmosphereVisualResyncRequest(pos.x, pos.z));
        }, 2);
    }

    @SubscribeEvent
    public static void requestCableVisualResync(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            CABLE_RESYNC.reset();
            cableResyncClock = 0;
            return;
        }
        var dimension = minecraft.level.dimension().location();
        if (cableWorldDimension != null && !cableWorldDimension.equals(dimension)) {
            CABLE_PENDING.retainDimension(dimension);
            CABLE_VISUALS.clearDimension();
            CABLE_RESYNC.reset();
            cableResyncClock = 0;
        }
        cableWorldDimension = dimension;
        CABLE_PENDING.flush(dimension, payload -> minecraft.level.hasChunk(payload.chunkX(), payload.chunkZ()),
                CABLE_VISUALS::apply);
        cableResyncClock++;
        int requestsSent = 0;
        var hasSnapshot = (java.util.function.Predicate<CableVisualResyncScheduler.Chunk>) chunk ->
                CABLE_VISUALS.hasUsableSnapshot(dimension, chunk.x(), chunk.z());
        var center = minecraft.player.chunkPosition();
        java.util.function.Predicate<CableVisualResyncScheduler.Chunk> isNearbyLoaded = chunk ->
                Math.abs(chunk.x() - center.x) <= 1 && Math.abs(chunk.z() - center.z) <= 1
                        && minecraft.level.hasChunk(chunk.x(), chunk.z());
        for (var chunk : CABLE_RESYNC.selectPriority(dimension, isNearbyLoaded, hasSnapshot, cableResyncClock,
                CableVisualResyncScheduler.MAX_REQUESTS_PER_SCAN)) {
            PacketDistributor.sendToServer(new CableVisualResyncRequest(chunk.x(), chunk.z()));
            requestsSent++;
        }
        if (requestsSent >= CableVisualResyncScheduler.MAX_REQUESTS_PER_SCAN) return;

        var window = new java.util.ArrayList<CableVisualResyncScheduler.Chunk>(9);
        for (int distance = 0; distance <= 2; distance++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    if (minecraft.level.hasChunk(chunkX, chunkZ))
                        window.add(new CableVisualResyncScheduler.Chunk(chunkX, chunkZ));
                }
            }
        }
        for (var chunk : CABLE_RESYNC.select(dimension, window, hasSnapshot,
                cableResyncClock, CableVisualResyncScheduler.MAX_REQUESTS_PER_SCAN - requestsSent)) {
            PacketDistributor.sendToServer(new CableVisualResyncRequest(chunk.x(), chunk.z()));
        }
    }
}


final class MoonStation14ClientNetworkEvents {
    private MoonStation14ClientNetworkEvents() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        com.juicyslew.moonstation14.entities.client.SingularityRenderer.clearDiagnostics();
        LocalSpeechClient.clear();
        BodyHandClient.reset();
        MoonStation14Client.clearCatalogSync();
        MoonStation14Client.resetAtmosphereVisualsForLogin();
        MoonStation14Client.resetCableVisualsForLogin();
        MoonStation14Client.clearDisposalTubeVisuals();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.juicyslew.moonstation14.ms14.combat.client.GunMuzzleFlashClient.clear();
        com.juicyslew.moonstation14.entities.client.SingularityRenderer.clearDiagnostics();
        com.juicyslew.moonstation14.ms14.singularity.client.SingularityVisualPreview.clear();
        LocalSpeechClient.clear();
        BodyHandClient.reset();
        MoonStation14Client.clearCatalogSync();
        MoonStation14Client.clearAtmosphereVisuals();
        MoonStation14Client.clearCableVisuals();
        MoonStation14Client.clearDisposalTubeVisuals();
    }

    @SubscribeEvent
    public static void onClientChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        var position = event.getChunk().getPos();
        MoonStation14Client.unloadAtmosphereChunk(level.dimension().location(), position.x, position.z);
        MoonStation14Client.unloadCableChunk(level.dimension().location(), position.x, position.z);
        MoonStation14Client.unloadDisposalTubeChunk(level.dimension().location(),position.x,position.z);
    }

    @SubscribeEvent
    public static void onClientChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        var position = event.getChunk().getPos();
        MoonStation14Client.prioritizeCableChunk(level.dimension().location(), position.x, position.z);
        MoonStation14Client.flushPendingCableChunk(level, position);
        MoonStation14Client.flushPendingAtmosphere(level);
    }
}
