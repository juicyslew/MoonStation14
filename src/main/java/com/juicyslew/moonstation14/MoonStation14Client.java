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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MoonStation14.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public class MoonStation14Client {
    private static final PrototypeCatalogSyncAssembler CATALOG_ASSEMBLER =
            new PrototypeCatalogSyncAssembler(PrototypeRuntime.clientManager()::publishEncodedCatalogs);

    public MoonStation14Client(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        PrototypeCatalogNetworking.installClientHandler(MoonStation14Client::handleCatalogPayload);
        NeoForge.EVENT_BUS.register(MoonStation14ClientNetworkEvents.class);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ModSpecialProperties.addCustomItemProperties(event);
        // SET ALL BLOCK RENDERTYPES THAT NEED TO BE TRANSPARENT
    }

    @SubscribeEvent
    public static void renderAlerts(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        AlertAttachment alerts = minecraft.player.getExistingDataOrNull(ModDataAttachments.ALERT.get());
        if (alerts == null || alerts.isEmpty()) return;
        var catalog = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientAlerts();
        var ordered = alerts.snapshot().entrySet().stream().filter(entry -> catalog.get(entry.getKey().location()) != null)
                .sorted(java.util.Comparator.comparingInt(entry -> catalog.get(entry.getKey().location()).order()))
                .toList();
        int y = 12;
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
    }

    @SubscribeEvent
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

    @SubscribeEvent
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

    @SubscribeEvent
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
}


final class MoonStation14ClientNetworkEvents {
    private MoonStation14ClientNetworkEvents() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        MoonStation14Client.clearCatalogSync();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MoonStation14Client.clearCatalogSync();
    }
}
