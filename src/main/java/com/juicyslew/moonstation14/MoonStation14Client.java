package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.JugBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogNetworking;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogSyncAssembler;
import com.juicyslew.moonstation14.ms14.prototype.network.PrototypeCatalogSyncPayload;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.util.ModSpecialProperties;
import com.juicyslew.moonstation14.ms14.alert.AlertAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.movement.client.MovementClientController;
import com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualClientCache;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualNetworking;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualPayload;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualClientCache;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualRenderer;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualResyncScheduler;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualPendingSnapshots;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualNetworking;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualPayload;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualResyncRequest;
import com.juicyslew.moonstation14.ms14.power.ui.client.ApcScreen;
import com.juicyslew.moonstation14.ms14.ui.client.gallery.MachineUiGalleryCommands;
import com.juicyslew.moonstation14.ms14.power.ui.ApcNetworking;
import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualResyncRequest;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
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
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
    private static final AtmosphereVisualClientCache ATMOSPHERE_VISUALS = new AtmosphereVisualClientCache();
    private static final CableVisualClientCache CABLE_VISUALS = new CableVisualClientCache();
    private static final CableVisualResyncScheduler CABLE_RESYNC = new CableVisualResyncScheduler();
    private static final CableVisualPendingSnapshots CABLE_PENDING = new CableVisualPendingSnapshots();
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
        modEventBus.addListener(MoonStation14Client::onClientSetup);
        modEventBus.addListener(MoonStation14Client::registerMoonSky);
        modEventBus.addListener(MoonStation14Client::registerItemColors);
        modEventBus.addListener(MoonStation14Client::registerBlockColors);
        modEventBus.addListener(MoonStation14Client::onRegisterRenderers);
        modEventBus.addListener(MoonStation14Client::registerMenuScreens);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        PrototypeCatalogNetworking.installClientHandler(MoonStation14Client::handleCatalogPayload);
        AtmosphereVisualNetworking.installClientHandler(MoonStation14Client::handleAtmospherePayload);
        CableVisualNetworking.installClientHandler(MoonStation14Client::handleCableVisualPayload);
        ApcNetworking.installClientHandler((response, context) -> context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof ApcScreen screen) screen.handleResponse(response);
        }));
        MovementClientController.install();
        NeoForge.EVENT_BUS.register(MoonStation14ClientNetworkEvents.class);
        NeoForge.EVENT_BUS.addListener(MachineUiGalleryCommands::register);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        ModSpecialProperties.addCustomItemProperties(event);
        // SET ALL BLOCK RENDERTYPES THAT NEED TO BE TRANSPARENT
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.APC.get(), ApcScreen::new);
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

    private static void handleAtmospherePayload(AtmosphereVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        if (!payload.dimension().equals(atmosphereDimension)) {
            ATMOSPHERE_VISUALS.clear();
            atmosphereDimension = payload.dimension();
            warnedIncompleteAtmosphere = false;
        }
        ATMOSPHERE_VISUALS.apply(payload);
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
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
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
            Matrix4f celestialMatrix = new Matrix4f(matrix)
                    .rotateY((float) Math.toRadians(-90.0))
                    .rotateX((float) (level.getTimeOfDay(partialTick) * Math.PI * 2.0));
            BufferBuilder bodies = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_COLOR);
            addDisc(bodies, celestialMatrix, 0, 80, 0, 9, 255, 255, 245);
            addDisc(bodies, celestialMatrix, 0, -80, 0, 7, 63, 139, 226);
            // Small low-poly continent and polar ice highlights on the texture-free blue Earth disc.
            addTriangle(bodies, celestialMatrix, -3.8F, -80.1F, -1.0F, -1.0F, -80.1F, -3.6F,
                    2.1F, -80.1F, -1.7F, 78, 171, 105);
            addTriangle(bodies, celestialMatrix, 0.2F, -80.1F, 1.2F, 3.7F, -80.1F, 0.8F,
                    2.2F, -80.1F, 3.5F, 78, 171, 105);
            addTriangle(bodies, celestialMatrix, -1.8F, -80.1F, 4.0F, 0.5F, -80.1F, 3.4F,
                    1.1F, -80.1F, 4.8F, 202, 230, 243);
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
            return level != null && Config.shouldRenderMoonSky(Config.MOON_SKY.get(), level.dimension());
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
            int segments = 32;
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
        if (minecraft.options.hideGui) return;
        if (minecraft.player == null || minecraft.level == null) return;
        int y = 12;
        if (serverAtmosVisualsActive && (ATMOSPHERE_VISUALS.isIncomplete() || atmosphereVisualRangeTruncated)) {
            y = drawMovementHudLabel(event, minecraft, "Gas visuals incomplete", 0xffa83232, y);
        }

        boolean mindCarrier = GhostControlClient.isLocalMindCarrier();
        var body = mindCarrier ? GhostControlClient.ownedCharacterForHud() : null;
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
            int width = minecraft.font.width(label);
            int x = minecraft.getWindow().getGuiScaledWidth() - width - 20;
            event.getGuiGraphics().fill(x - 3, y - 2, x + width + 3, y + 11, 0x99000000 | (prototype.color() & 0x00ffffff));
            event.getGuiGraphics().drawString(minecraft.font, label, x, y, 0xffffffff, false);
            y += 13;
        }

        if (CharacterControlSystem.isClientActionBlocked(source)) {
            y = drawMovementHudLabel(event, minecraft, "moonstation14.hud.movement.stunned", 0xffa83232, y);
        }
        if (SlipSystem.isSliding(source)) {
            y = drawMovementHudLabel(event, minecraft, "moonstation14.hud.movement.slipped", 0xffa86b16, y);
        }
        if (CharacterControlSystem.isKnockedDown(source)) {
            y = drawMovementHudLabel(event, minecraft, "moonstation14.hud.movement.knocked_down", 0xff67469a, y);
        }
        if (body != null) {
            int guiWidth = minecraft.getWindow().getGuiScaledWidth();
            int guiHeight = minecraft.getWindow().getGuiScaledHeight();
            int bodyY = 12;
            float maximum = body.getMaxHealth();
            float health = body.getHealth();
            if (Float.isFinite(maximum) && maximum > 0f && Float.isFinite(health)) {
                maximum = Math.min(maximum, 1_000_000f);
                health = Math.max(0f, Math.min(maximum, health));
                String label = "BODY HEALTH " + Math.round(health) + "/" + Math.round(maximum);
                bodyY = drawBodyHudLabel(event, minecraft, label, 0xffa83232, bodyY, guiWidth, guiHeight);
            }

            var hunger = com.juicyslew.moonstation14.ms14.hunger.HungerSystem.isEligible(body)
                    ? body.getExistingDataOrNull(ModDataAttachments.HUNGER.get()) : null;
            if (hunger != null && Float.isFinite(hunger.hunger())) {
                bodyY = drawBodyHudLabel(event, minecraft, "BODY HUNGER " + Math.round(hunger.hunger()),
                        0xffa86b16, bodyY, guiWidth, guiHeight);
            }

            var thirst = com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isEligible(body)
                    ? body.getExistingDataOrNull(ModDataAttachments.THIRST.get()) : null;
            if (thirst != null && Float.isFinite(thirst.thirst())) {
                drawBodyHudLabel(event, minecraft, "BODY THIRST " + Math.round(thirst.thirst()),
                        0xff3186a8, bodyY, guiWidth, guiHeight);
            }
        }
    }

    private static int drawBodyHudLabel(RenderGuiEvent.Post event, Minecraft minecraft, String label, int color,
                                        int y, int guiWidth, int guiHeight) {
        int width = minecraft.font.width(label);
        int x = Math.max(3, Math.min(6, guiWidth - width - 3));
        int lineHeight = minecraft.font.lineHeight;
        int clampedY = Math.max(2, Math.min(y, Math.max(2, guiHeight - lineHeight - 2)));
        event.getGuiGraphics().fill(x - 3, clampedY - 2, x + width + 3, clampedY + lineHeight + 2,
                0x99000000 | (color & 0x00ffffff));
        event.getGuiGraphics().drawString(minecraft.font, label, x, clampedY, 0xffffffff, false);
        return clampedY + lineHeight + 3;
    }

    private static int drawMovementHudLabel(RenderGuiEvent.Post event, Minecraft minecraft,
                                            String translationKey, int color, int y) {
        String label = net.minecraft.network.chat.Component.translatable(translationKey).getString();
        int width = minecraft.font.width(label);
        int x = minecraft.getWindow().getGuiScaledWidth() - width - 20;
        event.getGuiGraphics().fill(x - 3, y - 2, x + width + 3, y + 11, 0x99000000 | (color & 0x00ffffff));
        event.getGuiGraphics().drawString(minecraft.font, label, x, y, 0xffffffff, false);
        return y + 13;
    }

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
                if (data != null){
                    // This is overkill. Technically BlendedData could be retrieved JUST from map data (i.e. read-only)
                    return ReagentComponent.getBlendedColor(data.contents(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default (no tint)
        }, ModItems.JUG.get());
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0){
                if (level != null && pos != null && level.getBlockEntity(pos) instanceof JugBlockEntity jug) {
                    var container = jug.getData(ModDataAttachments.REAGENT.get());
                    return ReagentComponent.getBlendedColor(container.getMap(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default
        }, ModBlocks.JUG.get());
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

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // This is the simplest 2D renderer. It just draws the Item's icon.
        event.registerEntityRenderer(ModEntities.THROWN_JUG.get(), ThrownItemRenderer::new);
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
        if (!ATMOSPHERE_VISUALS.isIncomplete()) return;
        for (var key : ATMOSPHERE_VISUALS.resyncCandidates(2)) {
            if (!key.dimension().equals(minecraft.level.dimension().location())) continue;
            if (!minecraft.level.hasChunk(key.x(), key.z())) {
                ATMOSPHERE_VISUALS.unload(key.dimension(), key.x(), key.z());
                continue;
            }
            PacketDistributor.sendToServer(new AtmosphereVisualResyncRequest(key.x(), key.z()));
        }
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
        MoonStation14Client.clearCatalogSync();
        MoonStation14Client.clearAtmosphereVisuals();
        MoonStation14Client.resetCableVisualsForLogin();
        MovementClientController.reset();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MoonStation14Client.clearCatalogSync();
        MoonStation14Client.clearAtmosphereVisuals();
        MoonStation14Client.clearCableVisuals();
        MovementClientController.reset();
    }

    @SubscribeEvent
    public static void onClientChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        var position = event.getChunk().getPos();
        MoonStation14Client.unloadAtmosphereChunk(level.dimension().location(), position.x, position.z);
        MoonStation14Client.unloadCableChunk(level.dimension().location(), position.x, position.z);
    }

    @SubscribeEvent
    public static void onClientChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        var position = event.getChunk().getPos();
        MoonStation14Client.prioritizeCableChunk(level.dimension().location(), position.x, position.z);
        MoonStation14Client.flushPendingCableChunk(level, position);
    }
}
